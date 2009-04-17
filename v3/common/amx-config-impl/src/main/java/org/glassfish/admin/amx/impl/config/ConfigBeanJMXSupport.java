/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.admin.amx.impl.config;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;
import javax.management.modelmbean.DescriptorSupport;
import org.glassfish.admin.amx.core.AMXConstants;
import org.glassfish.admin.amx.config.AMXConfigProxy;
import org.glassfish.admin.amx.impl.util.ImplUtil;
import org.glassfish.admin.amx.util.MapUtil;
import org.glassfish.admin.amx.util.jmx.JMXUtil;
import org.glassfish.api.amx.AMXConfigInfo;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.Element;

/**
 * Helps generate required JMX artifacts (MBeanInfo, etc) from a ConfigBean interface, as well
 * storing author useful information about each @Configured interface.
 * @author llc
 */
public class ConfigBeanJMXSupport {

    /** bugs: these @Configured do not set @Attribute(key=true) */
    private static final Map<String,String>    CONFIGURED_BUGS = Collections.unmodifiableMap(MapUtil.newMap(
        "com.sun.grizzly.config.dom.Protocol", "Name",
        "com.sun.grizzly.config.dom.Transport", "Name",
        "com.sun.grizzly.config.dom.ThreadPool", "ThreadPoolId"
    ));
    private boolean hasConfiguredBug()  { return configuredBugKey() != null;}
    private String configuredBugKey()
    {
        final String key = CONFIGURED_BUGS.get( mIntf.getName() );
        return key;
    }
    
    
    private final Class<? extends ConfigBeanProxy> mIntf;

    private final List<AttributeMethodInfo>  mAttrInfos      = new ArrayList<AttributeMethodInfo>();
    private final List<ElementMethodInfo>    mElemenInfos   = new ArrayList<ElementMethodInfo>();
    private final List<Method>  mDuckTypedMethods = new ArrayList<Method>();
    
    private final NameHint mNameHint;
    
    private final MBeanInfo mMBeanInfo;
    
    public ConfigBeanJMXSupport(final Class<? extends ConfigBeanProxy> intf) {
        mIntf = intf;
        
        findStuff( intf, mAttrInfos, mElemenInfos, mDuckTypedMethods);
        
        mMBeanInfo = _getMBeanInfo();
        sanityCheckMBeanInfo();
        mNameHint = findNameHint();
        
        if ( hasConfiguredBug() )
        {
            ImplUtil.getLogger().warning( "ConfigBeanJMXSupport (AMX): working around @Configured bug for " + mIntf.getName() +
                ", using \"" + configuredBugKey() + "\" as the key attribute" );
        }
    }
    
    public String toString()
    {
        final StringBuilder buf = new StringBuilder();

        final String DELIM = ", ";
        
        buf.append( mIntf.getName() + " = " );
        buf.append("Attributes: {" );
        for (final AttributeMethodInfo info : mAttrInfos )
        {
            buf.append( info.attrName() + "/" + info.xmlName() + DELIM );
        }
        buf.append("}, Elements: {" );
        for (final ElementMethodInfo info : mElemenInfos )
        {
            buf.append( info.attrName() + "/" + info.xmlName() + DELIM );
        }

        final Set<String> childTypes = childTypes().keySet();
        buf.append("}, child types: {" );
        for (final String type : childTypes )
        {
            buf.append( type + DELIM);
        }

        buf.append("}, DuckTyped: " );
        for (final Method m : mDuckTypedMethods )
        {
            buf.append( m.getName() + DELIM);
        }
        buf.append("}");
        
        return buf.toString();
    }

        public static String
    typeFromName( final String s)
    {
        String simpleName = s;
        final int idx = s.lastIndexOf(".");
        if ( idx >= 0 )
        {
            simpleName = s.substring( idx + 1 );
        }
        return Dom.convertName(simpleName);
    }

        public String
    getTypeString()
    {
        return getTypeString(mIntf);
    }

        public String
    getTypeString( final Class<? extends ConfigBeanProxy> intf )
    {
        final Package pkg = intf.getPackage();
        String simple = intf.getName().substring( pkg.getName().length() + 1, intf.getName().length() );
        
        return typeFromName(simple);
    }
    
