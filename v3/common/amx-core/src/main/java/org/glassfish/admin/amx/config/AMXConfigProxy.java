/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admin.amx.config;

import org.glassfish.admin.amx.config.AttributeResolver;
import org.glassfish.admin.amx.config.DefaultValues;
import org.glassfish.admin.amx.core.AMXProxy;

import java.util.Map;
import javax.management.MBeanOperationInfo;
import org.glassfish.admin.amx.annotation.ManagedOperation;


/**
	Extending this interface implies that the class is part of the  API
	for configuration.
	<p>
	All AMXConfigProxy s are required to implement NotificationEmitter.
	A Config  must issue {@link javax.management.AttributeChangeNotification} when
	changes are made to the configuration.
    <p>
    Note that most sub-interfaces generally provide an AMX_TYPE field because there
    are enough exceptions that explicit mapping to the actual type cannot be purely
    algorithmic.
 */
public interface AMXConfigProxy extends AMXProxy, AttributeResolver, DefaultValues
{
	/**
		The type of the Notification emitted when a config element
		is created.
	 */
	public final String	CONFIG_CREATED_NOTIFICATION_TYPE	=
		"org.glassfish.admin.amx.intf.ConfigCreated";
    
	/**
		The type of the Notification emitted when a config element
		is removed.
	 */
	public final String	CONFIG_REMOVED_NOTIFICATION_TYPE	=
		"org.glassfish.admin.amx.config.ConfigRemoved";
		
	/**
		The key within the Notification's Map of type
		CONFIG_REMOVED_NOTIFICATION_TYPE which yields the ObjectName
		of the  created or removed config.
	 */
	public final String	CONFIG_OBJECT_NAME_KEY	= "ConfigObjectName";
    
	/**
        Return a Map of default values for the specified child type.
        The resulting Map is keyed by the  attribute name, either the AMX attribute name or the xml attribute name.
        @since Glassfish V3.
        @param type the J2EEType of the child
        @param useAMXAttributeName whether to key the values by the the AMX Attribute name or XML attribute name
	 */
    @ManagedOperation(impact=MBeanOperationInfo.INFO)
	public Map<String,String> getDefaultValues( final String type, final boolean useAMXAttributeName);
    
	/**
        Return a Map of default values for this MBean.
        @param useAMXAttributeName whether to key the values by the XML attribute name vs the AMX Attribute name
        @see DefaultValues
	 */
    @ManagedOperation(impact=MBeanOperationInfo.INFO)
    public Map<String,String> getDefaultValues(final boolean useAMXAttributeName);
    
    
    /**
        Generic creation of an {@link AMXConfigProxy} based on the desired XML element type, which must
        be legitimate for the containing element.
        <p>
        Required attributes must be specified, and should all be 'String' (The Map value is declared
        with a type of of 'Object' anticipating future extensions).
        Use the {@link AMXAttributes#ATTR_NAME} key for the name.
        <p>
        Properties can be included in the 'params' Map using the {@link PropertiesAccess#PROPERTY_PREFIX}
        prefix on the property name.  
        System properties can be included in the 'params' Map using the
        {@link SystemPropertiesAccess#SYSTEM_PROPERTY_PREFIX} prefix on the property name.
        
        @param elementType the XML element type
        @param params Map containing  attributes which are required by the @Configured and any
        optional attributes (as desired).
        @return proxy interface to the newly-created AMXConfigProxy
        
        @see org.glassfish.admin.amx.base.XTypes
     */
    @ManagedOperation
    public AMXConfigProxy createChild( String elementType, Map<String,Object> params );
    
    /**
        Generically remove a config by type and name.
        @param childType the AMX j2eeType as defined
        @param name the name of the child
     */
    @ManagedOperation
    public void  removeChild( String childType, String name );

    /**
        Generically remove a config by type (child must be a singleton)
        @param childType the AMX j2eeType as defined
     */
    @ManagedOperation
    public void  removeChild( String childType );
}
