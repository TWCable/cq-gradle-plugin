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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.TypeChecked
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import static BundleState.ACTIVE
import static com.twcable.gradle.GradleUtils.extension

/**
 * <h1>Plugin name</h1>
 * "sling-bundle"
 *
 * <h1>Description</h1>
 * Adds tasks for working with the OSGi bundle created by this Project's 'jar' task via the Sling HTTP interface.
 * <p>
 * The bundle's configuration is controlled by setting properties on the {@link SlingBundleConfiguration}.
 *
 * <h1>Tasks</h1>
 * <table>
 *   <tr><th>name</th><th>description</th></tr>
 *   <tr><td>uploadBundle</td><td>Upload the bundle to the servers</td></tr>
 *   <tr><td>startBundle</td><td>Start the bundle on the servers</td></tr>
 *   <tr><td>stopBundle</td><td>Stop the bundle on the servers</td></tr>
 *   <tr><td>deleteBundle</td><td>Deletes the bundle from the servers</td></tr>
 *   <tr><td>showBundle</td><td>Output the JSON for the bundle's status</td></tr>
 *   <tr><td>refreshAllBundles</td><td>Refresh the dependencies for every bundle running on the servers</td></tr>
 * </table>
 *
 * @see SlingBundleConfiguration
 * @see SlingServersConfiguration
 */
@TypeChecked
@SuppressWarnings("GrMethodMayBeStatic")
class SlingBundlePlugin implements Plugin<Project> {

    @SuppressWarnings("GroovyUnusedAssignment")
    void apply(Project project) {
        def existingTasks = project.tasks.asList() as Set

        uploadBundle(project)
        startBundle(project)
        stopBundle(project)
        deleteBundle(project)
        showBundle(project)
        Task refreshAllBundlesTask = refreshAllBundles(project)

        com.twcable.gradle.GradleUtils.extension(project, SlingServersConfiguration)

        def osgiBundle = com.twcable.gradle.GradleUtils.extension(project, SlingBundleConfiguration, project)
        osgiBundle.sourceFile // "prime" the "jar" task

        // apply configuration that applies to all these tasks
        project.tasks.withType(BasicBundleTask) { BasicBundleTask task ->
            task.group = 'OSGi'
            task.osgiBundle = osgiBundle
            task.slingBundle = new SlingOsgiBundle(osgiBundle)
        }

        // if the root project does not already have the "refreshAllBundles" task, attach it
        if (project.rootProject.tasks.findByName('refreshAllBundles') == null) {
            project.rootProject.tasks.add(refreshAllBundlesTask)
        }

        def allTasks = project.tasks.asList() as Set
        def newTasks = (allTasks - existingTasks) as Set<Task>

        //GradleUtils.taskDependencyGraph(project, newTasks)
    }


    static boolean doAcrossServers(Project project, Closure<HttpResponse> closure) {
        def serversConfiguration = project.extensions.getByType(SlingServersConfiguration)
        final post = SlingOsgiBundle.doAcrossServers(serversConfiguration) { httpClient, configuration ->
            closure(httpClient, configuration)
        }

        return SlingOsgiBundle.bundleConfigStateIs(post, ACTIVE)
    }


    private Task refreshAllBundles(Project project) {
        return project.tasks.create('refreshAllBundles', BasicBundleTask).with {
            description = 'Refreshes all the bundles in the Sling server'
            doLast {
                def bundle = slingBundle
                osgiBundle.slingServers.each { SlingServerConfiguration slingServerConfiguration ->
                    def slingSupport = slingServerConfiguration.slingSupport
                    slingSupport.doHttp { httpClient ->
                        bundle.refreshAllPackages(httpClient, slingSupport, slingServerConfiguration.bundleControlUriJson)
                    }
                }
            }
        }
    }


    private Task showBundle(Project project) {
        return project.tasks.create('showBundle', BasicBundleTask).with {
            description = 'Shows the bundle information in the authoring Sling server to STDOUT'
            doLast {
                println((String)slingBundle.slingBundleInformationOnAuthor)
            }
        }
    }


    private Task deleteBundle(Project project) {
        return project.tasks.create('deleteBundle', BasicBundleTask).with {
            description = 'Deletes the bundle in the Sling server'
            dependsOn 'stopBundle'
            doLast {
                doAcrossServers(project) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
                    slingBundle.removeBundle(httpClient, serverConfiguration.slingSupport, slingBundle.getBundleLocation(httpClient, serverConfiguration.baseUri, serverConfiguration.installPath, serverConfiguration.name, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri))
                }
            }
        }
    }


    private Task stopBundle(Project project) {
        return project.tasks.create('stopBundle', BasicBundleTask).with {
            description = 'Stops bundle on the Sling server'
            doLast {
                doAcrossServers(project) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
                    slingBundle.stopBundle(httpClient, serverConfiguration)
                }
            }
        }
    }


    private Task startBundle(Project project) {
        return project.tasks.create('startBundle', BasicBundleTask).with {
            description = 'Starts the bundle in the Sling server'
            doLast {
                doAcrossServers(project) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
                    slingBundle.startBundle(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)
                }
            }
        }
    }


    private Task uploadBundle(Project project) {
        return project.tasks.create('uploadBundle', BasicBundleTask).with {
            description = 'Upload the bundle to the Sling server'
            dependsOn 'jar'
            doLast {
                doAcrossServers(project) { SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration ->
                    slingBundle.uploadBundle(httpClient, serverConfiguration)
                }
            }
        }
    }

}