    public MBeanInfo getMBeanInfo() { return mMBeanInfo; }
    
    private MBeanInfo _getMBeanInfo() {
        final List<MBeanAttributeInfo>  attrsList = new ArrayList<MBeanAttributeInfo>();
        
        for( final AttributeMethodInfo info : mAttrInfos ) {
            attrsList.add( attributeToMBeanAttributeInfo( info ) );
        }
        for( final ElementMethodInfo e : mElemenInfos )
        {
            final MBeanAttributeInfo attrInfo  = elementToMBeanAttributeInfo( e.method() );
            if ( attrInfo != null )
            {
                attrsList.add( attrInfo );
            }
        }
        
        final MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[ attrsList.size() ];
        attrsList.toArray( attrs );

        final String classname = mIntf.getName();
        final String description = "ConfigBean " + mIntf.getName();
        final MBeanConstructorInfo[] constructors = null;
        final MBeanOperationInfo[]   operations = toMBeanOperationInfos();
        final MBeanNotificationInfo[] notifications = null;
        final Descriptor descriptor = descriptor();
        
        final MBeanInfo info = new MBeanInfo(
                classname,
                description,
                attrs,
                constructors,
                operations,
                notifications,
                descriptor);
        
        return info;
    }
    
    private boolean hasNameAttribute()
    {
        for( final MBeanAttributeInfo attrInfo : getMBeanInfo().getAttributes() )
        {
            if ( AMXConstants.ATTR_NAME.equals( attrInfo.getName() ) )
            {
                return true;
            }
        }
        return false;
    }
    
    private void sanityCheckMBeanInfo()
    {
        // verify that we don't have an item with getName() that's marked as a singleton
        // another ID could be used too (eg 'thread-pool-id'), no way to tell.
        if ( isSingleton() )
        {
            if ( hasNameAttribute() )
            {
                ImplUtil.getLogger().warning( "ConfigBeanJMXSupport (AMX): @Configured interface " + mIntf.getName() +
                    " has getName() which is not a key value.  Remove getName() or use @Attribute(key=true)" );
            }
        }
    }
    
    
    // if no key value can be found, consider it a singleton
    public boolean isSingleton()
    {
        if ( hasConfiguredBug() )
        {
            return false;
        }
        
        for( final AttributeMethodInfo info : mAttrInfos  )
        {
            if ( info.key() )
            {
                return false;
            }
        }
        
        for( final ElementMethodInfo info : mElemenInfos  )
        {
            if ( info.key() )
            {
                return false;
            }
        }
        
        return true;
    }

    // if no elements, then it's a leaf
    // Tricky case FIXME:  what if there are List<String> elements.
    boolean isLeaf()
    {
        return mElemenInfos.size() == 0;
    }
    

    private static void findStuff(
        final Class<? extends ConfigBeanProxy> intf,
        final List<AttributeMethodInfo> attrs,
        final List<ElementMethodInfo> elements,
        final List<Method> duckTyped )
    {
        for( final Method m : intf.getMethods() )
        {
            AttributeMethodInfo a;
            if ( (a = AttributeMethodInfo.get(m)) != null )
            {
                attrs.add( a );
                continue;
            }
            
            ElementMethodInfo e;
            if ( (e = ElementMethodInfo.get(m)) != null )
            {
                elements.add( e );
                continue;
            }
            
            final DuckTyped dt = m.getAnnotation(DuckTyped.class);
            if ( dt != null )
            {
                duckTyped.add(m);
            }
        }
    }
    
    public static final String P = "amx.configbean.";
    
    /** type of item: @Attribute, @Element, @DuckTyped */
    public static final String DESC_KIND = P + "kind";
    
    /** class of Collection element eg String of something else */
    public static final String DESC_ELEMENT_CLASS = P + "elementClass";
    
    /** class of Collection element eg String of something else */
    public static final String DESC_XML_NAME = P + "xmlName";
    
    /** classname of data type (@Attribute only) */
    public static final String DESC_DATA_TYPE = P + "dataType";
    
