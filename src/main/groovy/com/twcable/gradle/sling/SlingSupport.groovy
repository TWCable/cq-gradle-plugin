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

package com.twcable.gradle.sling

import com.twcable.gradle.http.DefaultSimpleHttpClient
import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import groovy.json.JsonSlurper
import groovy.transform.TypeChecked
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.ContentBody
import org.apache.http.entity.mime.content.StringBody

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.charset.Charset

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_OK

/**
 * This provides methods to easily use the HTTP interface to Sling to work with the JCR.
 */
@Slf4j
@TypeChecked
@SuppressWarnings(["GroovyMultipleReturnPointsPerMethod", "GrMethodMayBeStatic"])
class SlingSupport {
    private static final Charset UTF8 = Charset.forName('UTF-8')

    private final SlingServerConfiguration serverConf

    @SuppressWarnings("GroovyConstantNamingConvention")
    private final static Map<SlingServerConfiguration, Map<String, Number>> serverToSymbolicNameToId = [:]


    SlingSupport(SlingServerConfiguration slingServerConfiguration) {
        this.serverConf = slingServerConfiguration
    }

    /**
     * While "predicate" is true and "maxWaitTime" hasn't been surpassed, will run "action" every
     * "waitPeriodMs" milliseconds
     *
     * @param maxWaitTimeMs max number of milliseconds to run the action
     * @param predicate while true, will continue running "action"
     * @param action the action to run
     * @param waitPeriodMs how long to wait (in milliseconds) between executions of "action"
     */
    static void block(long maxWaitTimeMs, Closure<Boolean> predicate, Closure<Void> action, long waitPeriodMs) {
        if (waitPeriodMs < 1) throw new IllegalArgumentException("waitPeriodMs < 1: ${waitPeriodMs}")
        log.info "Blocking for at most ${maxWaitTimeMs}ms"

        long startTime = System.currentTimeMillis()
        long stopTime = startTime + maxWaitTimeMs

        action.call()
        while (predicate.call() && (System.currentTimeMillis() <= stopTime)) {
            log.debug "Trying again after waiting for ${waitPeriodMs}ms"
            Thread.sleep(waitPeriodMs)
            action.call()
        }
    }


    protected SimpleHttpClient createHttpClient() {
        return new DefaultSimpleHttpClient(username: serverConf.username, password: serverConf.password)
    }

    /**
     * Returns the numeric Bundle ID (which most Sling commands need) for the server bundle with the given name.
     *
     * @param name the bundle name
     * @param httpClient the HTTP client to use; useful for batching calls. Otherwise a new client is created
     * @return null if it can't find the bundle with the given name
     */
    @Nullable
    Number getIdForSymbolicName(@Nonnull String name, @Nonnull SimpleHttpClient httpClient = null) {
        if (serverToSymbolicNameToIdMissing()) {
            if (httpClient == null) {
                doHttp { SimpleHttpClient hc ->
                    populateSymbolicNameToIdMap(hc)
                    new HttpResponse(HTTP_OK, '')
                }
            }
            else {
                populateSymbolicNameToIdMap(httpClient)
            }
        }

        return serverToSymbolicNameToId[serverConf][name]
    }


    private boolean serverToSymbolicNameToIdMissing() {
        return serverToSymbolicNameToId.isEmpty() ||
            serverToSymbolicNameToId[serverConf] == null ||
            serverToSymbolicNameToId[serverConf].isEmpty()
    }


    @SuppressWarnings("GroovyResultOfAssignmentUsed")
    private void populateSymbolicNameToIdMap(SimpleHttpClient httpClient) {
        Map<String, Number> map = [:]
        def resp = doGet(serverConf.bundleControlUriJson, httpClient)
        if (resp.code == HTTP_OK) {
            def json = new JsonSlurper().parseText(resp.body) as Map
            List<Map> data = json.data as List
            data.each { Map datum ->
                map.put((String)datum.symbolicName, datum.id as Number)
            }
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            serverConf.active = false
        }
        serverToSymbolicNameToId[serverConf] = map
    }


    void clearIdMappings() {
        if (serverToSymbolicNameToId != null) serverToSymbolicNameToId[serverConf] = [:]
    }

