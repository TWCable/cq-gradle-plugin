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

import groovy.transform.TypeChecked
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.tasks.bundling.Zip

import javax.annotation.Nonnull

/**
 * Convention settings to describe a CQ Package.
 *
 * Follows to patterns used for a Gradle "named domain object".
 */
@Slf4j
@TypeChecked
class CqPackageConfiguration {
    public static final String NAME = 'cqPackage'

    final Project project
    String packageName
    private String _bundleInstallRoot = '/apps/install'
    String nativeStartLevel
    String dependencyStartLevel
    Collection<ExcludeRule> packageExclusions


    CqPackageConfiguration(Project project) {
        this.project = project
        packageName = project.name

        packageExclusions = [
            exclude([group: 'com.day.cq']),
            exclude([group: 'com.day.cq.wcm']),
            exclude([group: 'commons-codec']),
            exclude([group: 'commons-io']),
            exclude([group: 'commons-lang3']),
            exclude([group: 'javax.jcr']),
            exclude([group: 'javax.servlet.jsp']),
            exclude([group: 'org.apache.felix', module: 'org.osgi.compendium']),
            exclude([group: 'org.apache.felix', module: 'org.osgi.core']),
            exclude([group: 'org.apache.sling']),
            exclude([group: 'org.slf4j']),
        ]
    }


    private static ExcludeRule exclude(Map exclusionMap) {
        return new DefaultExcludeRule((String)exclusionMap['group'], (String)exclusionMap['module'])
    }

    /**
     * Root location that jar bundles should be installed to.
     * Should include a prefixing / but not a trailing one.
     */
    String getBundleInstallRoot() {
        return _bundleInstallRoot
    }

    /**
     * Root location that jar bundles should be installed to.
     */
    void setBundleInstallRoot(String path) {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("path == null or empty")

        if (path[0] != '/')
            path = "/${path}"

        _bundleInstallRoot = (path.size() > 1 && path[-1] == '/') ?
            path[0..-2] : path
    }


    Project getProject() {
        return project
    }


    String getPackageName() {
        return packageName
    }


    void setPackageName(String packageName) {
        this.packageName = packageName
    }

    /**
     * Returns the CQ Package file to use.
     *
     * If a System Property of "package" is set, that is used. Otherwise the output of
     * the 'createPackage' task is used.
     */
    @Nonnull
    File getPackageFile() {
        def packageProperty = System.getProperty('package')
        if (packageProperty != null) {
            return new File(packageProperty)
        }
        log.info("No remote package passed in, using createPackage zip")
        return ((Zip)project.tasks.getByName('createPackage')).archivePath
    }


    String getNativeStartLevel() {
        return nativeStartLevel
    }


    void setNativeStartLevel(String nativeStartLevel) {
        this.nativeStartLevel = nativeStartLevel
    }


    String getDependencyStartLevel() {
        return dependencyStartLevel
    }


    void setDependencyStartLevel(String dependencyStartLevel) {
        this.dependencyStartLevel = dependencyStartLevel
    }

}
