/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twcable.gradle.sling

import com.twcable.gradle.sling.osgi.BundleState
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import com.twcable.gradle.sling.osgi.SlingOsgiBundle
import groovy.json.JsonBuilder
import groovy.transform.TypeChecked
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static java.net.HttpURLConnection.HTTP_OK

@TypeChecked
@SuppressWarnings("GroovyResultOfAssignmentUsed")
class SlingBundleFixture extends FixtureBase {
    private String _bundleName
    private String _version
    private String _symbolicName
    private String _packageName
    private String _description
    private BundleState _bundleState
    private SlingBundleConfiguration _osgiBundleConfiguration
    private SlingOsgiBundle _slingOsgiBundle
    private SlingServerConfiguration _slingServerConfiguration
    private String _installPath
    private File _sourceFile
    private Project _project


    private SlingBundleFixture() {}


    static SlingBundleFixture make(Closure closure) {
        make(SlingBundleFixture, closure)
    }


    void osgiBundleConfiguration(SlingBundleConfiguration osgiBundleConfiguration) {
        _osgiBundleConfiguration = osgiBundleConfiguration

        _symbolicName = osgiBundleConfiguration.symbolicName
        if (osgiBundleConfiguration.project) {
            _version = osgiBundleConfiguration.project.version as String
            _description = osgiBundleConfiguration.project.description
        }
        _bundleName = osgiBundleConfiguration.name
    }


    String getBundleName() {
        _bundleName = _bundleName ?: "Unit Test bundle name"
    }


    String getVersion() {
        _version = _version ?: "1.9.3"
    }


    Project getProject() {
        if (_project == null) {
            _project = ProjectBuilder.builder().build()

            def slingServersConfiguration = new SlingServersConfiguration()
            slingServersConfiguration[slingServerConfiguration.name] = slingServerConfiguration
            slingServersConfiguration.maxWaitValidateBundlesMs = 100
            slingServersConfiguration.retryWaitMs = 10
            project.extensions.add('slingServers', slingServersConfiguration)
        }
        return _project
    }


    String getSymbolicName() {
        _symbolicName = _symbolicName ?: "unit.test.symbolic.name"
    }


    String getPackageName() {
        _packageName = _packageName ?: _symbolicName.split('.').reverse().join('.')
    }


    String getDescription() {
        _description = _description ?: "Unit Test _description"
    }


    BundleState getBundleState() {
        _bundleState = _bundleState ?: com.twcable.gradle.sling.osgi.BundleState.ACTIVE
    }


    File getSourceFile() {
        if (_sourceFile == null) {
            _sourceFile = new File("build/libs/${bundleName}-${version}.jar")
        }
        _sourceFile
    }


    String getInstallPath() {
        if (_installPath == null) {
            _installPath = "/apps/install"
        }
        _installPath
    }


    SlingServerConfiguration getSlingServerConfiguration() {
        if (_slingServerConfiguration == null) {
            _slingServerConfiguration = new SlingServerConfiguration(name: 'test', protocol: 'test', machineName: 'unittest', port: 9797)
        }
        _slingServerConfiguration
    }


    SlingServersConfiguration getSlingServersConfiguration() {
        return project.extensions.getByType(SlingServersConfiguration)
    }


    SlingOsgiBundle getSlingOsgiBundle() {
        if (_slingOsgiBundle == null) {
            _slingOsgiBundle = new SlingOsgiBundle(osgiBundleConfiguration)
        }
        _slingOsgiBundle
    }


    SlingBundleConfiguration getOsgiBundleConfiguration() {
        if (_osgiBundleConfiguration == null) {
            def conf = new SlingBundleConfiguration(project)
            conf.name = bundleName
            conf.installPath = installPath
            conf.symbolicName = symbolicName
            conf.sourceFile = sourceFile
//            conf.slingServers = slingServersConfiguration
//            _osgiBundleConfiguration = new SlingBundleConfiguration(name: bundleName,
//                installPath: installPath,
//                symbolicName: symbolicName,
//                sourceFile: sourceFile,
//                slingServers: slingServersConfiguration)
            _osgiBundleConfiguration = conf
        }
        _osgiBundleConfiguration
    }


