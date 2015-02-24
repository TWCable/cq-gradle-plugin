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

import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull

/**
 * Holds the configuration of a particular Sling server.
 *
 * <h2>Primary Properties</h2>
 * <ul>
 *     <li>name</li>
 *     <li>protocol</li>
 *     <li>port</li>
 *     <li>machineName</li>
 *     <li>username</li>
 *     <li>password</li>
 *     <li>installPath</li>
 *     <li>active</li>
 * </ul>
 */
@Slf4j
@TypeChecked
class SlingServerConfiguration {
    /**
     * The name to use for this server (e.g., "webcms-auth01-4502")
     */
    @Nonnull
    String name

    /**
     * The protocol for this server (http/https)
     */
    @Nonnull
    String protocol = 'http'

    /**
     * The port to connect to for this server
     */
    int port = 9999

    /**
     * The name of this server (e.g., "localhost")
     */
    @Nonnull
    String machineName = 'localhost'

    /**
     * The login to use for this server
     */
    @Nonnull
    String username = 'admin'

    /**
     * The password to use for this server
     */
    @Nonnull
    String password = 'admin'

    /**
     * Where to put any bundles on this server
     */
    @Nonnull
    String installPath = '/apps/install'

    @Nonnull
    private SlingSupport _slingSupport

    private boolean _active = true


    @Nonnull
    URI getBaseUri() {
        new URI(protocol, null, machineName, port, '/', null, null)
    }

    /**
     * Returns the SlingSupport instance to use to talk to the server.
     *
     * @return if it has not been set, a new instance is created
     */
    @Nonnull
    SlingSupport getSlingSupport() {
        if (_slingSupport == null) {
            _slingSupport = new SlingSupport(this)
        }
        _slingSupport
    }

    /**
     * Sets the SlingSupport instance to use to talk to the server.
     */
    void setSlingSupport(@Nonnull SlingSupport slingSupport) {
        _slingSupport = slingSupport
    }

    /**
     * Is this server active (responding to requests) or not?
     */
    boolean getActive() {
        return _active
    }

    /**
     * Is this server active (responding to requests) or not?
     */
    void setActive(boolean active) {
        if (_active && !active) {
            log.warn("Marking ${name} as being inactive")
        }
        _active = active
    }

    /**
     * Returns the base URL to control a bundle.
     */
    @Nonnull
    URI getBundleControlBaseUri() {
        URI base = getBaseUri()
        new URI(base.scheme, base.userInfo, base.host, base.port, '/system/console/bundles', null, null)
    }

    /**
     * Returns the URL to use to do actions on a bundle.
     */
    @Nonnull
    URI getBundleControlUriJson() {
        URI base = getBundleControlBaseUri()
        new URI(base.scheme, base.userInfo, base.host, base.port, "${base.path}.json", null, null)
    }

    /**
     * Returns the URL to use to do actions on a CQ package.
     */
    @Nonnull
    URI getPackageControlUri() {
        URI base = getBaseUri()
        new URI(base.scheme, base.userInfo, base.host, base.port, '/crx/packmgr/service/.json', null, null)
    }

    /**
     * Returns the URL to list all the CQ packages.
     */
    @Nonnull
    URI getPackageListUri() {
        URI base = getBaseUri()
        new URI(base.scheme, base.userInfo, base.host, base.port, '/crx/packmgr/list.jsp', null, null)
    }

    /**
     * Returns the URL to upload to when installing a bundle.
     */
    @Nonnull
    URI getBaseInstallUri() {
        final URI base = baseUri
        new URI(base.scheme, base.userInfo, base.host, base.port, "${base.path}${installPath}", null, null).normalize()
    }

    /**
     * Returns the URL to download a CQ package.
     */
    @Nonnull
    URI getDownloadUri() {
        URI base = getBaseUri()
        new URI(base.scheme, base.userInfo, base.host, base.port, '/crx/packmgr/download.jsp', null, null)
    }
}
