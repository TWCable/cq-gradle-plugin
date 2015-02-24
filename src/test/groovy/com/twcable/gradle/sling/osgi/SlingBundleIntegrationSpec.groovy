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
import com.twcable.gradle.sling.SlingSupport
import groovy.json.JsonBuilder
import org.apache.http.HttpEntity
import org.apache.http.HttpVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
@SuppressWarnings("GroovyAssignabilityCheck")
class SlingBundleIntegrationSpec extends Specification {
    SlingOsgiBundle slingBundle
    SlingSupport slingSupport
    SlingBundleConfiguration osgiBundleConfiguration


    def setup() {
        final slingServerConfiguration = new SlingServerConfiguration()

        osgiBundleConfiguration = new SlingBundleConfiguration(name: 'com.test.cq-utils',
            installPath: "/apps/install",
            sourceFile: new File("build/libs/cq5-poc-0.9.0.jar"),
            slingServer: slingServerConfiguration)

        slingBundle = new SlingOsgiBundle(osgiBundleConfiguration)
        slingSupport = slingBundle.slingSupport
    }


    SimpleHttpClient createHttpClient(int statusCode, HttpEntity entity) {
        SimpleHttpClient httpClient = Mock(SimpleHttpClient)
        httpClient.execute(_) >> {
            final response = new BasicHttpResponse(HttpVersion.HTTP_1_1, statusCode, "")
            response.entity = entity
            response
        }
        httpClient
    }


    def "install bundle"() {
        expect:
        slingBundle.installBundle()
    }


    def "uninstall bundle"() {
        expect:
        slingBundle.uninstallBundle(configuration.slingSupport)
    }


    def "remove bundle"() {
        expect:
        slingBundle.removeBundle(configuration.slingSupport, configuration.slingSupport.slingSupport.slingSupport, slingBundle.getBundleLocation(configuration.slingSupport, configuration.slingSupport.slingSupport.baseUri, configuration.slingSupport.slingSupport.installPath, configuration.slingSupport.slingSupport.name, configuration.slingSupport.slingSupport.slingSupport, configuration.slingSupport.slingSupport.bundleControlBaseUri))
    }


    def "start bundle"() {
        expect:
        slingBundle.startBundle(configuration.slingSupport, configuration.slingSupport.slingSupport.bundleControlBaseUri)
    }


    def "stop bundle"() {
        expect:
        slingBundle.stopBundle()
    }


    def "refresh bundle"() {
        expect:
        slingBundle.refreshBundle()
    }


    def "update bundle"() {
        expect:
        slingBundle.updateBundle(configuration.getBundleControlBaseUri, configuration.slingSupport.slingSupport.bundleControlBaseUri)
    }


    def "get sling bundle information"() {
        expect:
        println slingBundle.slingBundleInformation
    }


    def "upload new bundle"() {
        given:
        def changesArray = [
            [type: 'created', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar'],
            [type: 'created', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content'],
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:lastModified'],
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:mimeType'],
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:data'],
            [type: 'modified', argument: '/apps/cq-poc/install/_noredir_'],
        ]
        def data = [
            changes         : changesArray,
            path            : '/apps/cq-poc/install',
            location        : '/apps/cq-poc/install',
            parentLocation  : '/apps/cq-poc',
            'status.code'   : 200,
            'status.message': 'OK',
            title           : 'Content modified /apps/cq-poc/install',
            referer         : ''
        ]
        def jsonBuilder = new JsonBuilder(data)
        slingSupport.metaClass.createHttpClient = { -> createHttpClient(200, new StringEntity(jsonBuilder.toString())) }

        expect:
        slingBundle.uploadBundle()
    }


    def "upload existing bundle"() {
        given:
        def changesArray = [
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:lastModified'],
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:mimeType'],
            [type: 'modified', argument: '/apps/cq-poc/install/cq5-poc-0.9.0.jar/jcr:content/jcr:data'],
            [type: 'modified', argument: '/apps/cq-poc/install/_noredir_'],
        ]
        def data = [
            changes         : changesArray,
            path            : '/apps/cq-poc/install',
            location        : '/apps/cq-poc/install',
            parentLocation  : '/apps/cq-poc',
            'status.code'   : 200,
            'status.message': 'OK',
            title           : 'Content modified /apps/cq-poc/install',
            referer         : ''
        ]
        def jsonBuilder = new JsonBuilder(data)
        boolean madeParent = false
        slingSupport.metaClass.doMkcol = { String url, SimpleHttpClient httpClient ->
            if (madeParent || url.endsWith('/apps/cq-poc')) {
                madeParent = true
                [201, '']
            }
            else {
                [405, '']
            }
        }
        slingSupport.metaClass.doGet = { String url, SimpleHttpClient httpClient -> [404, ''] }
        slingSupport.metaClass.createHttpClient = { -> createHttpClient(200, new StringEntity(jsonBuilder.toString())) }

        expect:
        slingBundle.uploadBundle()
    }

}
