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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import static com.twcable.gradle.cqpackage.CqPackageUtils.nonProjectDependencyBundleFiles
import static com.twcable.gradle.cqpackage.CqPackageUtils.allProjectBundleJarFiles

@Slf4j
@PackageScope
@CompileStatic
class FilterDefinition {
    final Collection<String> jarNames


    private FilterDefinition(Collection<String> jarNames) {
        this.jarNames = jarNames
    }


    static FilterDefinition create(Collection<File> bundleFiles) {
        return new FilterDefinition(bundleFiles.collect { it.name })
    }


    static FilterDefinition createAllBundles(Project project, Configuration configuration) {
        Collection<String> allBundleFileNames = allBundleFileNames(project, configuration)

        return new FilterDefinition(allBundleFileNames)
    }


    static FilterDefinition createProjectBundles(Project project, Configuration configuration) {
        Collection<String> allBundleFileNames = projectBundleNames(project, configuration)

        return new FilterDefinition(allBundleFileNames)
    }


    static FilterDefinition createNonProjectBundles(Configuration configuration) {
        Collection<String> allBundleFileNames = nonProjectDependencyBundleFileNames(configuration)

        return new FilterDefinition(allBundleFileNames)
    }


    static FilterDefinition createNoBundles() {
        return new FilterDefinition(Collections.EMPTY_LIST)
    }


    private static Collection<String> allBundleFileNames(Project project, Configuration configuration) {
        def allBundleNames = projectBundleNames(project, configuration) + nonProjectDependencyBundleFileNames(configuration)

        log.info "All Bundles: ${allBundleNames.sort()}"

        return allBundleNames
    }


    private static Collection<String> projectBundleNames(Project project, Configuration configuration) {
        def allProjectJarNames = allProjectBundleJarFiles(project, configuration).
            collect { it.name }

        log.info "Project Bundles: ${allProjectJarNames.sort()}"

        return allProjectJarNames
    }


    private static Collection<String> nonProjectDependencyBundleFileNames(Configuration configuration) {
        def nonProjDepFilenames = nonProjectDependencyBundleFiles(configuration).
            collect { it.name }

        log.info "Non-project Bundles: ${nonProjDepFilenames.sort()}"

        return nonProjDepFilenames
    }

}
