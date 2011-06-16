/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package com.sun.enterprise.web;

import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.config.serverbeans.WebContainer;
import com.sun.enterprise.web.session.PersistenceType;
import com.sun.logging.LogDomains;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.deployment.DeployCommandParameters;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.internal.api.ClassLoaderHierarchy;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.config.types.Property;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;

@Service
@Scoped(PerLookup.class)
public class ServerConfigLookup {

    protected static final Logger _logger = LogDomains.getLogger(
            ServerConfigLookup.class, LogDomains.WEB_LOGGER);


    @Inject(name= ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Config configBean;

    @Inject(optional = true)
    private AvailabilityService availabilityService;

    @Inject(optional = true)
    private WebContainerAvailability webContainerAvailability;

    @Inject
    private ClassLoaderHierarchy clh;


    /**
     * Get the session manager bean from domain.xml
     * return null if not defined or other problem
     */  
    public SessionManager getInstanceSessionManager() { 
        if (configBean == null) {
            return null;
        }
        
        WebContainer webContainerBean
            = configBean.getWebContainer();
        if (webContainerBean == null) {
            return null;
        }
        
        SessionConfig sessionConfigBean = webContainerBean.getSessionConfig();
        if (sessionConfigBean == null) {
            return null;
        }
        
        return sessionConfigBean.getSessionManager();
    }    
    
    /**
     * Get the manager properties bean from domain.xml
     * return null if not defined or other problem
     */  
    public ManagerProperties getInstanceSessionManagerManagerProperties() {
        
        SessionManager smBean = getInstanceSessionManager();
        if (smBean == null) {
            return null;
        }

        return smBean.getManagerProperties();
    } 
    
    /**
     * Get the store properties bean from domain.xml
     * return null if not defined or other problem
     */  
    public StoreProperties getInstanceSessionManagerStoreProperties() {
        
        SessionManager smBean = getInstanceSessionManager();
        if (smBean == null) {
            return null;
        }

        return smBean.getStoreProperties();
    } 

    /**
     * Get the session properties bean from server.xml
     * return null if not defined or other problem
     */      
    public SessionProperties getInstanceSessionProperties() { 
        if (configBean == null) {
            return null;
        }
        
        WebContainer webContainerBean
            = configBean.getWebContainer();
        if (webContainerBean == null) {
            return null;
        }
        
        SessionConfig sessionConfigBean = webContainerBean.getSessionConfig();
        if (sessionConfigBean == null) {
            return null;
        }
        
        return sessionConfigBean.getSessionProperties();
    }


    /**
     * Get the availability-service element from domain.xml.
     * return null if not found
     */
    protected AvailabilityService getAvailabilityService() {
        return this.availabilityService;
    }

    /**
     * Get the availability-enabled from domain.xml.
     * return false if not found
     */   
    public boolean getAvailabilityEnabledFromConfig() {
        AvailabilityService as = this.getAvailabilityService();
        if (as == null) {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("AvailabilityService was not defined - check domain.xml");
            }
            return false;
        }        

        if (as.getAvailabilityEnabled() == null) {
            return false;
        } else {
            return toBoolean(as.getAvailabilityEnabled());
        }
    }

    /**
     * Geo the web-container-availability element from domain.xml.
     * return null if not found
     */     
    private WebContainerAvailability  getWebContainerAvailability() {
        return this.webContainerAvailability;
    }
    
    /**
     * Get the String value of the property under web-container-availability 
     * element from domain.xml whose name matches propName
     * return null if not found
     * @param propName
     */
    protected String getWebContainerAvailabilityPropertyString(
                String propName) {
        return getWebContainerAvailabilityPropertyString(propName, null);
    }

    /**
     * Get the String value of the property under web-container-availability 
     * element from domain.xml whose name matches propName
     * return defaultValue if not found
     * @param propName
     */    
    protected String getWebContainerAvailabilityPropertyString(
                String propName,
                String defaultValue) {
        WebContainerAvailability wcAvailabilityBean = getWebContainerAvailability();
        if (wcAvailabilityBean == null) {
            return defaultValue;
        }

        List<Property> props = wcAvailabilityBean.getProperty();
        if (props == null) {
            return defaultValue;
        }

        for (Property prop : props) {
            String name = prop.getName();
            String value = prop.getValue();
            if (name.equalsIgnoreCase(propName)) {
                return value;
            }
        }

        return defaultValue;
    } 


    /**
     * Get the availability-enabled for the web container from domain.xml.
     * return inherited global availability-enabled if not found
     */
    public boolean getWebContainerAvailabilityEnabledFromConfig() {
        boolean globalAvailabilityEnabled = getAvailabilityEnabledFromConfig();
        WebContainerAvailability was = getWebContainerAvailability();
        if (was == null) {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("WebContainerAvailability not defined - check domain.xml");
            }
            return globalAvailabilityEnabled;
        }

