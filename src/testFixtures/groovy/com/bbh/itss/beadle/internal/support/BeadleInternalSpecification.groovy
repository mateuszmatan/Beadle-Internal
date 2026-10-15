package com.bbh.itss.beadle.internal.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import java.time.Instant

import static java.time.temporal.ChronoUnit.SECONDS
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * Runs Beadle-Internal on a random port with the demo adapters on an in-memory H2 in Oracle mode, a clock that only
 * moves when a spec moves it, and {@link GuiStub} as Beadle-GUI. Every spec shares the one application, so a spec
 * tells its data apart by the correlation id of its calls or by a unique name.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-internal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'beadle.services.token=regression-service-token'])
@ActiveProfiles('local')
@Import(RegressionClock)
abstract class BeadleInternalSpecification extends ServiceSpecification {

    static final String TOKEN = 'regression-service-token'
    static final String USER = 'Mateusz Matan'
    static final GuiStub GUI = GuiStub.start()

    @DynamicPropertySource
    static void beadleGui(DynamicPropertyRegistry registry) {
        registry.add('beadle.gui.url') { GUI.url }
    }

    @Autowired
    MutableClock clock

    String correlationId = UUID.randomUUID().toString()

    @Override
    ServiceClient getApi() {
        super.api.withHeaders([Authorization: "Bearer $TOKEN" as String, 'X-Beadle-User': USER,
                               'X-Correlation-Id': correlationId])
    }

    ServiceClient getAnonymous() {
        super.api
    }

    /**
     * The call log of this spec's calls, oldest first.
     */
    List<Map> callLog(String correlation = correlationId) {
        def response = api.get("/api/internal/call-log?correlationId=$correlation&size=500")
        assert response.status == 200: response
        (response.json.entries as List<Map>).reverse()
    }

    static String uniqueName(String prefix) {
        "$prefix ${UUID.randomUUID().toString().take(8)}"
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RegressionClock {

        @Bean
        @Primary
        MutableClock regressionClock() {
            new MutableClock(Instant.now().truncatedTo(SECONDS))
        }
    }
}
