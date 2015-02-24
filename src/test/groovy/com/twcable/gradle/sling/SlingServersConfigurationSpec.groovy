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

import spock.lang.Specification
import spock.lang.Subject

@Subject(SlingServersConfiguration)
class SlingServersConfigurationSpec extends Specification {

    def setup() {
        clearProperties()
    }


    public void clearProperties() {
        System.clearProperty("envJson");
        System.clearProperty("environment");
    }


    def "has expected servers as properties"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.author
        servers.publisher
    }


    def "has expected servers as key lookup"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers['author']
        servers['publisher']
    }


    def "can iterate over expected servers"() {
        given:
        SlingServersConfiguration servers = new SlingServersConfiguration()

        expect:
        servers.collect { it.name }.containsAll(['author', 'publisher'])
    }

}
