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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import spock.lang.Specification
import spock.lang.Subject

@Subject(SlingSupport)
class SlingSupportSpec extends Specification {

    @SuppressWarnings("GroovyAssignabilityCheck")
    SlingServerFixture sf = SlingServerFixture.make {
        installPath '/apps/installx'
    }


    def "make_path"() {
        given:
        SimpleHttpClient httpClient = Mock(SimpleHttpClient) {
            2 * execute(_ as HttpPost) >> { HttpUriRequest req -> req.URI.path == '/apps' ? new HttpResponse(200, "") : new HttpResponse(500, "") }
            1 * execute(_ as SlingSupport.MkCol) >> { new HttpResponse(HttpURLConnection.HTTP_CREATED, '') }
        }

        final serverConfiguration = sf.slingServerConfiguration
        SlingSupport slingSupport = new SlingSupport(serverConfiguration)

        expect:
        slingSupport.makePath(serverConfiguration.baseInstallUri, httpClient)
    }


    def "populate symbolic name lookup"() {
        final serverConfiguration = sf.slingServerConfiguration
        SlingSupport slingSupport = new SlingSupport(serverConfiguration)
        SimpleHttpClient httpClient = Mock(SimpleHttpClient) {
            1 * execute(_ as HttpGet) >> { HttpUriRequest req -> new HttpResponse(200, sf.createBundlesJsonStr()) }
        }

        expect:
        slingSupport.getIdForSymbolicName('com.test.services', httpClient)

        // make sure the service only got called once
        slingSupport.getIdForSymbolicName('com.test.services', httpClient)
    }

}
