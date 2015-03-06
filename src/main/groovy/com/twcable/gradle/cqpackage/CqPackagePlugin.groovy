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

import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import groovy.transform.TypeChecked
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.TaskValidator
import org.gradle.api.tasks.bundling.Zip

import static com.twcable.gradle.GradleUtils.extension

/**
 * <h1>Plugin name</h1>
 * "cqpackage"
 *
 * <h1>Description</h1>
 * Adds tasks for working with CQ Packages via the Sling HTTP interface.
 * <p>
 * The package's configuration is controlled by setting properties on the {@link CqPackageConfiguration}.
 *
 * <h1>Tasks</h1>
 * <table>
 *  <tr><td>createPackage</td><td>Creates the CQ Package zip file. (Depends on the `jar` tasks of the projects that are a part of the `cq_package` configuration.)</td></tr>
 *  <tr><td>install</td><td>Issues the command to CQ to install the uploaded package.</td></tr>
 *  <tr><td>uninstall</td><td>Uninstalls the CQ Package. If the package is not on the server, nothing happens. (i.e., This does not fail if the package is not on the server.)</td></tr>
 *  <tr><td>reinstall</td><td>Reinstalls (removes then uploads and installs a fresh copy) the CQ Package. (Depends on `clean`, `uninstall`, `remove`, `install`, `verifyBundles`)</td></tr>
 *  <tr><td>installPackage</td><td>Installs the CQ Package</td></tr>
 *  <tr><td>validateBundles</td><td>Checks all the JARs that are included in the package to make sure they are installed and in an ACTIVE state and gives a report of any that are not. This task polls in the case the bundles are newly installed.</td></tr>
 *  <tr><td>installRemote</td><td>Issues the command to CQ to install the uploaded package to a remote server defined by -Denvironment, -DenvJson, and -Dpackage.</td></tr>
 *  <tr><td>validateRemoteBundles</td><td>Validates remote bundles in the CQ Package are started correctly</td></tr>
 *  <tr><td>upload</td><td>Upload the package to all the servers defined by the `slingServers` configuration.</td></tr>
 *  <tr><td>uploadRemote</td><td>Upload the package to all the servers defined by -Denvironment, -DenvJson, and -Dpackage.</td></tr>
 *  <tr><td>remove</td><td>Removes the package from CQ. Does not fail if the package was not on the server to begin with.</td></tr>
 *  <tr><td>uninstallBundles</td><td>Downloads the currently installed package .zip file if it exists, compiles list of bundles based off what is currently installed, then stops and uninstalls each bundles individually.  A final `refreshAllBundles` is performed.</td></tr>
 *  <tr><td>installRemoteTask</td><td>Installs a specified CQ Package to a remote environment</td></tr>
 *  <tr><td>verifyBundles</td><td>Checks all the JARs that are included in the package to make sure they are OSGi compliant, and gives a report of any that are not. Never causes the build to fail.</td></tr>
 *  <tr><td>addBundlesToFilterXml</td><td>Adds the bundles to the filter.xml</td></tr>
 *  <tr><td>checkBundleStatus</td><td>Check Bundle Status</td></tr>
 *  <tr><td>startInactiveBundles</td><td>Asynchronously attempts to start any bundle in RESOLVED state.</td></tr>
 * </table>
 *
 * <h1>Artifact Configurations</h1>
 * <table>
 *   <tr><th>name</th><th>description</th></tr>
 *   <tr><td>cq_package</td><td>All the project bundles that should be packaged in the zip</td></tr>
 *   <tr><td>native_cq_package</td><td>All the third-party bundles that should be packaged in the zip</td></tr>
 * </table>
 * <p>
 * "native_cq_package" only applies if the {@link CqPackageConfiguration#nativeStartLevel} and
 * {@link CqPackageConfiguration#dependencyStartLevel} have both been set.
 * <p>
 * <b><u>Note</u>:</b> "native_cq_package" is deprecated and will be rolled into "cq_package" in the future.
 *
 * @see SlingServersConfiguration
 */
@TypeChecked
@SuppressWarnings(["GroovyResultOfAssignmentUsed", "GrMethodMayBeStatic"])
class CqPackagePlugin implements Plugin<Project> {