    /** Default value, omitted if none */
    public static final String DESC_DEFAULT_VALUE = P + "defaultValue";
    
    /** true|false: whether this is the primary key (name) */
    public static final String DESC_KEY = P + "key";
    
    /** true|false if this is the primary key (name) */
    public static final String DESC_REQUIRED = P + "required";
    
    /** true|false whether this is a reference to another element */
    public static final String DESC_REFERENCE = P + "reference";
    
    /** true|false whether variable expansion should be supplied */
    public static final String DESC_VARIABLE_EXPANSION = P + "variableExpansion";
    
    
    public static String xmlName(final MBeanAttributeInfo info, final String defaultValue )
    {
        final String value = (String)info.getDescriptor().getFieldValue(DESC_XML_NAME);
        return value == null ? defaultValue : value;
    }
    public static boolean isKey(final MBeanAttributeInfo info )
    {
        return (Boolean)info.getDescriptor().getFieldValue(DESC_KEY);
    }
    public static String defaultValue(final MBeanAttributeInfo info )
    {
        return (String)info.getDescriptor().getFieldValue(DESC_DEFAULT_VALUE);
    }
    
    /** Return a Map from the Attribute name to the xml name. */
    public Map<String,String>  getToXMLNameMapping()
    {
        final Map<String,String> m = new HashMap<String,String>();
        
        final MBeanInfo info = getMBeanInfo();
        for( final MBeanAttributeInfo attrInfo : info.getAttributes() )
        {
            m.put( attrInfo.getName(), xmlName(attrInfo, attrInfo.getName()) );
        }
        
        return m;
    }
    
    /** Return a Map from the xml  name to the Attribute name. */
     public Map<String,String>  getFromXMLNameMapping()
    {
        final Map<String,String> m = new HashMap<String,String>();
        
        final MBeanInfo info = getMBeanInfo();
        for( final MBeanAttributeInfo attrInfo : info.getAttributes() )
        {
            m.put( xmlName(attrInfo, attrInfo.getName()), attrInfo.getName() );
        }
        
        return m;
    }
    
    public static boolean isAttribute(final MBeanAttributeInfo info )
    {
        final String value = (String)info.getDescriptor().getFieldValue(DESC_KIND);
        
        return value == null || Attribute.class.getName().equals(value);
    }
    
    public static boolean isElement(final MBeanAttributeInfo info )
    {
        final String value = (String)info.getDescriptor().getFieldValue(DESC_KIND);
        
        return Element.class.getName().equals(value);
    }
    
    public static DescriptorSupport  descriptor( final Attribute a )
    {
        final DescriptorSupport d = new DescriptorSupport();

        d.setField( DESC_KIND, Attribute.class.getName() );
        
        if ( ! a.defaultValue().equals( "\u0000" ) )
        {
            d.setField( DESC_DEFAULT_VALUE, a.defaultValue() );
        }
        
        d.setField( DESC_KEY, a.key() );
        d.setField( DESC_REQUIRED, a.required() );
        d.setField( DESC_REFERENCE, a.reference() );
        d.setField( DESC_VARIABLE_EXPANSION, a.variableExpansion() );
        d.setField( DESC_DATA_TYPE, a.dataType().getName() );
        
        return d;
    }
    
    public static DescriptorSupport  descriptor( final Element e )
    {
        final DescriptorSupport d = new DescriptorSupport();

        d.setField( DESC_KIND, Element.class.getName() );
        
        d.setField( DESC_KEY, e.key() );
        d.setField( DESC_REQUIRED, e.required() );
        d.setField( DESC_REFERENCE, e.reference() );
        d.setField( DESC_VARIABLE_EXPANSION, e.variableExpansion() );
        
        return d;
    }
    
    public static DescriptorSupport  descriptor( final DuckTyped dt )
    {
        final DescriptorSupport d = new DescriptorSupport();

        d.setField( DESC_KIND, DuckTyped.class.getName() );
        
        return d;
    }

