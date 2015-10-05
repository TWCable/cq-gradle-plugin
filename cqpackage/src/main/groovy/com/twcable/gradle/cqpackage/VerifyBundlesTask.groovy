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
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import static com.twcable.gradle.cqpackage.CqPackageHelper.isOsgiFile

@CompileStatic
class VerifyBundlesTask extends DefaultTask {

    Collection<File> nonOsgiFiles() {
        def createProjectTask = CreatePackageTask.from(project)
        def bundleFiles = createProjectTask.bundleFiles
        return bundleFiles.findAll { !isOsgiFile(it) }
    }


    @TaskAction
    @SuppressWarnings("GroovyUnusedDeclaration")
    void verify() {
        for (File file : nonOsgiFiles()) {
            logger.lifecycle "\n${file.name} is not an OSGi file"
        }
    }

}
