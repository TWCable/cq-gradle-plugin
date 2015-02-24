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

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AddBundlesToFilterXmlTaskSpec extends Specification {

    @Subject
    AddBundlesToFilterXmlTask task


    @Unroll
    def "check filter XML processing with native jars using #before and #bundleRoot"() {
        given:
        Project rootProject = createCqPackageProject('2.3.4', bundleRoot)

        createSubProject(rootProject, 'subproject1', true)
        createSubProject(rootProject, 'subproject2', true)
        createSubProject(rootProject, 'subproject3', false)

        task = rootProject.tasks.getByName('addBundlesToFilterXml') as AddBundlesToFilterXmlTask

        when:
        def processed = process(xml(before), ['groovy-all-2.1.6.jar', 'subproject1-2.3.4.jar'])

        then:
        xml(processed) == xml(after)

        where:
        before << [
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder")
                    filter(root: "/etc/clientlibs/formbuilder")
                }

            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder")
                    filter(root: "/etc/clientlibs/formbuilder")
                }

            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/install")
                    filter(root: "/apps/responsive")
                    filter(root: "/etc/clientlibs/responsive")
                }

            }
        ]
        after << [
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder") {
                        exclude(pattern: "/apps/formbuilder/install/20/groovy-all-2.1.6.jar")
                        exclude(pattern: "/apps/formbuilder/install/30/subproject1-2.3.4.jar")
                        exclude(pattern: "/apps/formbuilder/install/30/subproject2-2.3.4.jar")
                    }
                    filter(root: "/etc/clientlibs/formbuilder")
                }
            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder")
                    filter(root: "/etc/clientlibs/formbuilder")
                    filter(root: "/apps/install") {
                        exclude(pattern: "/apps/install/20/groovy-all-2.1.6.jar")
                        exclude(pattern: "/apps/install/30/subproject1-2.3.4.jar")
                        exclude(pattern: "/apps/install/30/subproject2-2.3.4.jar")
                    }
                }
            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/responsive")
                    filter(root: "/etc/clientlibs/responsive")
                    filter(root: "/apps/install") {
                        exclude(pattern: "/apps/install/20/groovy-all-2.1.6.jar")
                        exclude(pattern: "/apps/install/30/subproject1-2.3.4.jar")
                        exclude(pattern: "/apps/install/30/subproject2-2.3.4.jar")
                    }
                }
            }
        ]
        bundleRoot << [
            '/apps/formbuilder/install',
            '/apps/install',
            '/apps/install'
        ]
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************

    Project createCqPackageProject(String version, String bundleRoot) {
        Project project = ProjectBuilder.builder().build()
        project.version = version
        project.apply plugin: 'java'
        project.apply plugin: 'cqpackage'

        CqPackageConfiguration cqPackageConfiguration = project.extensions.findByType(CqPackageConfiguration)
        cqPackageConfiguration.dependencyStartLevel = '20'
        cqPackageConfiguration.nativeStartLevel = '30'
        cqPackageConfiguration.bundleInstallRoot = bundleRoot

        return project
    }


    Project createSubProject(Project parentProject, String projName, boolean makeOsgi) {
        Project project = ProjectBuilder.builder().withName(projName).withParent(parentProject).build()
        project.version = parentProject.version
        project.apply plugin: 'java'
        if (makeOsgi) project.apply plugin: 'osgi'
        return project
    }


    @SuppressWarnings("GroovyAssignabilityCheck")
    String xml(Closure closure) {
        def builder = new StreamingMarkupBuilder()
        XmlUtil.serialize(builder.bind(closure))
    }


    String xml(String xml) {
        XmlUtil.serialize(xml)
    }


    String process(String xml, Collection jarNames) {
        StringWriter writer = new StringWriter()

        task.filterXmlReader = new StringReader(xml)
        task.filterXmlWriter = writer
        task.jarFileNames = jarNames
        task.execute()

        return writer.toString()
    }

}
