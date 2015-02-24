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

package com.twcable.gradle.http

import groovy.util.logging.Slf4j
import org.apache.commons.codec.binary.Base64
import org.apache.http.HttpVersion
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.CoreProtocolPNames
import org.apache.http.util.EntityUtils

import javax.annotation.Nonnull

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT

@Slf4j
class DefaultSimpleHttpClient implements SimpleHttpClient {
    DefaultHttpClient client
    String username
    String password


    DefaultSimpleHttpClient() {
        client = new DefaultHttpClient()
        setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1)
    }


    @Override
    void setParameter(String name, Object value) {
        client.params.setParameter(name, value)
    }


    @Override
    void setCredentials(AuthScope scope, Credentials credentials) {
        client.credentialsProvider.setCredentials(scope, credentials)
    }


    @Nonnull
    @Override
    HttpResponse execute(HttpUriRequest httpMessage) {
        if (username) {
            setCredentials(
                new AuthScope(httpMessage.URI.host, httpMessage.URI.port),
                new UsernamePasswordCredentials(username, password))

            byte[] authBytes = "${username}:${password}".getBytes("UTF8")
            httpMessage.setHeader('Authorization', "Basic ${Base64.encodeBase64URLSafeString(authBytes)}")
        }

        httpMessage.setHeader('Accept', 'application/json')

        log.debug "${httpMessage.method} to ${httpMessage.URI}"

        try {
            def execute = client.execute(httpMessage)
            int statusCode = execute.statusLine.statusCode
            String response = (execute.entity == null) ? '' : EntityUtils.toString(execute.entity, 'UTF-8')
            log.debug "status code: ${statusCode}; ${response}"

            new HttpResponse(statusCode, response)
        }
        catch (HttpHostConnectException exp) {
            new HttpResponse(HTTP_CLIENT_TIMEOUT, exp.message)
        }
    }


    @Override
    void shutdown() {
        client.connectionManager.shutdown()
    }

}
