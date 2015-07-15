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

package com.twcable.gradle.cqpackage

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.json.JsonBuilder
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Subject

import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings(["GroovyAssignabilityCheck", "GroovyAccessibility"])
class CqPackageHelperSpec extends Specification {
    @Subject
    CqPackageHelper cqPackageHelper

    def slingServersConfiguration = Mock(SlingServersConfiguration)
    def slingServerConfiguration = Mock(SlingServerConfiguration)
    def slingSupport = Mock(SlingSupport)


    def setup() {
        slingServersConfiguration.maxWaitValidateBundlesMs >> 1000
        slingServersConfiguration.retryWaitMs >> 10
        slingServersConfiguration.iterator() >> [slingServerConfiguration].iterator()
        slingServerConfiguration.getSlingSupport() >> slingSupport
        slingServerConfiguration.getActive() >> true
        SimpleHttpClient httpClient = Mock(SimpleHttpClient)
        slingSupport.doHttp(_) >> { Closure closure -> closure.delegate = slingSupport; closure.call(httpClient) }

        Project project = ProjectBuilder.builder().withName("fakepackage").build()

        project.extensions.add('slingServers', slingServersConfiguration)

        cqPackageHelper = project.extensions.create(CqPackageHelper.NAME, CqPackageHelper, project)

        project.tasks.create('createPackage', CreatePackageTask)
    }


    def "upload package, no problems"() {
        1 * slingSupport.doPost(_, _, _) >> { new HttpResponse(HTTP_OK, '{"success": true, "msg": "File uploaded"}') }

        expect:
        cqPackageHelper.uploadPackage()
    }


    def "install package, no problems"() {
        def json = new JsonBuilder(packageList)
        1 * slingSupport.doGet(_, _) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _, _) >> { new HttpResponse(HTTP_OK, '{"success": true, "msg": "File installed"}') }