        if (was.getAvailabilityEnabled() == null) {
            return globalAvailabilityEnabled;
        } else {
            return toBoolean(was.getAvailabilityEnabled());
        }
    } 

    /**
     * Get the availability-enabled for the web container from domain.xml.
     * return inherited global availability-enabled if not found
     */   
    public boolean getWebContainerAvailabilityEnabledFromConfig(boolean inheritedValue) {
        WebContainerAvailability was = getWebContainerAvailability();
        if (was == null) {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("WebContainerAvailability not defined - check domain.xml");
            }
            return inheritedValue;
        }

        if (was.getAvailabilityEnabled() == null) {
            return inheritedValue;
        } else {
            return toBoolean(was.getAvailabilityEnabled());
        }
    }    

    /**
     * Get the sso-failover-enabled boolean from domain.xml.
     */
    public boolean isSsoFailoverEnabledFromConfig() {
        WebContainerAvailability webContainerAvailabilityBean = getWebContainerAvailability();
        if (webContainerAvailabilityBean == null) {
            return false;
        }
        if (webContainerAvailabilityBean.getSsoFailoverEnabled() == null) {
            return false;
        } else {
            return toBoolean(webContainerAvailabilityBean.getSsoFailoverEnabled());
        }
    }

    /**
     * Get the availability-enabled from domain.xml.
     * This takes into account:
     * global
     * web-container-availability
     * web-module (if stand-alone)
     * return false if not found
     */   
    public boolean calculateWebAvailabilityEnabledFromConfig(WebModule ctx) {
        // global availability from <availability-service> element
        boolean globalAvailability = getAvailabilityEnabledFromConfig();
        if (_logger.isLoggable(Level.FINEST)) {
            _logger.finest("globalAvailability = " + globalAvailability);
        }

        // web container availability from <web-container-availability>
        // sub-element

        boolean webContainerAvailability =
            getWebContainerAvailabilityEnabledFromConfig(globalAvailability);
        if (_logger.isLoggable(Level.FINEST)) {
            _logger.finest("webContainerAvailability = " + webContainerAvailability);
        }

        boolean webModuleAvailability = false;
        DeploymentContext dc = ctx.getWebModuleConfig().getDeploymentContext();
        if (dc != null) {
            DeployCommandParameters params = dc.getCommandParameters(DeployCommandParameters.class);
            if (params != null) {
                webModuleAvailability = params.availabilityenabled;
            }
        }


        if (_logger.isLoggable(Level.FINEST)) {
            _logger.finest("webModuleAvailability = " + webModuleAvailability);
        }
        return globalAvailability && webContainerAvailability && webModuleAvailability;
    }

    /**
     * Get the persistenceType from domain.xml.
     * return null if not found
     */

    public boolean getAsyncReplicationFromConfig(WebModule ctx) {
        boolean asyncReplication = true;
        DeploymentContext dc = ctx.getWebModuleConfig().getDeploymentContext();
        if (dc != null) {
            DeployCommandParameters params = dc.getCommandParameters(DeployCommandParameters.class);
            if (params != null) {
                asyncReplication = params.asyncreplication;
            }

        }
        return asyncReplication;
    }

    public PersistenceType getPersistenceTypeFromConfig() {
        String persistenceTypeString = null;      
        PersistenceType persistenceType = null;

        WebContainerAvailability webContainerAvailabilityBean =
            getWebContainerAvailability();
        if (webContainerAvailabilityBean == null) {
            return null;
        }
        persistenceTypeString = webContainerAvailabilityBean.getPersistenceType();

        if (persistenceTypeString != null) {
            persistenceType = PersistenceType.parseType(persistenceTypeString);
        }
        if (persistenceType != null) {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("SERVER.XML persistenceType= " + persistenceType.getType());
            }
        } else {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("SERVER.XML persistenceType missing");
            }
        }

        return persistenceType;
    }     
    
    /**
     * Get the persistenceFrequency from domain.xml.
     * return null if not found
     */
    public String getPersistenceFrequencyFromConfig() { 
        WebContainerAvailability webContainerAvailabilityBean =
            getWebContainerAvailability();
        if (webContainerAvailabilityBean == null) {
            return null;
        }
        return webContainerAvailabilityBean.getPersistenceFrequency();      
    }
    
    /**
     * Get the persistenceScope from domain.xml.
     * return null if not found
     */
    public String getPersistenceScopeFromConfig() {
        WebContainerAvailability webContainerAvailabilityBean =
            getWebContainerAvailability();
        if (webContainerAvailabilityBean == null) {
            return null;
        }
        return webContainerAvailabilityBean.getPersistenceScope(); 
    }     

    /**
     * convert the input value to the appropriate Boolean value
     * if input value is null, return null
     */     
    protected Boolean toBoolean(String value) {
        if (value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("on")
                || value.equalsIgnoreCase("1")) {
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }


    /**
     * Loads the requested class using the Common Classloader
     *
     * @param className the name of the class to load
     *
     * @return the loaded class
     */
    Class loadClass(String className) throws Exception {
        return clh.getCommonClassLoader().loadClass(className);
    }
}