    @Override
    @SuppressWarnings("GroovyAssignabilityCheck")
    void apply(Project project) {
        project.configurations.create 'cq_package'
        project.configurations.create 'native_cq_package'

        extension(project, CqPackageConfiguration, project)
        extension(project, CqPackageHelper, project)
        extension(project, SlingBundleConfiguration, project)
        extension(project, SlingServersConfiguration)

        addTasks(project)

        // GradleUtils.taskDependencyGraph(project)
    }


    private void addTasks(Project project) {
        addBundlesToFilterXml(project)
        createPackage(project)

        uninstallBundles(project)
        uninstall(project)
        remove(project)
        upload(project)
        uploadRemote(project)
        installPackage(project)
        installRemoteTask(project)

        verifyBundles(project)
        validateBundles(project)

        validateRemoteBundles(project)

        checkBundleStatus(project)
        startInactiveBundles(project)
        install(project)
        installRemote(project)
        reinstall(project)
    }

    // TODO: Should be able to get rid of 'installRemoteTask'
    private void installRemote(Project project) {
        Boolean.valueOf('true')
        project.tasks.create('installRemote').configure { Task task ->
            task.description = "Sequence of tasks to install CQ Package to a remote environment"
            task.group = 'CQ'
            task.dependsOn 'uninstall', 'checkBundleStatus', 'remove', 'installRemoteTask'
        }
    }


    private void startInactiveBundles(Project project) {
        project.tasks.create('startInactiveBundles').configure { Task task ->
            task.description = "Asynchronously attempts to start any bundle in RESOLVED state."
            task.group = 'CQ'
            task.doLast {
                getCqPackageHelper(project).startInactiveBundles()
            }
        }
    }


    private void verifyBundles(Project project) {
        project.tasks.create('verifyBundles').configure { Task task ->
            task.description = "Checks all the JARs that are included in the package to make sure they are " +
                "installed and in an ACTIVE state and gives a report of any that are not. This task polls in the " +
                "case the bundles are newly installed."
            task.group = 'CQ'
            final packageDeps = cqPackageDependencies(project)
            task.doLast {
                getCqPackageHelper(project).verifyOsgiArtifacts(packageDeps)
            }
            task.dependsOn packageDeps
        }
    }


    private void installRemoteTask(Project project) {
        project.tasks.create('installRemoteTask').configure { DefaultTask task ->
            task.description = "Installs a specified CQ Package to a remote environment"
            task.group = 'CQ'
            task.dependsOn 'uploadRemote'
            task.doLast {
                getCqPackageHelper(project).installPackage()
            }

            task.addValidator({ TaskInternal t, Collection<String> messages ->
                if (!System.hasProperty('package')) {
                    messages << "No package path passed in to remotely install. Use -Dpackage=<filepath>"
                }
            } as TaskValidator)
        }
    }


    private void installPackage(Project project) {
        project.tasks.create('installPackage').configure { Task task ->
            task.description = "Installs the CQ Package"
            task.group = 'CQ'
            task.doLast {
                getCqPackageHelper(project).installPackage()
            }
            task.mustRunAfter 'upload'
        }
    }


    Task uploadRemote(Project project) {
        Task task = project.task([
            description: "Uploads the CQ Package to a remote environment",
            group      : 'CQ',
        ], 'uploadRemote')

        task.doLast {
            getCqPackageHelper(project).uploadPackage()
        }

        task.dependsOn 'createPackage'
        task.mustRunAfter 'remove'

        return task
    }


    Task upload(Project project) {
        Task task = project.task([description: "Uploads the CQ Package", group: 'CQ'], 'upload') << {
            getCqPackageHelper(project).uploadPackage()
        }

        task.dependsOn 'createPackage'
        task.mustRunAfter 'remove'

        return task
    }


    Task validateRemoteBundles(Project project) {
        Task task = project.task([
            description: "Validates remote bundles in the CQ Package are started correctly",
            group      : 'CQ'
        ], 'validateRemoteBundles')

        task.doLast {
            getCqPackageHelper(project).validateRemoteBundles()
        }

        return task
    }