        expect:
        cqPackageHelper.installPackage()
    }


    def "delete package, no problems"() {
        def json = new JsonBuilder(packageList)
        1 * slingSupport.doGet(_, _) >> { new HttpResponse(HTTP_OK, json.toString()) }
        1 * slingSupport.doPost(_, _, _) >> { new HttpResponse(HTTP_OK, '{"success": true, "msg": "File deleted"}') }

        expect:
        cqPackageHelper.deletePackage()
    }


    def "upload package, package exists"() {
        1 * slingSupport.doPost(_, _, _) >> {
            new HttpResponse(HTTP_OK, '{"success": false, "msg": "Package already exists: fakepackage"}')
        }
//        slingSupport.doGet(_, _) >> new HttpResponse(HTTP_OK, '{}')
//        0 * slingSupport._

        expect:
        cqPackageHelper.uploadPackage()
    }


    def "uninstall package, package was installed"() {
        1 * slingSupport.doPost(_, _, _) >> {
            new HttpResponse(HTTP_OK, '{"success": true, "msg": "Package uninstalled"}')
        }

        cqPackageHelper.metaClass.getPackageInfo = { SlingServerConfiguration serverConfig -> [path: '/etc/packages/fackepackage-1.0.1-SNAPSHOT'] }
        cqPackageHelper.uninstallCommand.logger = Mock(Logger)

        when:
        cqPackageHelper.uninstallPackage()

        then:
        1 * cqPackageHelper.uninstallCommand.logger.info({ it =~ /Package uninstalled/ })
    }


    def "uninstall package, package exists but is not installed"() {
        1 * slingSupport.doPost(_, _, _) >> {
            new HttpResponse(HTTP_OK, '{"success": false, "msg": "Unable to uninstall package. No snapshot present."}')
        }

        cqPackageHelper.metaClass.getPackageInfo = { SlingServerConfiguration serverConfig -> [path: '/etc/packages/fackepackage-1.0.1-SNAPSHOT'] }
        cqPackageHelper.uninstallCommand.logger = Mock(Logger)

        when:
        cqPackageHelper.uninstallPackage()

        then:
        1 * cqPackageHelper.uninstallCommand.logger.info({ it =~ / is not installed on / })
    }


    def "listPackages for a server configuration"() {
        def json = new JsonBuilder(packageList)
        1 * slingSupport.doGet(_, _) >> { new HttpResponse(HTTP_OK, json.toString()) }

        expect:
        cqPackageHelper.listPackages(slingServerConfiguration)
    }


    def "getPackageInfo for a server configuration"() {
        def json = new JsonBuilder(packageList)
        slingSupport.doGet(_, _) >> { new HttpResponse(HTTP_OK, json.toString()) }
        slingServerConfiguration.getPackageListUri() >> URI.create('http://test')

        expect:
        cqPackageHelper.getPackageInfo(slingServerConfiguration)
    }


    def packageList = [
        results: [
            [
                pid            : "day/cq550/product:cq-content:5.5.0.20120220",
                path           : "/etc/packages/day/cq550/product/cq-content-5.5.0.20120220.zip",
                name           : "cq-content",
                downloadName   : "cq-content-5.5.0.20120220.zip",
                group          : "day/cq550/product",
                groupTitle     : "day",
                version        : "5.5.0.20120220",
                description    : "Default installation package that contains repository content for CQ5.",
                thumbnail      : "/crx/packmgr/thumbnail.jsp?_charset_=utf-8&path=%2fetc%2fpackages%2fday%2fcq550%2fproduct%2fcq-content-5.5.0.20120220.zip&ck=1345049949526",
                buildCount     : 0,
                created        : 1329751518460,
                createdBy      : "Adobe Systems Incorporated",
                lastUnpacked   : 1345049995338,
                lastUnpackedBy : "admin",
                lastUnwrapped  : 1345049949532,
                lastUnwrappedBy: "admin",
                size           : 78532000,
                hasSnapshot    : false,
                needsRewrap    : false,
                builtWith      : "Adobe CQ5-5.5.0.SNAPSHOT",
                requiresRoot   : false,
                requiresRestart: false,
                acHandling     : "merge_preserve",
                filter         : [
                    [root: "/var", rules: [[modifier: "include", pattern: "/var/classes(/.*)?"],
                                           [modifier: "include", pattern: "/var/audit"],
                                           [modifier: "include", pattern: "/var/proxy(/.*)?"]]],
                    [root: "/", rules: [[modifier: "include", pattern: "/"],
                                        [modifier: "include", pattern: "/var"]]]],
                screenshots    : []
            ],
            [
                pid            : "twc/webcms:fakepackage:1.0.1-SNAPSHOT",
                path           : "/etc/packages/fakepackage-1.0.1-SNAPSHOT.zip",
                name           : "fakepackage",
                downloadName   : "fakepackage-1.0.1-SNAPSHOT.zip",
                group          : "twc/webcms",
                groupTitle     : "twc",
                version        : "1.0.1-SNAPSHOT",
                description    : "FakePackage - JCR",
                thumbnail      : "/crx/packmgr/thumbnail.jsp?_charset_=utf-8&path=%2fetc%2fpackages%2ffakepackage-1.0.1-SNAPSHOT.zip&ck=1359577761522",
                buildCount     : 1,
                lastUnwrapped  : 1359577685665,
                size           : 24277786,
                hasSnapshot    : false,
                needsRewrap    : true,
                builtWith      : "Adobe CQ5-5.5.0",
                requiresRoot   : true,
                requiresRestart: false,
                acHandling     : "overwrite",
                dependencies   : [],
                providerName   : "Time Warner Cable",
                providerUrl    : "http://www.timewarnercable.com",
                filter         : [
                    [root: "/apps/common", rules: []],
                    [root: "/etc/clientlibs/common", rules: []],
                    [root: "/etc/designs/common", rules: [[modifier: "exclude", pattern: "/etc/designs/common/jcr:content/(.*)?"]]],
                    [root: "/var/classes/org/apache/jsp/apps", rules: []],
                    [root: "/var/clientlibs/apps", rules: []],
                    [root: "/var/clientlibs/etc", rules: []]],
                screenshots    : []
            ]
        ],
        total  : 16]

}

/*
When uninstall an uploaded package that is not installed.
status code: 200; {"success":false,"msg":"Unable to uninstall package. No snapshot present."}

*/
