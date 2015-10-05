package com.twcable.gradle.cqpackage

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingSupport
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT
import static com.twcable.gradle.cqpackage.SuccessOrFailure.failure
import static com.twcable.gradle.cqpackage.SuccessOrFailure.success
import static com.twcable.gradle.sling.SlingSupport.block
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK

@Slf4j
@CompileStatic
class ListPackages {

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     */
    @Nonnull
    static SuccessOrFailure<Collection<RuntimePackageProperties>> listPackages(SlingServerConfiguration serverConfig,
                                                                               long maxWaitMs, long retryWaitMs) {
        if (!serverConfig.active) return failure(Status.SERVER_INACTIVE)

        return listPackages(serverConfig.packageListUri, serverConfig.slingSupport, maxWaitMs, retryWaitMs)
    }

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     */
    @Nonnull
    static SuccessOrFailure<Collection<RuntimePackageProperties>> listPackages(URI packageListUri, SlingSupport slingSupport,
                                                                               long maxWaitMs, long retryWaitMs) {
        HttpResponse resp
        com.twcable.gradle.sling.SlingSupport.block(
            maxWaitMs,
            { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) },
            {
                resp = slingSupport.doHttp { SimpleHttpClient httpClient ->
                    slingSupport.doGet(packageListUri, httpClient)
                }
            },
            retryWaitMs
        )

        if (resp.code == HTTP_OK) {
            final String jsonStr = resp.body
            final JsonSlurper jsonSlurper = new JsonSlurper()
            return success(((Collection<Map>)(jsonSlurper.parseText(jsonStr) as Map).results).collect {
                RuntimePackageProperties.fromJson(it)
            })
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            return failure(Status.SERVER_TIMEOUT)
        }
        else {
            throw new GradleException("Could not list the packages on ${packageListUri}: ${resp.code} - ${resp.body}")
        }
    }

}