    Task checkBundleStatus(Project project) {
        Task task = project.task([
            description: "Check Bundle Status",
            group      : 'CQ'
        ], 'checkBundleStatus')

        task.doLast {
            if (project.hasProperty('group')) {
                String groupProperty = project.property('group')
                getCqPackageHelper(project).checkActiveBundles(groupProperty)
            }
            else {
                project.logger.error "Group property is not defined on the project"
            }
        }

        project.gradle.taskGraph.whenReady {
            if (hasProperty(project, 'skipStatusCheck')) {
                task.setEnabled(false)
            }
        }

        return task
    }


    public Task reinstall(Project project) {
        Task reinstallTask = project.task([
            description: "Reinstalls (removes then uploads and installs a fresh copy) the CQ Package",
            group      : 'CQ'
        ], 'reinstall')

        reinstallTask.dependsOn 'clean', 'uninstall', 'remove', 'install', 'verifyBundles'

        return reinstallTask
    }


    Task install(Project project) {
        Task installTask = project.task([
            description: "Sequence of tasks to install CQ Package to localhost",
            group      : 'CQ'
        ], 'install')

        installTask.dependsOn 'uninstall', 'remove', 'upload', 'installPackage', 'checkBundleStatus'

        return installTask
    }


    Task remove(Project project) {
        Task removeTask = project.task([
            description: "Removes the package from CQ. Does not fail if the package was not on" +
                "the server to begin with.",
            group      : 'CQ'
        ], 'remove')

        removeTask.doLast {
            getCqPackageHelper(project).deletePackage()
        }

        removeTask.mustRunAfter 'checkBundleStatus', 'uninstall'

        return removeTask
    }


    Task uninstall(Project project) {
        Task uninstallTask = project.task([
            description: "Uninstalls the CQ Package. If the package is not on the server, nothing happens. " +
                "(i.e., This does not fail if the package is not on the server.)",
            group      : 'CQ',
            dependsOn  : 'uninstallBundles'
        ], 'uninstall')

        uninstallTask.doLast {
            getCqPackageHelper(project).uninstallPackage()
        }

        uninstallTask.mustRunAfter('createPackage')

        return uninstallTask
    }


    static CqPackageHelper getCqPackageHelper(Project project) {
        return (CqPackageHelper)extension(project, CqPackageHelper)
    }


    Task uninstallBundles(Project project) {
        def task = project.task([
            description: "Downloads the currently installed package .zip file if it exists, compiles list of " +
                "bundles based off what is currently installed, then stops and uninstalls each bundles individually. " +
                "A final refreshAllBundles is performed.",
            group      : 'CQ'
        ], 'uninstallBundles')

        task.doLast {
            def serversConfiguration = project.extensions.getByType(SlingServersConfiguration)

            // if the uninstallBundlesPredicate was not set, default to uninstalling bundles where the symbolic
            // name match this project's group name
            if (serversConfiguration.uninstallBundlesPredicate == null) {
                serversConfiguration.uninstallBundlesPredicate = { String symbolicName ->
                    symbolicName.contains(project.group as CharSequence)
                }
            }

            getCqPackageHelper(project).uninstallBundles()
        }
        return task
    }


    Task addBundlesToFilterXml(Project project) {
        def task = project.tasks.create('addBundlesToFilterXml', AddBundlesToFilterXmlTask)
        task.description = "Adds the bundles to the filter.xml"
        task.group = 'CQ'
        task.dependsOn cqPackageDependencies(project)
        return task
    }


    Task validateBundles(Project project) {
        def task = project.tasks.create('validateBundles')
        task.description = "Checks all the JARs that are included in the package to make sure they are " +
            "installed and in an ACTIVE state and gives a report of any that are not. This task polls in the " +
            "case the bundles are newly installed."
        task.group = 'CQ'
        task.doLast {
            getCqPackageHelper(project).
                validateBundles(cqPackageDependencies(project))
        }
        task.dependsOn cqPackageDependencies(project)
    }


