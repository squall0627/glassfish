/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.enterprise.admin.servermgmt.services;

import com.sun.enterprise.universal.io.SmartFile;
import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.util.io.ServerDirs;
import java.io.File;
import java.util.Date;
import static com.sun.enterprise.admin.servermgmt.services.Constants.*;

/**
 * A place to keep platform services info...
 * @author Byron Nevins
 */
public class PlatformServicesInfo {
    public PlatformServicesInfo(ServerDirs sDirs, AppserverServiceType theType) {
        serverDirs = sDirs;

        if (serverDirs == null || serverDirs.getServerDir() == null)
            throw new RuntimeException(Strings.get("bad.server.dirs"));

        type = theType;
    }

    public void validate() {
        if (!StringUtils.ok(serviceName))
            serviceName = serverDirs.getServerName();

        date = new Date();
        setInstallRootDir();
        setLibDir();
        setAsadmin();
        osUser = System.getProperty("user.name");
        // used by SMF only
        fqsn = serverDirs.getServerName() + serverDirs.getServerParentDir().getPath().replace('/', '_');
        smfFullServiceName = SERVICE_NAME_PREFIX + serviceName;
        valid = true;
    }

    /**
     * @param serviceName the serviceName to set
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * @param dryRun the dryRun to set
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * @param passwordFile the passwordFile to set
     */
    public void setPasswordFile(File passwordFile) {
        this.passwordFile = passwordFile;
    }

    /**
     * @param force the force to set
     */
    public void setForce(boolean force) {
        this.force = force;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    //////////////////////////////////////////////////////////////////////
    //////////////          private         //////////////////////////////
    //////////////////////////////////////////////////////////////////////

    private void setLibDir() {
        libDir = SmartFile.sanitize(new File(installRootDir, "lib"));

        if (!libDir.isDirectory())
            throw new RuntimeException(Strings.get("internal.error",
                    "Not a directory: " + libDir));
    }

    private void setInstallRootDir() {
        String ir = System.getProperty(SystemPropertyConstants.INSTALL_ROOT_PROPERTY);

        if (!StringUtils.ok(ir))
            throw new RuntimeException(Strings.get("internal.error", "System Property not set: "
                    + SystemPropertyConstants.INSTALL_ROOT_PROPERTY));

        installRootDir = SmartFile.sanitize(new File(ir));

        if (!installRootDir.isDirectory())
            throw new RuntimeException(Strings.get("internal.error",
                    "Not a directory: " + installRootDir));
    }

    private void setAsadmin() {
        String s = SystemPropertyConstants.getAsAdminScriptLocation();

        if (!StringUtils.ok(s))
            throw new RuntimeException(
                    Strings.get("internal.error",
                    "Can't get Asadmin script location"));

        asadminScript = SmartFile.sanitize(new File(s));

        if (!asadminScript.isFile()) {
            throw new RuntimeException(
                    Strings.get("noAsadminScript", asadminScript));
        }
    }
    
    // set at construction-time
    final ServerDirs serverDirs;
    final AppserverServiceType type;

    // accessed by classes in this package
    String fqsn;
    String serviceName;
    boolean dryRun;
    String osUser;
    boolean trace;
    File libDir;
    String smfFullServiceName;

    // private to this implementation
    private File asdminFile;
    private File passwordFile;
    private boolean force;
    private Date date;
    private boolean valid;
    private File installRootDir;
    private File asadminScript;
}
