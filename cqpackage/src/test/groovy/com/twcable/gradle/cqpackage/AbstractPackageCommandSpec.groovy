package com.twcable.gradle.cqpackage

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import spock.lang.Specification

import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings("GroovyAssignabilityCheck")
abstract class AbstractPackageCommandSpec extends Specification {
    SlingServersConfiguration slingServersConfiguration
    SlingServerConfiguration slingServerConfiguration
    SlingSupport slingSupport


    def setup() {
        final httpClient = Mock(SimpleHttpClient)
        slingSupport = Mock(SlingSupport)
        slingSupport.doHttp(_) >> { Closure closure -> closure.delegate = slingSupport; closure.call(httpClient) }

        slingServerConfiguration = Mock(SlingServerConfiguration) {
            getSlingSupport() >> slingSupport
            getActive() >> true
        }

        slingServersConfiguration = Stub(SlingServersConfiguration) {
            getMaxWaitValidateBundlesMs() >> 1000
            getRetryWaitMs() >> 10
            iterator() >> [slingServerConfiguration].iterator()
        }
    }


    protected void setupSlingSupport(success, msg, installedPackage) {
        // List Packages
        slingSupport.doGet(_, _) >> {
            new HttpResponse(HTTP_OK, "{\"results\":[{\"name\":\"${installedPackage}\",\"group\":\"testing\"}]}")
        }

        // command POST
        slingSupport.doPost(_, _, _) >> {
            new HttpResponse(HTTP_OK, "{\"success\": ${success}, \"msg\": \"${msg}\"}")
        }
    }

//    def "tester"() {
//        // a real connection to find out how Sling actually responds
//        def serverConfiguration = new SlingServerConfiguration(name: "tester", port: 4502)
//
//        when:
//        def status = UploadPackage.upload(new File("/Users/jmoore/Downloads/testpackage-1.0.1.zip"), serverConfiguration, 10_000L, 50L)
//
//        then:
//        status == Status.OK
//    }

}