    String bundleInformationJson() {
        new JsonBuilder(bundleInformation()).toPrettyString()
    }


    Map bundleInformation() {
        def props =
            [
                [key: "Symbolic Name", value: symbolicName],
                [key: "Version", value: version],
                [key: "Bundle Location", value: "jcrinstall:${osgiBundleConfiguration.getBundlePath(slingServerConfiguration.installPath)}"],
                [key: "Last Modification", value: "Wed Jan 09 10:59:24 MST 2013"],
                [key: "Bundle Documentation", value: "http://mystropedia.corp.mystrotv.com/display/WEBCMS/Home"],
                [key: "Vendor", value: "Time Warner Cable - Converged Technology Group - CMS Team"],
                [key: "Description", value: description],
                [key: "Start Level", value: 20],
                [key: "Exported Packages", value: ["${packageName},version=${version}"]],
                [key  : "Imported Packages",
                 value: ["javax.jcr,version=2.0.0 from <a href='/system/console/bundles/55'>javax.jcr (55)</a>",
                         "org.slf4j,version=1.6.4 from <a href='/system/console/bundles/11'>slf4j.api (11)</a>"]],
                [key  : "Importing Bundles",
                 value: ["<a href='/system/console/bundles/239'>com.test.servlets (239)</a>",
                         "<a href='/system/console/bundles/277'>com.test.servlets (277)</a>"]],
                [key  : "Manifest Headers",
                 value: ["Bnd-LastModified: 1357594240000",
                         "Bundle-Description: ${description}",
                         "Bundle-DocURL: http://mystropedia.corp.mystrotv.com/display/WEBCMS/Home",
                         "Bundle-ManifestVersion: 2",
                         "Bundle-Name: ${bundleName}",
                         "Bundle-SymbolicName: ${symbolicName}",
                         "Bundle-Vendor: Time Warner Cable - Converged Technology Group - CMS Team",
                         "Bundle-Version: 1.0.1",
                         "Created-By: 1.6.0_37 (Apple Inc.)",
                         "Export-Package: ${packageName}; uses:=\\\"javax.jcr, org.slf4j\\\"; version=\\\"${version}\\\"",
                         "Implementation-Title: Unit Testing Bundle",
                         "Implementation-Vendor: Time Warner Cable - Converged Technology Group - CMS Team",
                         "Implementation-Version: ${version}",
                         "Import-Package: javax.jcr; version=\\\"[2.0, 3)\\\", org.slf4j; version=\\\"[1.6, 2)\\\"",
                         "Manifest-Version: 1.0",
                         "Tool: Bnd-1.50.0"]]
            ]
        List data = [[props       : props, id: 284,
                      name        : bundleName,
                      fragment    : false,
                      stateRaw    : bundleState.stateRaw,
                      state       : bundleState.stateString,
                      version     : version,
                      symbolicName: symbolicName,
                      category    : ""
                     ]]

        [data  : data,
         s     : [
             279,
             272,
             7,
             0,
             0
         ],
         status: "Bundle information: 279 bundles in total - all 279 bundles active."
        ]
    }


    String uploadFileResponse(String installPath) {
        final bundlePath = osgiBundleConfiguration.getBundlePath(installPath)
        final installLocation = osgiBundleConfiguration.getInstallPathForServer(installPath)
        def changesArray = [
            [type: 'created', argument: bundlePath],
            [type: 'created', argument: "${bundlePath}/jcr:content"],
            [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:lastModified"],
            [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:mimeType"],
            [type: 'modified', argument: "${bundlePath}/jcr:content/jcr:data"],
            [type: 'modified', argument: "${installLocation}/_noredir_"],
        ]
        def data = [
            changes         : changesArray,
            path            : installLocation,
            location        : installLocation,
            parentLocation  : new File(installLocation).parent,
            'status.code'   : HTTP_OK,
            'status.message': 'OK',
            title           : "Content modified ${installLocation}",
            referer         : ''
        ]
        JsonBuilder jsonBuilder = new JsonBuilder(data)
        jsonBuilder.toString()
    }

}
