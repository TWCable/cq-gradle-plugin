package com.twcable.gradle.cqpackage

import com.twcable.gradle.sling.SlingServerConfiguration
import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.vault.packaging.Dependency
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.SuccessOrFailure.failure
import static com.twcable.gradle.cqpackage.SuccessOrFailure.success

@Slf4j
class RuntimePackageProperties extends PackagePropertiesImpl {
    final Properties propertiesMap

    static final String RESOLVED_DEPENDENCIES = 'resolvedDependencies'
    static final String SCREENSHOTS = 'screenshots'
    static final String FILTER = 'filter'


    RuntimePackageProperties(Properties propertiesMap) {
        this.propertiesMap = propertiesMap
    }


    Dependency[] getResolvedDependencies() {
        def deps = getProperty(RESOLVED_DEPENDENCIES)
        if (deps == null) {
            return Dependency.EMPTY
        }
        else {
            return Dependency.parse(deps)
        }
    }


    boolean hasUnresolvedDependencies() {
        return getDependencies().length > getResolvedDependencies().length
    }


    String getName() {
        return id.name
    }


    String getPath() {
        return getProperty('path')
    }


    String getDownloadName() {
        return getProperty('download')
    }


    @SuppressWarnings("GroovyUnusedDeclaration")
    String getScreenshots() {
        return getProperty('screenshots')
    }


    static RuntimePackageProperties fromJson(Map json) {
        def props = new Properties()
        json.each { Object k, Object v ->
            if (v instanceof String || v instanceof Boolean || v instanceof Number) {
                log.debug "Setting property ${k}: ${v}"
                props.put(k, v.toString())
            }
            else if (k == 'dependencies') {
                dependencies(v as Collection<Map>, props)
                resolvedDependencies(v as Collection<Map>, props)
            }
            else if (k == 'screenshots') {
                final str = (v as List).join(',')
                log.debug "Setting property ${k}: ${str}"
                props.put(SCREENSHOTS, str)
            }
            else if (k == 'filter') {
                final jsonBuilder = new JsonBuilder(v)
                final str = jsonBuilder.toString()
                log.debug "Setting property ${k}: ${str}"
                props.put(FILTER, str)
            }
            else {
                log.warn("Unknown pair when creating RuntimePackageProperties - ${k}: ${v}")
            }
        }
        return new RuntimePackageProperties(props)
    }


    private static void resolvedDependencies(Collection<Map> v, Properties props) {
        final resDeps = v.
            findAll { Map dep -> dep.id != null && dep.id != '' }.
            collect { Map dep -> dep.id }.
            collect { String depStr -> Dependency.fromString(depStr) } as Dependency[]
        final resDepsStr = Dependency.toString(resDeps)
        log.debug "Setting property ${RESOLVED_DEPENDENCIES}: ${resDepsStr}"
        props.put(RESOLVED_DEPENDENCIES, resDepsStr)
    }


    private static void dependencies(Collection<Map> v, Properties props) {
        final deps = v.
            collect { Map dep -> dep.name }.
            collect { String depStr -> Dependency.fromString(depStr) } as Dependency[]
        final depsStr = Dependency.toString(deps)
        log.debug "Setting property ${NAME_DEPENDENCIES}: ${depsStr}"
        props.put(NAME_DEPENDENCIES, depsStr)
    }

    /**
     * Asks the given server for its information for the package identified by "packageName".
     */
    @Nonnull
    static SuccessOrFailure<RuntimePackageProperties> packageProperties(SlingServerConfiguration serverConfig,
                                                                        long maxWaitMs, long retryWaitMs,
                                                                        String packageName) {
        if (!serverConfig.active) return failure(Status.SERVER_INACTIVE)

        def sf = ListPackages.listPackages(serverConfig, maxWaitMs, retryWaitMs)
        if (sf.failed()) {
            return failure(sf.error)
        }

        def packagesProps = sf.value
        def packageProp = packagesProps.find { it.name == packageName }
        if (packageProp == null) {
            log.info "Could not find ${packageName} in ${packagesProps.collect { it.name }}"
            return failure(NO_PACKAGE)
        }
        return success(packageProp)
    }

}
