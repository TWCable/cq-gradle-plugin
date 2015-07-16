package com.twcable.gradle.cqpackage

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.testfixtures.ProjectBuilder

final class CqPackageTestUtils {

    static Project createCqPackageProject() {
        return createCqPackageProject("2.3.4", "/apps/install")
    }


    static Project createCqPackageProject(String version, String bundleRoot) {
        Project project = ProjectBuilder.builder().build()
        project.logging.level = LogLevel.DEBUG
        project.version = version
        project.apply plugin: 'java'
        project.apply plugin: 'cqpackage'

        CqPackageConfiguration cqPackageConfiguration = project.extensions.findByType(CqPackageConfiguration)
        cqPackageConfiguration.dependencyStartLevel = '20'
        cqPackageConfiguration.nativeStartLevel = '30'
        cqPackageConfiguration.bundleInstallRoot = bundleRoot

        return project
    }


    static Project createSubProject(Project parentProject, String projName, boolean makeOsgi) {
        Project project = ProjectBuilder.builder().withName(projName).withParent(parentProject).build()
        project.version = parentProject.version
        project.apply plugin: 'java'
        if (makeOsgi) project.apply plugin: 'osgi'
        return project
    }


    @SuppressWarnings("GroovyAssignabilityCheck")
    static String xml(Closure closure) {
        def builder = new StreamingMarkupBuilder()
        return XmlUtil.serialize(builder.bind(closure))
    }


    static String xml(String xml) {
        XmlUtil.serialize(xml)
    }


    static def addProjectToNativeCqPackage(Project project, Project projectDep) {
        def dependencyHandler = project.dependencies
        def native_cq_package = project.configurations.getByName('native_cq_package')
        native_cq_package.dependencies.add(dependencyHandler.project([path: projectDep.path, configuration: 'archives']))
    }


    public static File contentDir(Project project) {
        def projectDir = project.projectDir
        def contentDir = new File(new File(new File(projectDir.canonicalFile, "src"), "main"), "content")
        if (!contentDir.exists()) {
            assert contentDir.mkdirs()
        }
        return contentDir
    }

}
