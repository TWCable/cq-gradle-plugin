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

import com.twcable.gradle.sling.SlingServersConfiguration
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class SlingBundlePluginSpec extends Specification {

    @SuppressWarnings("GroovyMissingReturnStatement")
    def "check types"() {
        given:
        Project project = ProjectBuilder.builder().withName('sling-bundle-plugin-test').build()
        project.with {
            version = '1.2.5'
            apply plugin: JavaPlugin
            apply plugin: SlingBundlePlugin
            bundle.installPath '/apps/gradle_test/install'
        }

        expect:
        project.slingServers instanceof SlingServersConfiguration
        project.bundle instanceof SlingBundleConfiguration
        project.bundle.sourceFile == project.jar.archivePath
        project.jar.archivePath == project.file('build/libs/sling-bundle-plugin-test-1.2.5.jar')
        project.bundle.getBundlePath(project.slingServers.author.installPath) == '/apps/gradle_test/install/sling-bundle-plugin-test-1.2.5.jar'
    }

}
