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

import com.twcable.gradle.http.SimpleHttpClient
import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

/**
 * Holds the configuration of the Sling servers that the build should care about.
 *
 * <h3>Primary Properties</h3>
 * <ul>
 *     <li>retryWaitMs</li>
 *     <li>maxWaitValidateBundlesMs</li>
 *     <li>clusterAuths</li>
 *     <li>clusterPubs</li>
 * </ul>
 *
 * <h3>System Properties</h3>
 * If "envJson" and "environment" properties are defined, the list of servers for this environment are extracted from
 * the JSON file.  See {@link EnvironmentJsonFileReader}.
 *
 * @see SlingServersConfiguration#retryWaitMs
 * @see SlingServersConfiguration#maxWaitValidateBundlesMs
 * @see SlingServersConfiguration#clusterAuths
 * @see SlingServersConfiguration#clusterPubs
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyMapPutCanBeKeyedAccess", "GroovyMapGetCanBeKeyedAccess"])
class SlingServersConfiguration implements Iterable<SlingServerConfiguration> {
    /**
     * The name to register this under in the project extensions
     */
    public static final String NAME = 'slingServers'

    @SuppressWarnings("GrFinalVariableAccess")
    private final Map<String, SlingServerConfiguration> servers

    /**
     * When a command fails (usually because the server is busy), how many milliseconds should
     * we wait before trying again? Defaults to 1 second.
     */
    long retryWaitMs = 1000

    /**
     * When a command fails (usually because the server is busy), how many milliseconds should
     * we wait at most before giving up? Defaults to 10 seconds.
     */
    long maxWaitValidateBundlesMs = 10000

    /**
     * Are the authors clustered? Defaults to false.
     */
    boolean clusterAuths

    /**
     * Predicate taking a bundle's name and returning true if it should uninstall the bundle as part
     * of "uninstallAllBundles".
     *
     * @see XSlingOsgiBundle#uninstallAllBundles(List, SimpleHttpClient, SlingServerConfiguration, Closure)
     */
    Closure<Boolean> uninstallBundlesPredicate

    /**
     * Are the publishers clustered? Defaults to false.
     */
    boolean clusterPubs


    SlingServersConfiguration() {
        def envJsonFilename = System.getProperty('envJson')
        def envName = System.getProperty('environment')

        if (envJsonFilename) {
            if (!envName) {
                throw new GradleException("When using the 'envJson' property, you must also specify the 'environment' property")
            }
            servers = EnvironmentJsonFileReader.getServersFromJsonFile(this, envJsonFilename, envName)
        }
        else {
            servers = defaultServers()
        }
    }


    private static Map defaultServers() {
        log.info("JSON environments file not passed in. Defaulting to localhost environment.")

        return [
            author   : new SlingServerConfiguration(
                name: 'author', protocol: 'http', port: 4502, machineName: 'localhost',
                username: 'admin', password: 'admin'),
            publisher: new SlingServerConfiguration(
                name: 'publisher', protocol: 'http', port: 4503, machineName: 'localhost',
                username: 'admin', password: 'admin'),
        ]
    }


    void propertyMissing(String name, SlingServerConfiguration configuration) {
        servers.put(name, configuration)
    }


    SlingServerConfiguration propertyMissing(String name) {
        servers.get(name)
    }


    SlingServerConfiguration getAt(String name) {
        servers.get(name)
    }


    void putAt(String name, SlingServerConfiguration configuration) {
        servers.put(name, configuration)
    }


    @Override
    Iterator<SlingServerConfiguration> iterator() {
        servers.values().findAll { it.active }.iterator()
    }

}
