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

import groovy.transform.TypeChecked

import java.lang.reflect.Field

/**
 * Provides basic support for "Fixtures" DSL.
 */
@TypeChecked
class FixtureBase {
    protected FixtureBase() {}


    protected static <T extends FixtureBase> T make(Class<T> clz, Closure closure) {
        final T builder = (T)clz.newInstance()
        closure.delegate = builder
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
        builder
    }

    /**
     * Treat methods like setting properties.
     *
     * For example, these are all effectively the same:<ul>
     *   <li><code>installPath = '/'</code>
     *   <li><code>setInstallPath('/')</code>
     *   <li><code>installPath '/'</code>
     * </ul>
     */
    @SuppressWarnings("GroovyUntypedAccess")
    def methodMissing(String name, args) {
        final field = findField(name)

        if (field != null) {
            field.set(this, ((Object[])args)[0])
        }
        else {
            throw new MissingMethodException(name, this.class, (Object[])args)
        }
    }


    private Field findField(String name) {
        def field = safeFindField(name)
        if (field == null) {
            field = safeFindField("_${name}")
        }
        field
    }


    @SuppressWarnings("GroovyUnusedCatchParameter")
    private Field safeFindField(String name) {
        try {
            final field = this.class.getField(name)
            field.accessible = true
            field
        }
        catch (NoSuchFieldException e1) {
            try {
                final field = this.class.getDeclaredField(name)
                field.accessible = true
                field
            }
            catch (NoSuchFieldException e2) {
                null
            }
        }
    }

}
