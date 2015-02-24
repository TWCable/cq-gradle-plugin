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

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

/**
 * Handles reading the environment JSON file.
 * <p>
 * Sample of what the JSON looks like:
 * <pre>
 * &nbsp;{ &nbsp;
 *   "testEnv" : {&nbsp;
 *     "authors": {&nbsp;
 *       "cq-auth01": "4502",
 *       "cq-auth02": "4502"
 *     &nbsp;},
 *     "publishers": {&nbsp;
 *       "cq-pub01": "4503",
 *       "cq-pub02": "4503"
 *     &nbsp;},
 *     "dispatchers": [
 *       "cq-web01",
 *       "cq-web02"
 *     ],
 *     "cnames": [ "site1", "site2"],
 *     "domainName": "test.myco.com",
 *     "protocol": "http",
 *     "username": "admin",
 *     "password": "admin",
 *     "clusterAuths": true,
 *     "clusterPubs": false
 *   &nbsp;}&nbsp;
 *}&nbsp;
 * </pre>
 */
@Slf4j
@CompileStatic
class EnvironmentJsonFileReader {

    static Map<String, SlingServerConfiguration> getServersFromJsonFile(SlingServersConfiguration serversConf, String jsonFileName, String envName) {
        Map environment = getEnvironmentFromJsonFile(jsonFileName, envName)
        if (!environment) {
            log.warn "Could not find \"${envName}\" in \"${jsonFileName}\""
            return [:]
        }

        def authors = environment.authors as Map
        def publishers = environment.publishers as Map
        def clusterAuths = environment.clusterAuths as boolean
        def clusterPubs = environment.clusterPubs as boolean
        serversConf.clusterAuths = clusterAuths
        serversConf.clusterPubs = clusterPubs

        if (authors) {
            if (clusterAuths)
                return clusteredAuthors(environment, authors, publishers, clusterPubs)
            else
                return unclusteredAuthors(environment, publishers, authors, clusterPubs)
        }
        else {
            if (!publishers) throw new GradleException("There are no authors or publishers defined in ${jsonFileName}")

            return noAuthors(environment, clusterPubs, publishers)
        }
    }


    private static Map<String, SlingServerConfiguration> noAuthors(Map environment,
                                                                   boolean clusterPubs,
                                                                   Map publishers) {
        if (clusterPubs) { // clustered pubs, no auths
            def firstPubKey = publishers.keySet().first()
            def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

            return jsonMapToServerConfMap(environment, firstPub)
        }
        else { // unclustered pubs, no auths
            return jsonMapToServerConfMap(environment, publishers)
        }
    }


    private static Map<String, SlingServerConfiguration> unclusteredAuthors(Map environment,
                                                                            Map publishers,
                                                                            Map authors,
                                                                            boolean clusterPubs) {
        if (publishers) {
            if (clusterPubs) { // clustered pubs, unclustered auths
                def firstPubKey = publishers.keySet().first()
                def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

                def pubInstance = jsonMapToServerConfMap(environment, firstPub)
                return jsonMapToServerConfMap(environment, authors) + pubInstance
            }
            else { // unclustered pubs, unclustered auths
                return jsonMapToServerConfMap(environment, publishers) +
                    jsonMapToServerConfMap(environment, authors)
            }
        }
        else { // no pubs, unclustered auths
            return jsonMapToServerConfMap(environment, authors)
        }
    }


    private static Map<String, SlingServerConfiguration> clusteredAuthors(Map environment,
                                                                          Map authors,
                                                                          Map publishers,
                                                                          boolean clusterPubs) {
        def firstAuthorKey = authors.keySet().first()
        def firstAuthor = [(firstAuthorKey): "${authors[firstAuthorKey]}"]

        def authInstance = jsonMapToServerConfMap(environment, firstAuthor)
        if (publishers) {
            if (clusterPubs) { // clustered pubs, clustered auths
                def firstPubKey = publishers.keySet().first()
                def firstPub = [(firstPubKey): "${publishers[firstPubKey]}"]

                def pubInstance = jsonMapToServerConfMap(environment, firstPub)
                return authInstance + pubInstance
            }
            else { // unclustered pubs, clustered auths
                return jsonMapToServerConfMap(environment, publishers) + authInstance
            }
        }
        else { // no pubs, clustered auths
            return authInstance
        }
    }


    private static Map getEnvironmentFromJsonFile(String jsonFileName, String envName) {
        File jsonFile = new File(jsonFileName)

        def jsonEnvironments = new JsonSlurper().parseText(jsonFile.getText()) as Map

        return jsonEnvironments.get(envName) as Map
    }


    private static Map<String, SlingServerConfiguration> jsonMapToServerConfMap(Map environment, Map hostAndPort) {
        hostAndPort.collectEntries { hostname, port ->
            return ["${hostname}-${port}":
                        new SlingServerConfiguration(
                            name: "${hostname}-${port}", protocol: (String)environment.protocol, port: ((String)port).toInteger(),
                            machineName: "${hostname}.${environment.domainName}", username: (String)environment.username,
                            password: (String)environment.password
                        )] as Map<String, SlingServerConfiguration>
        }
    }

}
