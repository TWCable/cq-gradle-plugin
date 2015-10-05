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

import com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode
import org.gradle.api.Project
import org.hamcrest.Matcher
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addCompileDependency
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToCompile
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createCqPackageProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.xml
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.ALL
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NONE
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.NON_PROJECT_ONLY
import static com.twcable.gradle.cqpackage.CreatePackageTask.CopyBundlesMode.PROJECT_ONLY
import static spock.util.matcher.HamcrestSupport.expect

@Subject(FilterXmlWriter)
class FilterXmlWriterSpec extends Specification {

    @Unroll
    def "check filter XML processing with jars using #bundleRoot"() {
        given:
        Project rootProject = createCqPackageProject('2.3.4', bundleRoot)
        rootProject.verifyBundles.enabled = false

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)

        def subproject2 = createSubProject(rootProject, 'subproject2', true)
        addProjectToCompile(rootProject, subproject2)

        def subproject3 = createSubProject(rootProject, 'subproject3', false)
        addProjectToCompile(rootProject, subproject3)

        when:
        def processed = outputXml(rootProject, xml(before), ['groovy-all-2.1.6.jar'], ALL)

        then:
        expect processed, xmlEquals(xml(after))

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
                    filter(root: "/apps/formbuilder/install/subproject1-2.3.4.jar")
                    filter(root: "/apps/formbuilder/install/subproject2-2.3.4.jar")
                    filter(root: "/apps/formbuilder/install/groovy-all-2.1.6.jar")
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
        rootProject.verifyBundles.enabled = false

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
        def processed = outputXml(rootProject, xml(before), ['groovy-all-2.1.6.jar'], ALL)

        then:
        expect processed, xmlEquals(xml(after))
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
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], PROJECT_ONLY)

        then:
        expect processed, xmlEquals(xml(after))
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
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], NON_PROJECT_ONLY)

        then:
        expect processed, xmlEquals(xml(after))
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
        def processed = outputXml(project, xml(before), ['groovy-all-2.1.6.jar'], NONE)

        then:
        expect processed, xmlEquals(xml(after))
    }


    static Project simpleProject() {
        Project rootProject = createCqPackageProject('2.3.4', '/apps/install')
        rootProject.verifyBundles.enabled = false

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)
        return rootProject
    }


    static Matcher xmlEquals(String after) {
        CompareMatcher.isSimilarTo(after).
            withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndAllAttributes))
    }


    static String outputXml(Project project, String xml, Collection jarNames,
                            CopyBundlesMode copyBundlesMode) {
        jarNames.each { addCompileDependency(project, new File(it as String)) }

        StringWriter writer = new StringWriter()
        CreatePackageTask.from(project).copyBundlesMode = copyBundlesMode

        FilterXmlWriter.builder(project).
            inReader(new StringReader(xml)).
            outWriter(writer).
            build().
            run()

        return writer.toString()
    }

}
