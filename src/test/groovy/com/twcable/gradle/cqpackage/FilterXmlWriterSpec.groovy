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

import org.gradle.api.Project
import spock.lang.Specification
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addCompileDependency
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToCompile
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createCqPackageProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.xml
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.xmlString

class FilterXmlWriterSpec extends Specification {

    @Unroll
    def "check filter XML processing with jars using #before and #bundleRoot"() {
        given:
        Project rootProject = createCqPackageProject('2.3.4', bundleRoot)

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)

        def subproject2 = createSubProject(rootProject, 'subproject2', true)
        addProjectToCompile(rootProject, subproject2)

        def subproject3 = createSubProject(rootProject, 'subproject3', false)
        addProjectToCompile(rootProject, subproject3)

        when:
        def processed = outputXml(rootProject, xml(before), ['groovy-all-2.1.6.jar'], true, true)

        then:
        xmlString(processed) == xml(after)

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
                    filter(root: "/apps/responsive")
                    filter(root: "/etc/clientlibs/responsive")
                }
            }
        ]

        after << [
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder")
                    filter(root: "/etc/clientlibs/formbuilder")
                    filter(root: "/apps/formbuilder/install/groovy-all-2.1.6.jar")
                    filter(root: "/apps/formbuilder/install/subproject1-2.3.4.jar")
                    filter(root: "/apps/formbuilder/install/subproject2-2.3.4.jar")
                }
            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/formbuilder")
                    filter(root: "/etc/clientlibs/formbuilder")
                    filter(root: "/apps/install/groovy-all-2.1.6.jar")
                    filter(root: "/apps/install/subproject1-2.3.4.jar")
                    filter(root: "/apps/install/subproject2-2.3.4.jar")
                }
            },
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/responsive")
                    filter(root: "/etc/clientlibs/responsive")
                    filter(root: "/apps/install/groovy-all-2.1.6.jar")
                    filter(root: "/apps/install/subproject1-2.3.4.jar")
                    filter(root: "/apps/install/subproject2-2.3.4.jar")
                }
            }
        ]

        bundleRoot << [
            '/apps/formbuilder/install',
            '/apps/install',
            '/apps/install'
        ]
    }


    def "does not include subprojects that are not dependencies"() {
        given:
        Project rootProject = createCqPackageProject('2.3.4', '/apps/install')

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)
        createSubProject(rootProject, 'subproject2', true)
        createSubProject(rootProject, 'subproject3', false)

        def before =
            {
                workspaceFilter(version: "1.0")
            }

        def after =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/install/groovy-all-2.1.6.jar")
                    filter(root: "/apps/install/subproject1-2.3.4.jar")
                }
            }

        when:
        def processed = outputXml(rootProject, xml(before), ['groovy-all-2.1.6.jar'], true, true)

        then:
        xmlString(processed) == xml(after)
    }


    def "does not include external bundles"() {
        given:
        def project = simpleProject()

        def before =
            {
                workspaceFilter(version: "1.0")
            }

        def after =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/apps/install/subproject1-2.3.4.jar")
                }
            }

        when:
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], false, true)

        then:
        xmlString(processed) == xml(after)
    }


    def "only includes external bundles"() {
        given:
        def project = simpleProject()

        def before =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/content/stuff")
                }
            }

        def after =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/content/stuff")
                    filter(root: "/apps/install/groovy-all-2.1.6.jar")
                }
            }

        when:
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], true, false)

        then:
        xmlString(processed) == xml(after)
    }


    def "does not include any bundles"() {
        given:
        def project = simpleProject()

        def before =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/content/stuff")
                }
            }

        def after =
            {
                workspaceFilter(version: "1.0") {
                    filter(root: "/content/stuff")
                }
            }

        when:
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], false, false)

        then:
        xmlString(processed) == xml(after)
    }


    static Project simpleProject() {
        Project rootProject = createCqPackageProject('2.3.4', '/apps/install')

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)
        return rootProject
    }


    static String outputXml(Project project, String xml, Collection jarNames,
                            boolean nonProjBundles, boolean projBundles) {
        jarNames.each { addCompileDependency(project, new File(it as String)) }

        StringWriter writer = new StringWriter()

        FilterXmlWriter.builder().
            project(project).
            inReader(new StringReader(xml)).
            outWriter(writer).
            includeNonProjectBundles(nonProjBundles).
            includeProjectBundles(projBundles).
            build().
            run()

        return writer.toString()
    }

}
