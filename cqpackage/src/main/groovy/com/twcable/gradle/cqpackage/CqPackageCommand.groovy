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
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.Status.OK
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT
import static com.twcable.gradle.cqpackage.Status.UNKNOWN
import static com.twcable.gradle.cqpackage.SuccessOrFailure.success
import static com.twcable.gradle.sling.SlingSupport.block
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK
import static java.util.Collections.EMPTY_MAP

@Slf4j
@CompileStatic
@SuppressWarnings("GrMethodMayBeStatic")
class CqPackageCommand {

    /**
     * Send a command to the server.
     *
     * @param commandName the command to send
     * @param packageName the package to act on
     * @param serverConfig the configuration of the server to talk to
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the command to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     * @param successFalseHandler if the JSON returned has "success: false" then "successFalseHandler" will be
     *                            invoked to try to recover; if successFalseHandler returns UNKNOWN then
     *                            a GradleException is thrown
     *
     * @return the Status of invoking the command
     */
    @Nonnull
    static Status doCommand(String commandName,
                            String packageName,
                            SlingServerConfiguration serverConfig,
                            long maxWaitMs,
                            long retryWaitMs,
                            SuccessFalseHandler successFalseHandler) {
        return doCommand(commandName, packageName, serverConfig, maxWaitMs, retryWaitMs, EMPTY_MAP, successFalseHandler)
    }

    /**
     * Send a command to the server.
     *
     * @param commandName the command to send
     * @param packageName the package to act on
     * @param serverConfig the configuration of the server to talk to
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the command to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     * @param postParams the fields to pass in the POST
     * @param successFalseHandler if the JSON returned has "success: false" then "successFalseHandler" will be
     *                            invoked to try to recover; if successFalseHandler returns UNKNOWN then
     *                            a GradleException is thrown
     *
     * @return the Status of invoking the command
     */
    @Nonnull
    static Status doCommand(String commandName,
                            String packageName,
                            SlingServerConfiguration serverConfig,
                            long maxWaitMs,
                            long retryWaitMs,
                            Map postParams,
                            SuccessFalseHandler successFalseHandler) {
        if (!serverConfig.active) throw new IllegalArgumentException("The server configuration for ${serverConfig.name} is not active")

        final packageUriSF = packageURI(commandName, packageName, serverConfig, maxWaitMs, retryWaitMs)
        if (packageUriSF.failed()) return packageUriSF.error
        final URI uri = packageUriSF.value

        final resp = blockAndPost(serverConfig, uri, maxWaitMs, retryWaitMs, postParams)

        if (resp.code == HTTP_OK) {
            return handleHttpOk(commandName, packageName, resp, serverConfig, successFalseHandler)
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            log.error(resp.body)
            serverConfig.active = false
            return Status.SERVER_TIMEOUT
        }
        else {
            throw new GradleException("Could not ${commandName} '${packageName}': ${resp.code} - ${resp.body}")
        }
    }


    static SuccessOrFailure<URI> packageURI(String commandName,
                                            String packageName,
                                            SlingServerConfiguration serverConfig,
                                            long maxWaitMs,
                                            long retryWaitMs) {
        if (commandName != "upload") {
            final packageInfoSF = RuntimePackageProperties.packageProperties(serverConfig, maxWaitMs, retryWaitMs, packageName)
            if (packageInfoSF.failed()) return SuccessOrFailure.failure(packageInfoSF.error)
            def packageInfo = packageInfoSF.value

            return success(URI.create("${serverConfig.packageControlUri}${packageInfo.path}?cmd=${commandName}"))
        }
        else {
            return success(URI.create("${serverConfig.packageControlUri}?cmd=${commandName}"))
        }
    }


    private static Status handleHttpOk(String commandName,
                                       String packageName,
                                       HttpResponse resp,
                                       SlingServerConfiguration serverConfig,
                                       SuccessFalseHandler successFalseHandler) {
        final jsonSlurper = new JsonSlurper()
        final json = jsonSlurper.parseText(resp.body) as Map
        if (json.success == true) {
            log.info("'${packageName}': ${json.msg}")
        }
        else {
            final status = successFalseHandler.handleFailure(serverConfig, (String)json.msg)
            if (status == Status.UNKNOWN) {
                throw new GradleException("Could not ${commandName} '${packageName}': ${((String)json.msg)}")
            }
            return status
        }
        return Status.OK
    }


    private static HttpResponse blockAndPost(SlingServerConfiguration serverConfig,
                                             URI uri, long maxWaitMs, long retryWaitMs,
                                             Map postParams) {
        HttpResponse resp
        com.twcable.gradle.sling.SlingSupport.block(
            maxWaitMs,
            { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) },
            { resp = doPost(serverConfig, uri, postParams) },
            retryWaitMs
        )
        return resp
    }

    /**
     * POST to the URI with the given fields
     */
    static HttpResponse doPost(SlingServerConfiguration serverConfig, URI uri, Map postParams) {
        final post = serverConfig.slingSupport.doHttp { SimpleHttpClient httpClient ->
            serverConfig.slingSupport.doPost(uri, postParams != null ? postParams : EMPTY_MAP, httpClient)
        }
        return post
    }

    /**
     * @see {@link SuccessFalseHandler#handleFailure(SlingServerConfiguration, String)}
     */
    interface SuccessFalseHandler {
        /**
         * If the JSON response from the server does not have a field where "success" is true, then
         * invoke this handler to try to deal with it.
         *
         * @param slingServerConfiguration
         * @param jsonMsg
         */
        Status handleFailure(SlingServerConfiguration slingServerConfiguration, String jsonMsg)
    }

}
