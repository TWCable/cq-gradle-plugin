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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Nonnull

import static com.twcable.gradle.sling.SlingSupport.block
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK

@TypeChecked
@SuppressWarnings("GrMethodMayBeStatic")
class CqPackageCommand implements Runnable {
    Logger logger = LoggerFactory.getLogger(CqPackageCommand)
    final CqPackageHelper cqPackageHelper

    @SuppressWarnings("SpellCheckingInspection")
    final JsonSlurper jsonSlurper = new JsonSlurper()

    final String command
    final Closure handleSuccessFalse
    final boolean missingPackageOk


    CqPackageCommand(@Nonnull String commandName,
                     @Nonnull CqPackageHelper cqPackageHelper,
                     @DelegatesTo(CqPackageCommand) @ClosureParams(value = SimpleType, options = ["SlingServerConfiguration", "java.lang.String"]) Closure<Boolean> handleSuccessFalse,
                     boolean missingPackageOk) {

        this.command = commandName
        this.cqPackageHelper = cqPackageHelper
        this.handleSuccessFalse = handleSuccessFalse
        if (handleSuccessFalse != null) {
            handleSuccessFalse.delegate = this
        }
        this.missingPackageOk = missingPackageOk
    }


    @Override
    void run() {
        slingServersConfiguration.each { serverConfig ->
            commandPackage(serverConfig)
        }
    }


    Project getProject() {
        return cqPackageHelper.project
    }


    SlingServersConfiguration getSlingServersConfiguration() {
        return project.extensions.getByType(SlingServersConfiguration)
    }

    /**
     * Returns the name of the project, which is used as the name of the package.
     */
    String getPackageName() {
        project.name
    }


    long getRetryWaitMs() {
        return slingServersConfiguration.retryWaitMs
    }


    long getMaxWaitMs() {
        return slingServersConfiguration.maxWaitValidateBundlesMs
    }


    @SuppressWarnings(["GroovyMultipleReturnPointsPerMethod", "GroovyOverlyLongMethod"])
    void commandPackage(@Nonnull SlingServerConfiguration serverConfig) {
        if (!serverConfig.active) return

        Map packageInfo = cqPackageHelper.getPackageInfo(serverConfig)
        if (packageInfo == null) {
            if (!serverConfig.active) return

            if (missingPackageOk) {
                logger.info("${packageName} is not currently on the server, so can not ${command}")
                return
            }
            else {
                throw new GradleException("${packageName} is not currently on the server, so can not ${command}")
            }
        }

        final uri = URI.create("${serverConfig.packageControlUri}${packageInfo.path}?cmd=${command}")

        HttpResponse resp
        block(maxWaitMs, { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) }, {
            resp = doPost(serverConfig, uri)
        } as Closure<Void>, retryWaitMs)

        if (resp.code == HTTP_OK) {
            handleHttpOk(resp, serverConfig)
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            logger.error(resp.body)
            serverConfig.active = false
        }
        else {
            throw new GradleException("Could not ${command} '${packageName}': ${resp.code} - ${resp.body}")
        }
    }


    private void handleHttpOk(HttpResponse post, SlingServerConfiguration serverConfig) {
        final Map json = jsonSlurper.parseText(post.body) as Map
        if (json.success == true) {
            logger.info("'${packageName}': ${json.msg}")
        }
        else {
            internalHandleSuccessFalse(serverConfig, (String)json.msg)
        }
    }


    private HttpResponse doPost(SlingServerConfiguration serverConfig, URI uri) {
        final post = serverConfig.slingSupport.doHttp { SimpleHttpClient httpClient ->
            serverConfig.slingSupport.doPost(uri, [:], httpClient)
        }
        return post
    }


    void internalHandleSuccessFalse(@Nonnull SlingServerConfiguration serverConfig, @Nonnull String jsonMsg) {
        if (handleSuccessFalse != null) {
            if (handleSuccessFalse.call(serverConfig, jsonMsg)) {
                return
            }
        }
        throw new GradleException("Could not ${command} '${packageName}': ${jsonMsg}")
    }

}
