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

package com.twcable.gradle.bundlor

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(BundlorPlugin)
class BundlorPluginSpec extends Specification {

    @Unroll
    def "defined osgiVersion(#baseVer)"(baseVer, osgiVer) {
        expect:
        BundlorPlugin.osgiVersion(baseVer) == osgiVer

        where:
        baseVer         | osgiVer
        '1.2'           | '1.2.0'
        '1.2.3'         | '1.2.3'
        '1.2.3.RELEASE' | '1.2.3.RELEASE'
        '1'             | '1.0.0'
        '1.A'           | '1.0.0.A'
        '1.A.3'         | '1.0.0.A.3'
        '1.2.A'         | '1.2.0.A'
    }


    @Unroll
    def "undefined osgiVersion(#baseVer)"(baseVer, osgiVer) {
        expect:
        BundlorPlugin.osgiVersion(baseVer) == osgiVer

        where:
        baseVer | osgiVer
        '1-A'   | '1-A'
        'B.A.3' | 'B.A.3'
        'REL'   | 'REL'
        '1-3.4' | '1-3.4'
        ''      | ''
    }


    @Unroll
    def "osgiVersionUpperLimit(#baseVer)"(baseVer, upperVer) {
        expect:
        BundlorPlugin.osgiVersionUpperLimit(baseVer) == upperVer

        where:
        baseVer         | upperVer
        '1.2'           | '2.0.0'
        '1.2.3'         | '2.0.0'
        '1.2.3.RELEASE' | '2.0.0'
        '1'             | '2.0.0'
        '1.A'           | '2.0.0'
        '1.2.A'         | '2.0.0'
        '1.A.3'         | '2.0.0'
        '1-A'           | '1-A'
        'B.A.3'         | 'B.A.3'
        'REL'           | 'REL'
        '1-3.4'         | '1-3.4'
        ''              | ''
    }

}