    private DescriptorSupport descriptor()
    {
        final DescriptorSupport d = new DescriptorSupport();

        String amxInterfaceName = AMXConfigProxy.class.getName();
        final AMXConfigInfo configInfo = mIntf.getAnnotation(AMXConfigInfo.class);
        if ( configInfo != null && configInfo.amxInterfaceName().length() > 0 )
        {
            String classname = configInfo.amxInterfaceName();
            final String PREFIX = "com.sun.appserv.management.config";
            if ( classname.startsWith(PREFIX) )
            {
                classname = "org.glassfish.admin.amx.intf.config" + classname.substring( PREFIX.length() );
            }
            amxInterfaceName = classname;
        }
        
        d.setField( AMXConstants.DESC_INTERFACE_NAME, amxInterfaceName );
        d.setField( AMXConstants.DESC_IMMUTABLE_INFO, true );
        d.setField( AMXConstants.DESC_PATH_PART, getTypeString() );
        d.setField( AMXConstants.DESC_GROUP, "config" );
        
        // Adoption is not supported, only other config elements
        d.setField( AMXConstants.DESC_SUPPORTS_ADOPTION, false);
        
        d.setField( AMXConstants.DESC_IS_SINGLETON, isSingleton() );

        return d;
    }
    
    
    public final Set<String> requiredAttributeNames()
    {
        final Set<String> s = new HashSet<String>();
        for( final AttributeMethodInfo info : mAttrInfos )
        {
            if ( info.required() )
            {
                s.add( info.attrName() );
            }
        }
        
        for( final ElementMethodInfo info : mElemenInfos )
        {
            if ( info.required() )
            {
                s.add( info.attrName() );
            }
        }
        return s;
    }
    
        public MBeanOperationInfo[]
    toMBeanOperationInfos()
    {
        return new MBeanOperationInfo[0];
        /*
        assess whether this is a good idea or not before implementing
        for( final Method m : mDuckTypedMethods)
        {
            final DuckTyped dt = m.getAnnotation(DuckTyped.class);
            if ( m.getReturnType.getClass().starts
        }
        */
    }

        public MBeanAttributeInfo
    attributeToMBeanAttributeInfo(final AttributeMethodInfo info)
    {
        final Descriptor descriptor = descriptor( info.attribute() );
        
        final String name = info.attrName();
        final String xmlName = info.xmlName();
        descriptor.setField( DESC_XML_NAME, xmlName );
        //debug( m.getName() + " => " + name + " => " + xmlName );

        Class type = info.returnType();
        final Attribute a = info.attribute();
        if ( a != null && a.dataType() != String.class  && type == String.class ) {
            // FIXME
        }

        String description = "@Attribute " + name + " of " + mIntf.getName();
        final boolean isReadable = true;
        // we assume that all getters are writeable for now
        final boolean isWriteable = true;
        final boolean isIs = false;
        final MBeanAttributeInfo attrInfo =
            new MBeanAttributeInfo( name, type.getName(), description, isReadable, isWriteable, isIs, descriptor);
        return attrInfo;
    }

    /** An  @Element("*") is anonymous, no specified type, could be anything */
    public static final String ANONYMOUS_SUB_ELEMENT = "*";

    private static abstract class MethodInfo {
        protected final Method   mMethod;
        protected final String   mAttrName;
        protected final String   mXMLName;
        
        MethodInfo( final Method m, final String xmlName )
        {
            mMethod   = m;
            mAttrName = JMXUtil.getAttributeName(m);
            mXMLName  = xmlName;
        }
        
        public Method  method() { return mMethod; }
        public String  attrName() { return mAttrName; }
        public String  xmlName() { return mXMLName; }
        public Class<?> returnType() { return mMethod.getReturnType(); }
        public abstract boolean required();
        public abstract boolean key();

        /** return ConfigBeanProxy interface, or null if not a ConfigBeanProxy */
        public Class<? extends ConfigBeanProxy> intf() {
            final Class returnType = returnType();
            if ( ConfigBeanProxy.class.isAssignableFrom(returnType) )
            {
                return returnType.asSubclass(ConfigBeanProxy.class);
            }
            return null;
        }
    }
    
    private static final class ElementMethodInfo extends MethodInfo {
        private final Element  mElement;