    Zip createPackage(Project project) {
        Zip task = project.task([
            type       : Zip,
            description: "Creates the CQ Package zip file",
            group      : 'CQ',
        ], 'createPackage') as Zip
        task.dependsOn 'addBundlesToFilterXml', 'verifyBundles'

        task.with {
            inputs.dir project.file('/src/main/content') // TODO should be available from configuration...
            inputs.file cqPackageDependencies(project)

            // don't know why this isn't being set automatically, but...
            baseName = project.name
            version = project.version

            from 'src/main/content'

            // TODO should be available from configuration...
            exclude '**/.git'
            exclude '**/.git/**'
            exclude '**/.gitattributes'
            exclude '**/.gitignore'
            exclude '**/.gitmodules'
            exclude '**/.vlt'
            exclude 'jcr_root/.vlt-sync-config.properties'
            exclude 'jcr_root/var/**'
            exclude 'META-INF/vault/definition/.content.xml'
            exclude 'META-INF/vault/filter.xml'
            exclude 'SLING-INF/**'

            addBundles(project, task)

            into('META-INF/vault/definition') {
                from project.file('src/main/content/META-INF/vault/definition/.content.xml')
                expand(project.properties)
            }

            into('META-INF/vault') {
                from project.file("${project.buildDir}/tmp/filter.xml")
            }
        }

        task.dependsOn cqPackageDependencies(project)

        return task
    }


    public static Configuration cqPackageDependencies(Project project) {
        return project.configurations.getByName('cq_package')
    }


    void addBundles(Project project, Zip packageTask) {
        // If these properties are present we can split the install into start levels
        // But give the project a chance to initialize first so the properties can be set

        project.afterEvaluate {
            def packageConfiguration = project.extensions.findByType(CqPackageConfiguration)

            def bundleInstallRoot = packageConfiguration.bundleInstallRoot
            if (!bundleInstallRoot) throw new GradleException("Can not have an empty bundleInstallRoot")

            def nativeStartLevel = packageConfiguration.nativeStartLevel
            def dependencyStartLevel = packageConfiguration.dependencyStartLevel

            if (nativeStartLevel && dependencyStartLevel) {
                addBundlesWithStartLevels(project, packageTask, bundleInstallRoot, nativeStartLevel, dependencyStartLevel)
            }
            else {
                addBundlesWithNoStartLevels(packageTask, bundleInstallRoot, project)
            }
        }
    }


    void addBundlesWithNoStartLevels(Zip packageTask, String bundleInstallRoot, Project project) {
        packageTask.into("jcr_root${bundleInstallRoot}") { CopySpec spec ->
            Configuration packageDeps = cqPackageDependencies(project)
            packageDeps.with { // TODO: should come from configuration
                exclude group: 'com.day.cq'
                exclude group: 'com.day.cq.wcm'
                exclude group: 'com.adobe.aem'
                exclude group: 'commons-codec'
                exclude group: 'commons-io'
                exclude group: 'commons-lang3'
                exclude group: 'javax.jcr'
                exclude group: 'javax.servlet.jsp'
                exclude group: 'org.apache.felix', module: 'org.osgi.compendium'
                exclude group: 'org.apache.felix', module: 'org.osgi.core'
                exclude group: 'org.apache.sling'
                exclude group: 'org.slf4j'
            }

            spec.from packageDeps
        }
    }


    void addBundlesWithStartLevels(Project project, Zip packageTask, String bundleInstallRoot,
                                   String nativeStartLevel, String dependencyStartLevel) {
        Configuration native_cq_package = project.configurations.getByName('native_cq_package')

        packageTask.into("jcr_root${bundleInstallRoot}/${nativeStartLevel}") { CopySpec spec ->
            spec.from native_cq_package
        }

        packageTask.into("jcr_root${bundleInstallRoot}/${dependencyStartLevel}") { CopySpec spec ->
            Configuration packageDeps = cqPackageDependencies(project)
            packageDeps.with { // TODO: this really should come from config
                exclude group: 'com.day.cq'
                exclude group: 'com.day.cq.wcm'
                exclude group: 'com.adobe.aem'
                exclude group: 'commons-codec'
                exclude group: 'commons-io'
                exclude group: 'commons-lang3'
                exclude group: 'javax.jcr'
                exclude group: 'javax.servlet.jsp'
                exclude group: 'org.apache.felix', module: 'org.osgi.compendium'
                exclude group: 'org.apache.felix', module: 'org.osgi.core'
                exclude group: 'org.apache.sling'
                exclude group: 'org.slf4j'
            }

            spec.from packageDeps - native_cq_package
        }
    }


    static boolean hasProperty(Project project, String propName) {
        return project.extensions.findByName(propName) != null
    }

}
