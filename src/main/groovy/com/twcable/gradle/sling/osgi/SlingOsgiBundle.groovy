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
import com.twcable.gradle.sling.SlingSupport
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.apache.http.entity.mime.content.FileBody
import org.gradle.api.GradleException

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static BundleState.ACTIVE
import static BundleState.FRAGMENT
import static BundleState.INSTALLED
import static BundleState.MISSING
import static BundleState.RESOLVED
import static SlingSupport.block
import static java.net.HttpURLConnection.HTTP_BAD_METHOD
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK

/**
 * Provides an abstraction around an OSGi bundle for interaction via the Sling REST API.
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyPointlessBoolean", "GrMethodMayBeStatic"])
class SlingOsgiBundle {
    //
    // Many of the commands for interacting with Sling/Felix come from the documentation at
    // http://felix.apache.org/site/web-console-restful-api.html
    //

    final SlingBundleConfiguration slingBundleConfig
    private final JsonSlurper jsonSlurper = new JsonSlurper()
    private Map<String, URI> serverConfigToBundleLocation = [:]


    SlingOsgiBundle(SlingBundleConfiguration slingBundleConfig) {
        this.slingBundleConfig = slingBundleConfig
    }

    /**
     * How many milliseconds is the server configured to wait between retries?
     * @see SlingServersConfiguration#retryWaitMs
     */
    long getRetryWaitMs() {
        return slingBundleConfig.slingServers.retryWaitMs
    }

    /**
     * What is the max time the server configured to wait before giving up?
     * @see SlingServersConfiguration#maxWaitValidateBundlesMs
     */
    long getMaxWaitMs() {
        return slingBundleConfig.slingServers.maxWaitValidateBundlesMs
    }


    @Nonnull
    HttpResponse stopBundle(@Nonnull SimpleHttpClient httpClient, @Nonnull SlingServerConfiguration configuration) {
        final url = slingBundleConfig.getBundleControlUrl(null, configuration.bundleControlBaseUri, configuration.slingSupport)
        configuration.slingSupport.doPost(url, ['action': 'stop'], httpClient)
    }


    @Nonnull
    HttpResponse startBundle(@Nonnull SimpleHttpClient httpClient,
                             @Nonnull SlingSupport slingSupport,
                             @Nonnull URI bundleControlBaseUri) {
        final url = slingBundleConfig.getBundleControlUrl(null, bundleControlBaseUri, slingSupport)
        slingSupport.doPost(url, ['action': 'start'], httpClient)
    }


    @Nonnull
    @TypeChecked(TypeCheckingMode.SKIP)
    void startInactiveBundles(@Nonnull SimpleHttpClient httpClient, @Nonnull SlingServerConfiguration configuration) {
        def resp = configuration.slingSupport.doGet(configuration.bundleControlUriJson, httpClient)

        if (resp.code == HTTP_OK) {
            Map json = new JsonSlurper().parseText(resp.body) as Map
            List<Map> data = json.data as List

            def inactiveTwcBundles = data.findAll {
                it.state == RESOLVED.stateString
            }.collect { [it.id, it.symbolicName] }

            inactiveTwcBundles.each { id, symbolicName ->
                URI url = new URI("${configuration.bundleControlBaseUri}/${id}.json")

                configuration.slingSupport.doPost(url, ['action': 'start'], httpClient)
                log.info "Trying to start inactive bundle: ${id}:${symbolicName}"
            }
        }
    }


    @Nonnull
    HttpResponse refreshBundle(@Nonnull SimpleHttpClient httpClient, @Nonnull SlingServerConfiguration configuration) {
        final url = slingBundleConfig.getBundleControlUrl(null, configuration.bundleControlBaseUri, configuration.slingSupport)
        configuration.slingSupport.doPost(url, ['action': 'refresh'], httpClient)
    }


    static boolean bundleConfigStateIs(@Nonnull HttpResponse post, @Nonnull BundleState state) {
        if (post.code == HTTP_OK) {
            Map json = new JsonSlurper().parseText(post.body) as Map
            final stateString = json['state']
            if (stateString) {
                BundleState bundleState = BundleState.state(stateString as String)
                bundleState == state
            }
            else {
                final statusMessage = json['status.message']
                if (statusMessage != null) {
                    statusMessage == 'OK'
                }
                else {
                    log.error "Do not have 'stateString' or 'status.message' in ${json}"
                    true
                }
            }
        }
        else {
            false
        }
    }


    @Nonnull
    HttpResponse updateBundle(@Nonnull SimpleHttpClient httpClient,
                              @Nonnull SlingSupport slingSupport,
                              @Nonnull URI bundleControlBaseUri) {
        final url = slingBundleConfig.getBundleControlUrl(null, bundleControlBaseUri, slingSupport)
        slingSupport.doPost(url, ['action': 'update'], httpClient)
    }


    @Nonnull
    @SuppressWarnings("GroovyResultOfAssignmentUsed")
    static HttpResponse doAcrossServers(SlingServersConfiguration servers,
                                        @ClosureParams(value = SimpleType,
                                            options = ['SimpleHttpClient', 'SlingServerConfiguration'])
                                            Closure<HttpResponse> closure) {
        def httpResponse = new HttpResponse(HTTP_CLIENT_TIMEOUT, '')

        servers.each { configuration ->
            def slingSupport = configuration.slingSupport

            def resp = slingSupport.doHttp { httpClient ->
                closure.call(httpClient, configuration)
            }

            if (goodResponse(resp.code)) {
                // permissive: if any call is good, the result is good
                httpResponse = resp
            }
        }
        return httpResponse
    }


    static boolean goodResponse(int respCode) {
        respCode >= HTTP_OK && respCode < HTTP_BAD_REQUEST
    }


    @Nonnull
    HttpResponse getSlingBundleInformation(SimpleHttpClient httpClient, SlingSupport slingSupport,
                                           URI bundleControlUri) {
        final url = slingBundleConfig.getBundleInformationUrl(httpClient, slingSupport, bundleControlUri)
        if (url != null) {
            slingSupport.doGet(url, httpClient)
        }
        else {
            new HttpResponse(HTTP_BAD_REQUEST,
                "Can not get information on ${slingBundleConfig.symbolicName} - it is probably not installed")
        }
    }


    @Nonnull
    String getSlingBundleInformationOnAuthor() {
        SlingServerConfiguration configuration = slingBundleConfig.slingServers['author']

        final resp = configuration.slingSupport.doHttp { SimpleHttpClient httpClient ->
            getSlingBundleInformation(httpClient, configuration.slingSupport, configuration.bundleControlBaseUri)
        }

        (resp.code == HTTP_OK) ? resp.body : "${resp.code}: ${resp.body}"
    }


    @Nullable
    URI getBundleLocation(@Nonnull SimpleHttpClient httpClient, URI baseUri, String installPath,
                          String serverConfigName, SlingSupport slingSupport, URI bundleControlUri) {
        URI url = serverConfigToBundleLocation[serverConfigName]
        if (url == null) {
            final httpResponse = getSlingBundleInformation(httpClient, slingSupport, bundleControlUri)

            if (httpResponse.code == HTTP_OK) {
                try {
                    Map json = jsonSlurper.parseText(httpResponse.body) as Map

                    List<Map> dataProps = ((Map)((List)json.data)[0]).props as List<Map>
                    final location = dataProps.find { it.key == 'Bundle Location' }
                    if (location == null) return null
                    String fileLocation = location.value

                    String filePath = URI.create(fileLocation).path
                    URI uri = slingBundleConfig.getBundleUri(baseUri, installPath)
                    url = new URI(uri.scheme, uri.userInfo, uri.host, uri.port, filePath, null, null)
                    serverConfigToBundleLocation[serverConfigName] = url
                }
                catch (Exception exp) {
                    throw new GradleException("Could not read JSON from ${bundleControlUri}: \"${httpResponse.body}\"", exp)
                }
            }
            else {
                throw new GradleException("Problem getting bundle location from ${bundleControlUri} - ${httpResponse.code}: ${httpResponse.body}")
            }
        }
        url
    }


    @Nonnull
    static HttpResponse refreshAllPackages(@Nonnull SimpleHttpClient httpClient,
                                           @Nonnull SlingSupport slingSupport,
                                           @Nonnull URI bundleControlUriJson) {
        slingSupport.doPost(bundleControlUriJson, ['action': 'refreshPackages'], httpClient)
    }


    @Nonnull
    HttpResponse uninstallBundle(@Nonnull SimpleHttpClient httpClient,
                                 @Nonnull SlingSupport slingSupport,
                                 @Nonnull URI bundleControlBaseUri) {
        final url = slingBundleConfig.getBundleControlUrl(null, bundleControlBaseUri, slingSupport)
        slingSupport.doPost(url, ['action': 'uninstall'], httpClient)
    }


    @Nonnull
    HttpResponse removeBundle(SimpleHttpClient httpClient, SlingSupport slingSupport, URI bundleLocation) {
        slingSupport.doDelete(bundleLocation, httpClient)
    }


    @Nonnull
    HttpResponse uploadBundle(@Nonnull SimpleHttpClient httpClient,
                              @Nonnull SlingServerConfiguration serverConfiguration) {
        // Check to see if the bundle is already there. If so, remove it.
        removeExistingBundle(httpClient, serverConfiguration)

        final symbolicName = slingBundleConfig.symbolicName
        final installUri = serverConfiguration.baseInstallUri
        final sourceFile = slingBundleConfig.sourceFile

        def slingSupport = serverConfiguration.slingSupport

        if (slingSupport.makePath(installUri, httpClient)) {
            String filename = sourceFile.name
            log.info("Uploading ${filename}")
            def resp = slingSupport.doPost(installUri, [
                "${filename}": new FileBody(sourceFile, 'application/java-archive'),
            ], httpClient)

            // it's a new bundle, so its id will have changed
            def slingServers = slingBundleConfig.slingServers
            long maxValidateBundlesWaitMs = slingServers.maxWaitValidateBundlesMs
            long retryWaitMs = slingServers.retryWaitMs
            resetFelixId(httpClient, slingSupport, maxValidateBundlesWaitMs, symbolicName, retryWaitMs)

            return resp
        }
        else {
            new HttpResponse(HTTP_BAD_METHOD, 'Could not create area to put file in')
        }
    }


    private void removeExistingBundle(SimpleHttpClient httpClient, SlingServerConfiguration serverConfiguration) {
        final bundleInfoResp = getSlingBundleInformation(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)

        def symbolicName = slingBundleConfig.symbolicName

        if (bundleInfoResp.code == HTTP_OK) {
            String stateString
            try {
                Map bundleInfo = new JsonSlurper().parseText(bundleInfoResp.body) as Map
                def data = (List)bundleInfo.data
                def firstItem = (Map)data[0]
                stateString = firstItem.state as String
            }
            catch (Exception exp) {
                throw new GradleException("Could not read bundle information for ${slingBundleConfig.name}: \"${bundleInfoResp.body}\"", exp)
            }

            if (stateString == ACTIVE.stateString) {
                log.info("Uninstalling ${symbolicName}")
                uninstallBundle(httpClient, serverConfiguration.slingSupport, serverConfiguration.bundleControlBaseUri)
            }
            log.info("Removing ${symbolicName}")

            // TODO: Run only if uninstall successful
            def bundleLocation = getBundleLocation(httpClient, serverConfiguration.baseUri,
                serverConfiguration.installPath, serverConfiguration.name, serverConfiguration.slingSupport,
                serverConfiguration.bundleControlBaseUri)
            removeBundle(httpClient, serverConfiguration.slingSupport, bundleLocation)
        }
        else {
            log.info("${slingBundleConfig.getBundleUri(serverConfiguration.baseUri, serverConfiguration.installPath)} does not currently exist")
        }
    }


    void resetFelixId(@Nonnull SimpleHttpClient httpClient, @Nonnull SlingSupport slingSupport,
                      long maxResetFelixIdMs, String symbolicName, long retryWaitMs) {
        Number felixId = null

        block(maxResetFelixIdMs, { felixId == null }, {
            slingSupport.clearIdMappings()
            felixId = slingSupport.getIdForSymbolicName(symbolicName, httpClient)
        } as Closure<Void>, retryWaitMs)

        if (felixId == null) {
            throw new IllegalStateException("Could not find id for ${symbolicName}")
        }

        slingBundleConfig.felixId = felixId
    }


    void validateAllBundles(@Nonnull List<String> symbolicNames,
                            @Nonnull SimpleHttpClient httpClient,
                            @Nonnull SlingServerConfiguration configuration) {
        final slingSupport = configuration.slingSupport

        def serverName = configuration.name
        log.info "Checking for NON-ACTIVE bundles on ${serverName}"

        final pollingTxt = new DotPrinter()
        boolean bundlesActive = false

        block(maxWaitMs, { configuration.active && bundlesActive == false }, {
            log.info pollingTxt.increment()

            def resp = slingSupport.doGet(configuration.bundleControlUriJson, httpClient)
            if (resp.code == HTTP_OK) {
                try {
                    def json = new JsonSlurper().parseText(resp.body) as Map
                    Collection<Map<String, Object>> data = json.data as List

                    def knownBundles = data.findAll { Map b -> symbolicNames.contains(b.symbolicName) }
                    def knownBundleNames = knownBundles.collect { Map b -> (String)b.symbolicName }
                    def missingBundleNames = (symbolicNames - knownBundleNames)
                    def missingBundles = missingBundleNames.collect { String name ->
                        (Map<String, Object>)[symbolicName: name, state: MISSING.stateString]
                    }
                    def allBundles = knownBundles + missingBundles

                    if (!hasAnInactiveBundle(allBundles)) {
                        if (log.debugEnabled) allBundles.each { Map b -> log.debug "Active bundle: ${b.symbolicName}" }
                        bundlesActive = true
                    }
                }
                catch (Exception exp) {
                    throw new GradleException("Problem parsing \"${resp.body}\"", exp)
                }
            }
            else {
                if (resp.code == HTTP_CLIENT_TIMEOUT)
                    configuration.active = false
                else
                    throw new GradleException("Could not get bundle data. ${resp.code}: ${resp.body}")
            }
        } as Closure<Void>, retryWaitMs)

        if (configuration.active == false) return

        if (bundlesActive == false)
            throw new GradleException("FAILED: Not all bundles are ACTIVE on ${serverName}")
        else
            log.info("Bundles are ACTIVE on ${serverName}")
    }


    @SuppressWarnings("GroovyTrivialIf")
    protected static boolean areAllBundlesActive(Map json, String groupProperty) {
        // Reading Json response for bundle status
        // The status response is an array like "s": [ 84, 81, 3, 0, 0 ],
        // Status number described as: [bundles existing, active, fragment, resolved, installed]
        // Ref Url: http://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html

        def statuses = json.s as List
        def resolved = statuses[3] as Integer
        def installed = statuses[4] as Integer

        if (resolved == 0 && installed == 0) {
            log.debug "There are no bundles in the \"resolved\" or \"installed\" state"
            return true
        }

        List<Map> data = json.data as List

        def inactiveBundles = inactiveBudles(data).collect { it.symbolicName } as List<String>

        if (log.infoEnabled) inactiveBundles.each { log.info "Inactive bundle: ${it}" }

        if (inactiveBundles.isEmpty() || !inactiveBundles.any { it.contains(groupProperty) })
            return true
        else
            return false
    }


    private static Collection<Map> inactiveBudles(Collection<Map> knownBundles) {
        return knownBundles.findAll { bundle ->
            bundle.state == INSTALLED.stateString ||
                bundle.state == RESOLVED.stateString ||
                bundle.state == MISSING.stateString
        } as Collection<Map>
    }


    protected boolean hasAnInactiveBundle(final Collection<Map<String, Object>> knownBundles) {
        final activeBundles = knownBundles.findAll { bundle ->
            bundle.state == ACTIVE.stateString ||
                bundle.state == FRAGMENT.stateString
        } as Collection<Map>

        final inactiveBundles = inactiveBudles(knownBundles)

        if (log.infoEnabled) inactiveBundles.each { log.info("bundle ${it.symbolicName} NOT active: ${it.state}") }
        if (log.debugEnabled) activeBundles.each { log.debug("bundle ${it.symbolicName} IS active") }

        inactiveBundles.size() > 0
    }


    void uninstallAllBundles(@Nonnull List<String> symbolicNames,
                             @Nonnull SimpleHttpClient httpClient,
                             @Nonnull SlingServerConfiguration configuration,
                             @Nullable @ClosureParams(FirstParam.FirstGenericType) Closure<Boolean> predicate) {
        log.info "Uninstalling/removing bundles on ${configuration.slingSupport.serverConf.name}: ${symbolicNames}"

        configuration.slingSupport.clearIdMappings()

        symbolicNames.each { String symbolicName ->
            if (predicate != null && predicate.call(symbolicName)) {
                slingBundleConfig.felixId = configuration.slingSupport.getIdForSymbolicName(symbolicName, httpClient)
                log.info "Stopping $symbolicName on ${configuration.slingSupport.serverConf.name}"
                stopBundle(httpClient, configuration)
                log.info "Uninstalling $symbolicName on ${configuration.slingSupport.serverConf.name}"
                uninstallBundle(httpClient, configuration.slingSupport, configuration.bundleControlBaseUri)
            }
        }
    }


    @TypeChecked
    static class DotPrinter {
        private final StringBuilder str = new StringBuilder()


        String increment() {
            str.append('.' as char).toString()
        }
    }


    @SuppressWarnings("GroovyUnnecessaryReturn")
    void checkActiveBundles(String groupProperty, SimpleHttpClient httpClient, SlingServerConfiguration serverConf) {
        def slingSupport = serverConf.slingSupport
        def serverName = serverConf.name
        def bundleControlUriJson = serverConf.bundleControlUriJson

        log.info "Checking for bundles status as Active on ${serverName} for ${groupProperty}"

        final pollingTxt = new DotPrinter()
        boolean bundlesActive = false

        block(maxWaitMs, { serverConf.active && bundlesActive == false }, {
            log.info pollingTxt.increment()

            def resp = slingSupport.doGet(bundleControlUriJson, httpClient)
            if (resp.code == HTTP_OK) {
                try {
                    def json = new JsonSlurper().parseText(resp.body) as Map

                    if (areAllBundlesActive(json, groupProperty)) {
                        bundlesActive = true
                    }
                }
                catch (Exception exp) {
                    throw new GradleException("Could not parse \"${resp.body}\"", exp)
                }
            }
            else if (resp.code == HTTP_CLIENT_TIMEOUT) {
                serverConf.active = false
            }
        } as Closure<Void>, retryWaitMs)

        if (serverConf.active == false)
            return
        else if (bundlesActive == false)
            throw new GradleException("Check Bundle Status FAILED: Not all bundles are ACTIVE on ${serverName}")
        else
            log.info("Bundles are ACTIVE on ${serverName}!")
    }

}