        private ElementMethodInfo( final Method m, final Element e )
        {
            super( m, e.value().length() == 0 ? typeFromName(JMXUtil.getAttributeName(m)) : e.value() );
            mElement  = e;
        }

        public static ElementMethodInfo get( final Method m ) {
            
            final Element e = m.getAnnotation(Element.class);
            return e == null ? null : new ElementMethodInfo( m, e);
        }

        public Element element() { return mElement; }
        public boolean anonymous() { return ANONYMOUS_SUB_ELEMENT.equals(xmlName()); }
        
        public boolean required() { return mElement.required(); }
        public boolean key() { return mElement.key(); }
    }
    
     private static final class AttributeMethodInfo extends MethodInfo  {
        private final Attribute mAttribute;

        private AttributeMethodInfo( final Method m, final Attribute a )
        {
            super( m, a.value().length() == 0 ? typeFromName(JMXUtil.getAttributeName(m)) : a.value() );
            mAttribute  = a;
        }

        public static AttributeMethodInfo get( final Method m ) {
            final Attribute a = m.getAnnotation(Attribute.class);
            return a == null ? null : new AttributeMethodInfo( m, a);
        }

        public Attribute attribute() { return mAttribute; }
        public boolean required() { return mAttribute.required(); }
        public boolean key() { return mAttribute.key(); }
    }


    /**
        Get the child types, excluding String[] and anonymous.
     */
        public Set<Class<? extends ConfigBeanProxy>>
    childInterfaces()
    {
        return childInterfaces( mElemenInfos );
    }

        public Map<String, Class<? extends ConfigBeanProxy>>
    childTypes()
    {
        final Map<String, Class<? extends ConfigBeanProxy>> types = new HashMap<String, Class<? extends ConfigBeanProxy>>();
        for( final Class<? extends ConfigBeanProxy> intf : childInterfaces() )
        {
            types.put( getTypeString( intf ), intf);
        }
        return types;
    }
    
        public Class<? extends ConfigBeanProxy>
    getConfigBeanProxyClassFor( final String type )
    {
        return childTypes().get( type );
    }
    
         public Set<Class<? extends ConfigBeanProxy>>
    childInterfaces(final List<ElementMethodInfo> infos)
    {
        final Set<Class<? extends ConfigBeanProxy>> classes = new HashSet<Class<? extends ConfigBeanProxy>>();
        
        for( final ElementMethodInfo info : infos ) {
            if ( info.anonymous() ) continue;

            final Class methodReturnType = info.returnType();
            
            Class<? extends ConfigBeanProxy> intf = null;
            if ( info.intf() != null )
            {
                intf = info.intf();
            }
            else if ( Collection.class.isAssignableFrom(methodReturnType) )
            {
                final Type genericReturnType = info.method().getGenericReturnType();
                if ( genericReturnType instanceof ParameterizedType )
                {
                    final ParameterizedType  pt = (ParameterizedType)genericReturnType;
                    final Type[] argTypes = pt.getActualTypeArguments();
                    if ( argTypes.length == 1 )
                    {
                        final Type argType = argTypes[0];
                        if ( (argType instanceof Class) && (Class)argType == String.class)
                        {
                            // ignore for our purposes here
                        }
                        else
                        {
                            intf = ((Class)argType).asSubclass(ConfigBeanProxy.class);
                        }
                    }
                }
            }
            if ( intf != null )
            {
                classes.add(intf);
            }
        }
        
        return classes;
    }

    
    /**
        @Elements are represented as Attributes:  getters for sub-elements are presented
        as ObjectName, Collection<String> presented as String[], Collection<? extends ConfigBeanProxy>
        represented as ObjectName[].
     */
         public MBeanAttributeInfo
    elementToMBeanAttributeInfo(final Method m)
    {
        final ElementMethodInfo info = ElementMethodInfo.get(m);
        if ( info == null || info.anonymous() ) return null;
        
        final String name    = info.attrName();    // eg strip the "get"
        final String xmlName = info.xmlName();
        //debug( m.getName() + " => " + name + " => " + xmlName );

        final Class methodReturnType = info.returnType();
        Class<?> returnType = null;
        
        if ( info.intf() != null )
        {
            // some sub-type, which we must represent as an ObjectName
            returnType = ObjectName.class;
        }
        else if ( Collection.class.isAssignableFrom(methodReturnType) )
        {
            final Type genericReturnType = m.getGenericReturnType();
            if ( genericReturnType instanceof ParameterizedType )
            {
                final ParameterizedType  pt = (ParameterizedType)genericReturnType;
                final Type[] argTypes = pt.getActualTypeArguments();
                if ( argTypes.length == 1 )
                {
                    final Type argType = argTypes[0];
                    if ( (argType instanceof Class) && (Class)argType == String.class)
                    {
                        returnType = String[].class;
                    }
                    else
                    {
                        returnType = ObjectName[].class;
                    }
                }
            }
        }
        else
        {
            // some unknown type we cannot handle
        }
        
        MBeanAttributeInfo  attrInfo = null;
        if ( returnType != null )
        {
            final DescriptorSupport descriptor = descriptor(info.element());
        
            descriptor.setField( DESC_ELEMENT_CLASS, returnType.getName() );
            descriptor.setField( DESC_XML_NAME, xmlName );
            
            String description = "@Element " + name + " of interface " + mIntf.getName();
            final boolean isReadable = true;
            // we assume that all getters are writeable for now
            final boolean isWriteable = true;
            final boolean isIs = false;
            attrInfo = new MBeanAttributeInfo( name, returnType.getName(), description, isReadable, isWriteable, isIs, descriptor);
        }
        
        return attrInfo;
    }
    