    /**
     * Calls the given closure, passing it a new instance of {@link SimpleHttpClient}.
     * @param closure the closure to invoke
     * @return the result of calling the closure
     */
    HttpResponse doHttp(@DelegatesTo(SlingSupport)
                        @ClosureParams(value = SimpleType, options = 'com.twcable.gradle.http.SimpleHttpClient')
                            Closure closure) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        closure.delegate = this
        def httpClient = createHttpClient()
        try {
            return (HttpResponse)closure.call(httpClient)
        }
        finally {
            httpClient.shutdown()
        }
    }


    static class MkCol extends HttpRequestBase {
        final String method = 'MKCOL'
    }

    /**
     * Calls HTTP MKCOL against the given URL to create the JCR Node.
     *
     * @param url the URL to create a Node at
     * @param httpClient the client to use for HTTP
     *
     * @return HTTP_CREATED if it created the node;
     * HTTP_METHOD_NOT_ALLOWED if it already exists;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    HttpResponse doMkcol(URI url, @Nonnull SimpleHttpClient httpClient) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        MkCol mkCol = new MkCol(URI: url)
        // returns a HTTP_CREATED if it creates the column
        // returns a HTTP_METHOD_NOT_ALLOWED if the column already exists
        def resp = httpClient.execute(mkCol)
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Calls HTTP GET against the given URL.
     *
     * @param url the URL to GET
     * @param httpClient the client to use for HTTP
     *
     * @return HTTP_OK if it was successful;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    @Nonnull
    HttpResponse doGet(URI url, SimpleHttpClient httpClient) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        log.info "GET ${url}"
        HttpGet get = new HttpGet(url)
        def resp = httpClient.execute(get)
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Calls HTTP DELETE against the given URL.
     *
     * @param url the URL for the Node to delete
     * @param httpClient the client to use for HTTP
     *
     * @return HTTP_OK if it was successful;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    HttpResponse doDelete(URI url, SimpleHttpClient httpClient) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        def resp = httpClient.execute(new HttpDelete(url))
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Calls HTTP POST against the given URL.
     *
     * @param url the URL to POST to
     * @param httpClient the client to use for HTTP
     *
     * @return HTTP_OK or HTTP_CREATED if it was successful;
     * HTTP_CLIENT_TIMEOUT if the server doesn't respond;
     * HTTP_NOT_FOUND if the URL is missing
     */
    HttpResponse doPost(URI url, Map parts, SimpleHttpClient httpClient) {
        if (!serverConf.active) return new HttpResponse(HTTP_CLIENT_TIMEOUT, "${serverConf.name} is not responding")
        if (url == null) return new HttpResponse(HTTP_NOT_FOUND, 'Missing URL')
        HttpPost post = new HttpPost(url)
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)

        parts.each { key, value ->
            final ContentBody val

            switch (value) {
                case String:
                    val = stringBody((String)value); break
                case ContentBody:
                    val = (ContentBody)value; break
                case File:
                    val = null; break
                default:
                    val = null
            }
            entity.addPart((String)key, val)
        }

        post.entity = entity

        parts.each { log.debug "\t${it.key}: ${it.value}" }

        def resp = httpClient.execute(post)
        if (resp.code == HTTP_CLIENT_TIMEOUT) serverConf.active = false
        return resp
    }

    /**
     * Does the Node at the given URL currently exist?
     *
     * @param url the Node to check
     * @param httpClient the client to use for HTTP
     */
    boolean nodeExists(URI url, SimpleHttpClient httpClient) {
        HttpResponse post = doPost(url, [':http-equiv-accept': 'application/json'], httpClient)
        return post.code == HTTP_OK
    }

    /**
     * Create the nodes needed to fully represent the given URL path. Missing intermediate nodes are created.
     *
     * @param url the Node to create
     * @param httpClient the client to use for HTTP
     *
     * @return was it successful?
     */
    boolean makePath(URI url, SimpleHttpClient httpClient) {
        return makePathParts(url, pathParts(url), httpClient, 1, true)
    }


    @Nonnull
    private String[] pathParts(@Nonnull URI url) {
        final uri = url.normalize()
        final path = uri.path
        return path.split('/')
    }


    private boolean makePathParts(URI url, String[] pathParts, SimpleHttpClient httpClient, int depth, boolean checkGet) {
        log.debug "makePath(${url}, ${depth}, ${checkGet})"

        if (depth == pathParts.length) return true

        final uri = url.normalize()
        final checkUrl = new URI(uri.scheme, uri.userInfo, uri.host, uri.port, pathParts[0..depth].join('/'), null, null)

        boolean exists = checkGet ? nodeExists(checkUrl, httpClient) : false
        if (exists) {
            log.debug "$url exists"
            exists = makePathParts(url, pathParts, httpClient, depth + 1, true)
        }
        else {
            if (doMkcol(checkUrl, httpClient).code == HTTP_CREATED) {
                exists = true
            }
            else { // HttpURLConnection.HTTP_METHOD_NOT_ALLOWED
                exists = makePathParts(url, pathParts, httpClient, depth + 1, true)
            }
        }
        exists
    }


    private static StringBody stringBody(String text) {
        new StringBody(text, 'text/plain', UTF8)
    }

}
