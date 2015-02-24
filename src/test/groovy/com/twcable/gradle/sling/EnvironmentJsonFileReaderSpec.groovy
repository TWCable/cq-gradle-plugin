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

import org.gradle.api.GradleException
import org.junit.After
import spock.lang.Specification
import spock.lang.Subject

@Subject(EnvironmentJsonFileReader)
@SuppressWarnings("GroovyPointlessBoolean")
class EnvironmentJsonFileReaderSpec extends Specification {

    @After
    public void clearProperties() {
        System.clearProperty("envJson");
        System.clearProperty("environment");
    }


    def "can parse envJson for expected servers"() {
        given:
        System.setProperty("envJson", createEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.clusterAuths == true
        servers.clusterPubs == false
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-pub01-4503",
                                                             "cq-pub02-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-pub01.test.myco.com",
                                                                    "cq-pub02.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse no cluster envJson for expected servers"() {
        given:
        System.setProperty("envJson", createNoClusterEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.clusterAuths == false
        servers.clusterPubs == false
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-auth02-4502",
                                                             "cq-pub01-4503",
                                                             "cq-pub02-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-auth02.test.myco.com",
                                                                    "cq-pub01.test.myco.com",
                                                                    "cq-pub02.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse cluster all envJson for expected servers"() {
        given:
        System.setProperty("envJson", createClusterAllEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.clusterAuths == true
        servers.clusterPubs == true
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-pub01-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-pub01.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse cluster pubs envJson for expected servers"() {
        given:
        System.setProperty("envJson", createClusterPubsEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.clusterAuths == false
        servers.clusterPubs == true
        servers.collect { it.name }.sort().unique(false) == ["cq-auth01-4502",
                                                             "cq-auth02-4502",
                                                             "cq-pub01-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["cq-auth01.test.myco.com",
                                                                    "cq-auth02.test.myco.com",
                                                                    "cq-pub01.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse same hostname envJson for expected servers"() {
        given:
        System.setProperty("envJson", createSameHostnameEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.collect { it.name }.sort().unique(false) == ["localhost-4502",
                                                             "localhost-4503"]
        servers.collect { it.protocol }.unique(false) == ["http"]
        servers.collect { it.port }.containsAll([4502, 4503])
        servers.collect { it.machineName }.sort().unique(false) == ["localhost.test.myco.com"]
        servers.collect { it.username }.unique(false) == ["admin"]
        servers.collect { it.password }.unique(false) == ["admin"]
    }


    def "can parse no-auth envJson for expected servers"() {
        given:
        System.setProperty("envJson", createNoAuthEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.collect { it.name }.containsAll(["cq-pub01-4503", "cq-pub02-4503"])
        servers.collect { it.protocol }.contains("http")
        servers.collect { it.port }.containsAll([4503])
        servers.collect { it.machineName }.containsAll(["cq-pub01.test.myco.com",
                                                        "cq-pub02.test.myco.com"])
        servers.collect { it.username }.contains("admin")
        servers.collect { it.password }.contains("admin")
    }


    def "can parse no-pub envJson for expected servers"() {
        given:
        System.setProperty("envJson", createNoPubEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.collect { it.name }.containsAll(["cq-auth01-4502"])
        servers.collect { it.protocol }.contains("http")
        servers.collect { it.port }.containsAll([4502])
        servers.collect { it.machineName }.containsAll(["cq-auth01.test.myco.com"])
        servers.collect { it.username }.contains("admin")
        servers.collect { it.password }.contains("admin")
    }


    @SuppressWarnings("GroovyResultOfObjectAllocationIgnored")
    def "can parse no-auth and no-pub envJson for expected servers"() {
        given:
        System.setProperty("envJson", createNoAuthNoPubEnvJsonFile().absolutePath)
        System.setProperty("environment", "testEnv")

        when:
        new SlingServersConfiguration()

        then:
        true // some weird compile bug makes this required
        thrown(GradleException)
    }


    static File createEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }


    static File createNoClusterEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": false,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }


    static File createClusterAllEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": true
              }
            }
            ''')
        return envJson
    }


    static File createClusterPubsEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": false,
                "clusterPubs": true
              }
            }
            ''')
        return envJson
    }


    static File createSameHostnameEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "localhost": "4502"
                },
                "publishers": {
                  "localhost": "4503"
                },
                "dispatchers": [
                  "localhost"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }


    static File createNoPubEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "authors": {
                  "cq-auth01": "4502",
                  "cq-auth02": "4502"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }


    static File createNoAuthEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "publishers": {
                  "cq-pub01": "4503",
                  "cq-pub02": "4503"
                },
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }


    static File createNoAuthNoPubEnvJsonFile() {
        File envJson = File.createTempFile('envJson', 'json')
        envJson.write('''
            {
              "testEnv" : {
                "dispatchers": [
                  "cq-web01",
                  "cq-web02"
                ],
                "cnames": [ "site1", "site2"],
                "domainName": "test.myco.com",
                "protocol": "http",
                "username": "admin",
                "password": "admin",
                "clusterAuths": true,
                "clusterPubs": false
              }
            }
            ''')
        return envJson
    }

}