    public String getNameHint() {
        return mNameHint.mHint;
    }

    public boolean nameHintIsElement() {
        return mNameHint.mIsElement;
    }

    private static String toXMLName(final String name) {
        return name == null ? name : Dom.convertName(name);
    }
    private final static String DEFAULT_NAME_HINT = "name";

    private static final class NameHint {
        public static final NameHint NAME = new NameHint(DEFAULT_NAME_HINT);
        public static final NameHint NONE = new NameHint(null);
        private final String mHint;
        private final boolean mIsElement;

        public NameHint(final String hint, final boolean isElement) {
            mHint = toXMLName(hint);
            mIsElement = isElement;
        }

        public NameHint(final String hint) {
            this(hint, false);
        }
    }

    /**
    Return the name of the XML attribute which contains the value to be used as its name.
    First element is the name hint, 2nd indicates its type
     */
    private NameHint findNameHint() {
        String[] result = new String[2];
        result[0] = null;
        result[1] = null;

        if ( isSingleton() ) {
            return NameHint.NONE;
        }
        
        final String configuredBugKey = configuredBugKey();

        for (final AttributeMethodInfo info : mAttrInfos )
        {
            if ( info.key() )
            {
                //final MBeanAttributeInfo attrInfo = attributeToMBeanAttributeInfo( info );
                //debug( "KEY VALUE in " + mIntf.getName() + " = " + info.getName() + " ==> " + hint );
                //return new NameHint( xmlName( attrInfo, null) );
                return new NameHint( info.xmlName() );
            }
            else if (  configuredBugKey != null && info.attrName().equals(configuredBugKey) )
            {
                //debug( "findNameHint:  " + configuredBugKey + " for " + mIntf.getName() );
                return new NameHint( configuredBugKey );
            }
        }
    
        /**
        Is this possible?
        for (final ElementMethodInfo info : mElements.values()) {
            if (info.getElement().key()) {
                return new NameHint(info.getName(), true);
            }
            
        }
        */

        return NameHint.NAME;
    }
    
    public Map<String,String> getDefaultValues( final boolean useAttributeNames )
    {
        final Map<String,String> m = new HashMap<String,String>();
        
        final MBeanInfo info = getMBeanInfo();
        for( final MBeanAttributeInfo attrInfo : info.getAttributes() )
        {
            final String defaultValue = defaultValue(attrInfo);
            final String attrName = attrInfo.getName();
            final String name = useAttributeNames ? attrName : xmlName(attrInfo, attrName);
            
            m.put( name, defaultValue );
        }
        return m;
    }
    
    private static void debug( final String s ) { System.out.println( "### " + s); }
}

























