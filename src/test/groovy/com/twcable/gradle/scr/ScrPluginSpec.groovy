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

package com.twcable.gradle.scr

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject

@Subject(ScrPlugin)
class ScrPluginSpec extends Specification {
    Project project


    def setupProjectOne() {
        setupProject('/scr/proj')
    }


    def setupProjectTwo() {
        setupProject('/scr/proj2')
    }


    def setupProject(final path) {
        // project contains @Reference SCR annotation to a file that is not an interface
        project = ProjectBuilder.builder().withProjectDir(file(path)).build()
        project.repositories.flatDir(dirs: file('/scr/lib'))

        project.apply plugin: 'groovy'
        project.apply plugin: ScrPlugin

        final dependencies = project.configurations.getByName('compile').dependencies
        dependencies.add(new DefaultExternalModuleDependency('org.apache.felix', 'org.apache.felix.scr.annotations', '1.7.0'))
        dependencies.add(new DefaultExternalModuleDependency('javax.servlet', 'servlet-api', '2.5'))
        dependencies.add(project.dependencies.localGroovy())

        (project.tasks.getByName('clean') as DefaultTask).execute()
        (project.tasks.getByName('compileGroovy') as DefaultTask).execute()
        (project.tasks.getByName('processScrAnnotations') as DefaultTask).execute()
    }


    def "check serviceComponents components"() {
        when:
        setupProjectOne()
        final serviceComponentsFile = file('/scr/proj/build/classes/main/OSGI-INF/serviceComponents.xml')
        final xml = new XmlSlurper().parse(serviceComponentsFile).declareNamespace(scr: 'http://www.osgi.org/xmlns/scr/v1.0.0')

        def scrComponents = xml.'scr:component'

        then:
        final simpleService = scrComponents.grep { it.@name == 'testpkg.SimpleService' }[0]
        simpleService.@name == 'testpkg.SimpleService'
        simpleService.@immediate == true
        simpleService.service.provide.collect {
            it.@interface
        } as Set == ['java.lang.Runnable', 'groovy.lang.GroovyObject'] as Set
        simpleService.property.collect { it.@name } as Set ==
            ['testProp', 'testProp2', 'process.label', 'service.pid', 'prop.on.constant'] as Set

        final simpleServlet = scrComponents.grep { it.@name == 'testpkg.SimpleServlet' }[0]
        simpleServlet.@name == 'testpkg.SimpleServlet'
        simpleServlet.service.provide.collect { it.@interface } as Set == ['javax.servlet.Servlet'] as Set
        simpleServlet.property.collect { it.@name } as Set ==
            ['sling.servlet.paths', 'sling.servlet.methods', 'service.description', 'service.pid'] as Set
    }


    def "check metatype components"() {
        when:
        setupProjectOne()
        final metadataFile = file('/scr/proj/build/classes/main/OSGI-INF/metatype/metatype.xml')
        final metadata = new XmlSlurper().parse(metadataFile).declareNamespace(mt: 'http://www.osgi.org/xmlns/metatype/v1.0.0')

        then:
        metadata.OCD.@id == 'testpkg.SimpleService'
    }


    def "Fail test case for reference validation"() {
        when:
        setupProjectTwo()

        then:
        TaskExecutionException exception = thrown()
        "Execution failed for task ':processScrAnnotations'." == exception.message
        InvalidUserDataException cause = exception.cause as InvalidUserDataException
        "\ntestpkg2.SimpleServlet has an @Reference to testpkg2.SimpleService, but it is not an interface." == cause.message
    }


    File file(String filename) {
        final resource = ScrPluginSpec.getResource(filename)
        if (resource != null) {
            return new File(resource.file)
        }
        def f = new File('.')
        final theProjFile = new File(f, "src/test/${filename}").canonicalFile
        if (theProjFile.exists()) {
            return theProjFile
        }
        throw new FileNotFoundException(filename)
    }

}
