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

import com.twcable.gradle.http.DefaultSimpleHttpClient
import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingBundleFixture
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServerFixture
import com.twcable.gradle.sling.SlingSupport
import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.Unroll

import static BundleState.ACTIVE
import static BundleState.FRAGMENT
import static BundleState.INSTALLED
import static BundleState.RESOLVED
import static SlingOsgiBundle.doAcrossServers
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyPointlessArithmetic", "GroovyUntypedAccess", "GroovyPointlessBoolean"])
class SlingOsgiBundleSpec extends Specification {

    SlingBundleFixture slingBundleFixture
    SlingOsgiBundle slingBundle = slingBundleFixture

    final httpClient = new DefaultSimpleHttpClient()

    SlingBundleConfiguration bundleConfiguration


    def setup() {
        slingBundleFixture = SlingBundleFixture.make {
            symbolicName 'a.b.c.d'
            version '1.0.1'
            sourceFile new File("build/libs/a_b_c_d-1.0.1.jar")
        }
        slingBundle = slingBundleFixture.slingOsgiBundle
        bundleConfiguration = slingBundle.slingBundleConfig
    }


    def "uninstall bundle"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            (2..4) * it.doPost(_, { it['action'] == 'uninstall' }, _) >> {
                new HttpResponse(HTTP_OK, '{"state": "Active"}')
            }
        }

        expect:
        doAcrossServers(bundleConfiguration.slingServers) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
            slingBundle.uninstallBundle(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)
        }
    }


    @Unroll
    def "remove bundle on #srvr.name"(SlingServerConfiguration srvr) {
        given:
        mockSlingSupport {
            srvr.slingSupport = it
            it.getIdForSymbolicName(_, _) >> 24
            // Add the calls used for removing the existing bundle
            1 * it.doDelete(bundleConfiguration.getBundleUri(srvr.baseUri, srvr.installPath), _) >> new HttpResponse(HTTP_OK, '{"state": "Active"}')
            1 * it.doGet(bundleConfiguration.getBundleInformationUrl(srvr.slingSupport, srvr.bundleControlBaseUri), _) >> {
                new HttpResponse(HTTP_OK, slingBundleFixture.bundleInformationJson())
            }
        }

        def bundleLocation = slingBundle.getBundleLocation(httpClient, srvr.baseUri, srvr.installPath, srvr.name, srvr.slingSupport, srvr.bundleControlBaseUri)

        expect:
        slingBundle.removeBundle(httpClient, srvr.slingSupport, bundleLocation)

        where:
        srvr << SlingBundleFixture.make {
            symbolicName 'a.b.c.d'
            version '1.0.1'
            sourceFile new File("build/libs/a_b_c_d-1.0.1.jar")
        }.slingServersConfiguration.collect()
    }


    def "start bundle"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            (2..4) * it.doPost(_, { it['action'] == 'start' }, _) >> {
                new HttpResponse(HTTP_OK, '{"state": "Active"}')
            }
        }

        expect:
        doAcrossServers(bundleConfiguration.slingServers) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
            slingBundle.startBundle(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)
        }
    }


    def "stop bundle"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            (2..4) * it.doPost(_, { it['action'] == 'stop' }, _) >> { new HttpResponse(HTTP_OK, '{"state": "Active"}') }
        }

        expect:
        doAcrossServers(bundleConfiguration.slingServers) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
            slingBundle.stopBundle(httpClient, serverConfiguration)
        }
    }


    @SuppressWarnings("GroovyResultOfAssignmentUsed")
    def mockSlingSupport(Closure closure) {
        SlingSupport slingSupport = Mock()

        bundleConfiguration.slingServers.each { it.slingSupport = slingSupport }

        closure.call(slingSupport)

        slingSupport.doHttp(_) >> { Closure cls ->
            if (cls.parameterTypes.length == 1)
                cls.call(httpClient)
            else {
                cls.call(httpClient, bundleConfiguration)
            }
        }
        0 * slingSupport./do.*/(*_)

        slingSupport
    }


    def "refresh bundle"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            (2..4) * it.doPost(_, { it['action'] == 'refresh' }, _) >> {
                new HttpResponse(HTTP_OK, '{"state": "Active"}')
            }
        }

        expect:
        doAcrossServers(bundleConfiguration.slingServers) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
            slingBundle.refreshBundle(httpClient, serverConfiguration)
        }
    }


    def "update bundle"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            (2..4) * it.doPost(_, { it['action'] == 'update' }, _) >> {
                new HttpResponse(HTTP_OK, '{"state": "Active"}')
            }
        }

        expect:
        doAcrossServers(bundleConfiguration.slingServers) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
            slingBundle.updateBundle(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)
        }
    }


    def ok_response(String body) {
        new HttpResponse(HTTP_OK, body)
    }


    def "get sling bundle information"() {
        given:
        mockSlingSupport {
            it.getIdForSymbolicName(_, _) >> 44
            1 * it.doGet(_, _) >> { new HttpResponse(HTTP_OK, slingBundleFixture.bundleInformationJson()) }
        }

        expect:
        slingBundle.getSlingBundleInformation(httpClient, bundleConfiguration.slingServers.author.slingSupport, bundleConfiguration.slingServers.author.bundleControlBaseUri)
    }


    @Unroll
    def "upload new bundle to #srvr.name"(SlingServerConfiguration srvr) {
        given:
        mockSlingSupport {
            srvr.slingSupport = it
            it.getIdForSymbolicName(_, _) >> 44
            it.doGet(bundleConfiguration.getBundleInformationUrl(srvr.slingSupport, srvr.bundleControlBaseUri), _) >>> [
                new HttpResponse(HTTP_NOT_FOUND, ''), // shows that it's not there yet
                new HttpResponse(HTTP_OK, slingBundleFixture.bundleInformationJson())
            ]
            1 * it.makePath(_, _) >> { new HttpResponse(HTTP_OK, '') }
            1 * it.doPost(srvr.baseInstallUri, _, _) >> {
                new HttpResponse(HTTP_OK, slingBundleFixture.uploadFileResponse(srvr.installPath))
            }
        }

        when:
        final response = slingBundle.uploadBundle(httpClient, srvr)

        then:
        response.code == HTTP_OK
        (response as Map).path == bundleConfiguration.installPath

        where:
        srvr << SlingBundleFixture.make {
            symbolicName 'a.b.c.d'
            version '1.0.1'
            sourceFile new File("build/libs/a_b_c_d-1.0.1.jar")
        }.slingServersConfiguration.collect()
    }


    @Unroll
    def "upload existing bundle to #srvr.name"(SlingServerConfiguration srvr) {
        given:
        mockSlingSupport {
            srvr.slingSupport = it
            it.getIdForSymbolicName(_, _) >> 24
            it.doGet(bundleConfiguration.getBundleInformationUrl(srvr.slingSupport, srvr.bundleControlBaseUri), _) >> new HttpResponse(HTTP_OK, slingBundleFixture.bundleInformationJson())
            1 * it.makePath(_, _) >> { new HttpResponse(HTTP_OK, '') }
            1 * it.doPost(srvr.baseInstallUri, _, _) >> {
                new HttpResponse(HTTP_OK, slingBundleFixture.uploadFileResponse(srvr.installPath))
            }

            // Add the calls used for removing the existing bundle
            1 * it.doDelete(bundleConfiguration.getBundleUri(srvr.baseUri, srvr.installPath), _) >> new HttpResponse(HTTP_OK, '')
            1 * it.doPost(bundleConfiguration.getBundleControlUrl(httpClient, srvr.bundleControlBaseUri, srvr.slingSupport), {
                it['action'] == 'uninstall'
            }, _)
        }

        when:
        final response = slingBundle.uploadBundle(httpClient, srvr)

        then:
        bundleConfiguration.felixId == 24
        response.code == HTTP_OK
        (response as Map).path == bundleConfiguration.installPath

        where:
        srvr << SlingBundleFixture.make {
            symbolicName 'a.b.c.d'
            version '1.0.1'
            sourceFile new File("build/libs/a_b_c_d-1.0.1.jar")
        }.slingServersConfiguration.collect()
    }


    def "validate all bundles: resolved -> active"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasResolved = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState RESOLVED
            }
        }

        SlingServerFixture slingServerFixtureAllActive = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState ACTIVE
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf >> [name: 'testAuthor']
            final responseWithResolved = new HttpResponse(HTTP_OK, slingServerFixtureHasResolved.bundlesInformationJson())
            final responseWithActive = new HttpResponse(HTTP_OK, slingServerFixtureAllActive.bundlesInformationJson())
            4 * it.doGet(srvr.bundleControlUriJson, _) >>> [responseWithResolved, responseWithResolved, responseWithResolved, responseWithActive]
        }

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], httpClient, srvr)

        then:
        notThrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    def "start bundles"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasResolved = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState INSTALLED
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf() >> [name: 'testAuthor']
            final responseWithInstalled = new HttpResponse(HTTP_OK, slingServerFixtureHasResolved.bundlesInformationJson())
            it.doGet(srvr.bundleControlUriJson, _) >>> responseWithInstalled
        }

        when:
        slingBundle.startInactiveBundles(httpClient, srvr)

        then:
        notThrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    def "validate all bundles: resolved"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasResolved = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState RESOLVED
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf >> [name: 'testAuthor']
            final responseWithResolved = new HttpResponse(HTTP_OK, slingServerFixtureHasResolved.bundlesInformationJson())
            it.doGet(srvr.bundleControlUriJson, _) >>> responseWithResolved
        }

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], httpClient, srvr)

        then:
        thrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    def "validate all bundles: installed"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasResolved = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState INSTALLED
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf >> [name: 'testAuthor']
            final responseWithResolved = new HttpResponse(HTTP_OK, slingServerFixtureHasResolved.bundlesInformationJson())
            it.doGet(srvr.bundleControlUriJson, _) >>> responseWithResolved
        }

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], httpClient, srvr)

        then:
        thrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    def "validate all bundles: fragment"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasFragment = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState FRAGMENT
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf >> [name: 'testAuthor']
            final responseWithFragment = new HttpResponse(HTTP_OK, slingServerFixtureHasFragment.bundlesInformationJson())
            it.doGet(srvr.bundleControlUriJson, _) >>> responseWithFragment
        }

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], httpClient, srvr)

        then:
        notThrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    @Unroll
    def "validate all bundles on #srvr.name: missing"(SlingServerConfiguration srvr) {
        given:
        SlingServerFixture slingServerFixtureHasResolved = SlingServerFixture.make {
            add SlingBundleFixture.make {
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.serverConf >> [name: 'testAuthor']
            final responseWithResolved = new HttpResponse(HTTP_OK, slingServerFixtureHasResolved.bundlesInformationJson())
            it.doGet(srvr.bundleControlUriJson, _) >>> responseWithResolved
        }

        when:
        slingBundle.validateAllBundles(['b.c.d.e'], httpClient, srvr)

        then:
        thrown(GradleException)

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    @Unroll
    def "checkActiveBundles for ACTIVE on #srvr.name"(SlingServerConfiguration srvr) {
        SlingServerFixture slingServerFixtureAllActive = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'b.c.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState ACTIVE
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.doGet(srvr.bundleControlUriJson, _) >>> new HttpResponse(HTTP_OK, slingServerFixtureAllActive.bundlesInformationJson())
        }

        when:
        slingBundle.checkActiveBundles("com.test1", httpClient, srvr)

        then:
        true // the fact that no exception was thrown shows that it's good

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    @Unroll
    def "checkActiveBundles for RESOLVED on #srvr.name"(SlingServerConfiguration srvr) {
        SlingServerFixture slingServerFixtureAllActive = SlingServerFixture.make {
            add SlingBundleFixture.make {
                symbolicName 'com.test.d.e'
                version '1.0.2'
                sourceFile new File("build/libs/b_c_d_e-1.0.2.jar")
                bundleState RESOLVED
            }
        }

        mockSlingSupport {
            srvr.slingSupport = it
            it.doGet(srvr.bundleControlUriJson, _) >>> new HttpResponse(HTTP_OK, slingServerFixtureAllActive.bundlesInformationJson())
        }

        when:
        slingBundle.checkActiveBundles("com.test", httpClient, srvr)

        then:
        def exp = thrown(GradleException)
        exp.message.contains("Not all bundles are ACTIVE")

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }


    @Unroll
    def "bad checkActiveBundles for TIMEOUT on #srvr.name"(SlingServerConfiguration srvr) {
        mockSlingSupport {
            srvr.slingSupport = it
            it.doGet(srvr.bundleControlUriJson, _) >>> new HttpResponse(HTTP_CLIENT_TIMEOUT, '')
        }

        when:
        slingBundle.checkActiveBundles("com.test1", httpClient, srvr)

        then:
        srvr.active == false

        where:
        srvr << SlingBundleFixture.make {}.slingServersConfiguration.collect()
    }

}
