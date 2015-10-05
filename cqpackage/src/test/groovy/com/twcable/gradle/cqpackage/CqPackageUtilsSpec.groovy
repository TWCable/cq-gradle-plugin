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

import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addCompileDependency
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToCompile
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createCqPackageProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject

class CqPackageUtilsSpec extends Specification {
    def "ProjectBundleFiles"() {
        Project rootProject = createCqPackageProject('2.3.4', "/apps/install")
        addCompileDependency(rootProject, new File("groovy-2.3.jar"))

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)
        addCompileDependency(subproject1, new File("fooble-2.4.jar"))

        def subproject2 = createSubProject(rootProject, 'subproject2', true)
        addProjectToCompile(rootProject, subproject2)

        def subproject3 = createSubProject(rootProject, 'subproject3', false)
        addProjectToCompile(rootProject, subproject3)

        when:
        def files = CqPackageUtils.allProjectBundleJarFiles(rootProject, rootProject.configurations.cq_package)
        def fileNames = files.collect { it.name } as Set

        then:
        fileNames == ["subproject1-2.3.4.jar", "subproject2-2.3.4.jar"] as Set
    }


    def "ProjectDependencyProjects"() {
        Project rootProject = createCqPackageProject('2.3.4', "/apps/install")

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)

        def subproject2 = createSubProject(rootProject, 'subproject2', true)
        addProjectToCompile(rootProject, subproject2)

        def subproject3 = createSubProject(rootProject, 'subproject3', false)
        addProjectToCompile(rootProject, subproject3)

        when:
        def projects = CqPackageUtils.projectDependencyProjects(rootProject.configurations.cq_package)
        def projNames = projects.collect { it.name } as Set

        then:
        projNames == ["subproject1", "subproject2", "subproject3"] as Set
    }


    def "NonProjectDependencyBundleFiles"() {
        Project rootProject = createCqPackageProject('2.3.4', "/apps/install")
        addCompileDependency(rootProject, new File("groovy-2.3.jar"))

        def subproject1 = createSubProject(rootProject, 'subproject1', true)
        addProjectToCompile(rootProject, subproject1)

        def subproject2 = createSubProject(rootProject, 'subproject2', true)
        addProjectToCompile(subproject1, subproject2)
        addCompileDependency(subproject2, new File("fooble-2.4.jar"))

        def subproject3 = createSubProject(rootProject, 'subproject3', false)
        addProjectToCompile(rootProject, subproject3)

        when:
        def files = CqPackageUtils.nonProjectDependencyBundleFiles(rootProject.configurations.cq_package)
        def fileNames = files.collect { it.name } as Set

        then:
        fileNames == ["groovy-2.3.jar", "fooble-2.4.jar"] as Set
    }

}
