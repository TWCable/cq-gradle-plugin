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

package com.twcable.gradle.sling.osgi

import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.internal.plugins.osgi.OsgiHelper
import org.gradle.api.tasks.bundling.AbstractArchiveTask

import javax.annotation.Nonnull

/**
 * Convention settings to describe an OSGi bundle.
 *
 * Follows to patterns used for a Gradle "named domain object".
 */
@Slf4j
@TypeChecked
class SlingBundleConfiguration {
    /**
     * The name to register the configuration under in the project's extensions
     */
    public static final String NAME = 'bundle'

    String name
    private String _symbolicName
    private String _installPath
    private File _sourceFile
    private Number _felixId

    final Project project


    SlingBundleConfiguration(Project project) {
        this.project = project
    }


    SlingServersConfiguration getSlingServers() {
        SlingServersConfiguration slingServers = project.extensions.findByType(SlingServersConfiguration)
        if (slingServers == null) {
            slingServers = (SlingServersConfiguration)project.extensions.create(SlingServersConfiguration.NAME, SlingServersConfiguration)
        }
        return slingServers
    }


    void setInstallPath(String path) {
        _installPath = path
    }


    @Nonnull
    String getInstallPath() {
        def defaultAuthorInstallPath = slingServers['author']?.installPath
        return getInstallPathForServerName(defaultAuthorInstallPath)
    }


    @Nonnull
    String getInstallPathForServer(String serverConfigInstallPath) {
        if (_installPath == null) {
            if (serverConfigInstallPath == null) {
                return '/apps/install'
            }
            else {
                return serverConfigInstallPath
            }
        }
        else {
            return _installPath
        }
    }


    @Nonnull
    String getInstallPathForServerName(String serverConfigInstallPath) {
        return getInstallPathForServer(serverConfigInstallPath)
    }


    void setSourceFile(File file) {
        _sourceFile = file
    }


    @Nonnull
    File getSourceFile() {
        if (_sourceFile == null) {
            if (project.tasks.findByName('jar') == null) throw new IllegalStateException("There is not a 'jar' task for ${project}")
            _sourceFile = (project.tasks.getByName('jar') as AbstractArchiveTask).archivePath
        }
        return _sourceFile
    }


    void setSymbolicName(String symbolicName) {
        this._symbolicName = symbolicName
    }


    @Nonnull
    String getSymbolicName() {
        if (_symbolicName == null) {
            OsgiHelper osgiHelper = new OsgiHelper()
            _symbolicName = osgiHelper.getBundleSymbolicName(project)
        }
        _symbolicName
    }


    Number getFelixId() {
        return _felixId
    }


    void setFelixId(Number id) {
        _felixId = id
    }

    /**
     * The URI of the bundle JAR
     */
    @Nonnull
    URI getBundleUri(URI baseUri, String installPath) {
        new URI(baseUri.scheme, baseUri.userInfo, baseUri.host, baseUri.port,
            "${baseUri.path}${getBundlePath(installPath)}", null, null).normalize()
    }


    @Nonnull
    String getBundlePath(String installPath) {
        "${getInstallPathForServer(installPath)}/${sourceFile.name}"
    }


    @Nonnull
    URI getBundleControlUrl(SimpleHttpClient httpClient, URI bundleControlUriBase, SlingSupport slingSupport) {
        final URI base = bundleControlUriBase
        if (felixId == null) {
            felixId = slingSupport.getIdForSymbolicName(symbolicName, httpClient)
            if (felixId == null) {
                log.warn("Could not find id for ${symbolicName}")
                return null
            }
        }
        new URI(base.scheme, base.userInfo, base.host, base.port, "${base.path}/${felixId}.json", null, null).normalize()
    }


    @Nonnull
    URI getBundleControlUrl(@Nonnull String serverName) {
        def serverConfiguration = slingServers[serverName]
        if (serverConfiguration == null) throw new IllegalArgumentException("Server \"${serverName}\" is unknown in ${slingServers.collect { SlingServerConfiguration conf -> conf.name }}")
        getBundleControlUrl(null, serverConfiguration.bundleControlBaseUri, serverConfiguration.slingSupport)
    }


    @Nonnull
    URI getBundleInformationUrl(SimpleHttpClient httpClient, SlingSupport slingSupport, URI bundleControlUri) {
        getBundleControlUrl(httpClient, bundleControlUri, slingSupport)
    }


    @Nonnull
    URI getBundleInformationUrl(SlingSupport slingSupport, URI uri) {
        getBundleInformationUrl(null, slingSupport, uri)
    }


    @Nonnull
    URI getBundleInformationUrl(@Nonnull String serverName) {
        getBundleControlUrl(serverName)
    }

}
