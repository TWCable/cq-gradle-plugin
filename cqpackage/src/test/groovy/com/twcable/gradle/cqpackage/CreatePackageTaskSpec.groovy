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
import spock.lang.Subject
import spock.lang.Unroll

import java.util.jar.JarEntry
import java.util.jar.JarFile

import static com.twcable.gradle.GradleUtils.execute
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToCompile
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.contentDir
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.touch

@Subject(CreatePackageTask)
class CreatePackageTaskSpec extends Specification {

    Project project


    def setup() {
        project = CqPackageTestUtils.createCqPackageProject()
        project.verifyBundles.enabled = false

        def subproject1 = createSubProject(project, 'subproject1', true)
        addProjectToCompile(project, subproject1)
        def subprojJarFile = new File(subproject1.buildDir, "libs/subproject1.jar").canonicalFile
        touch(subprojJarFile)
        subproject1.jar.destinationDir = subprojJarFile.parentFile
        subproject1.jar.archiveName = subprojJarFile.name
        new File(subproject1.buildDir, "classes/main").mkdirs()

        def contentDir = contentDir(project)

        touch(new File(contentDir, "afile.txt"))
    }


    def cleanup() {
        assert project.projectDir.deleteDir()
    }


    def "create package with all bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addAllBundles()

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with no bundle option set"() {
        def createPackage = project.createPackage as CreatePackageTask

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with only project bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addProjectBundles()

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        filenames.contains("afile.txt")
        filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with only non-project bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNonProjectBundles()

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        filenames.contains("afile.txt")
        !filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "create package with no bundles"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNoBundles()

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        filenames.contains("afile.txt")
        !filenames.contains("jcr_root/apps/install/subproject1.jar")
    }


    def "the file exclusion filter is working"() {
        def createPackage = project.createPackage as CreatePackageTask
        createPackage.addNoBundles()
        def contentDir = contentDir(project)
        touch(new File(contentDir, ".gitignore"))
        touch(new File(contentDir, "jcr_root/.vlt-sync-config.properties"))

        when:
        com.twcable.gradle.GradleUtils.execute(createPackage)
        def filenames = filesInJar(createPackage)

        then:
        !filenames.contains(".gitignore")
        !filenames.contains("jcr_root/.vlt-sync-config.properties")
    }


    @Unroll
    def 'check bundleInstallRoot: #startBundleInstall'() {
        given:
        def createPackage = project.createPackage as CreatePackageTask

        if (startBundleInstall != null)
            createPackage.bundleInstallRoot = startBundleInstall

        expect:
        createPackage.bundleInstallRoot == bundleInstallRoot

        where:
        startBundleInstall | bundleInstallRoot
        null               | '/apps/install'
        '/my/app/root'     | '/my/app/root'
        'my/app/root'      | '/my/app/root'
        '/my/app/root/'    | '/my/app/root'
    }


    static List<String> filesInJar(CreatePackageTask createPackage) {
        return new JarFile(createPackage.archivePath).entries().iterator().collect { JarEntry entry -> entry.name }
    }

}
