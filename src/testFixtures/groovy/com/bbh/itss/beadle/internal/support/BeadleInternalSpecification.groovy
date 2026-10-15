package com.bbh.itss.beadle.internal.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-internal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'beadle.services.token=regression-service-token'])
@ActiveProfiles('local')
abstract class BeadleInternalSpecification extends ServiceSpecification {

    static final String TOKEN = 'regression-service-token'
    static final String USER = 'Mateusz Matan'

    String correlationId = UUID.randomUUID().toString()

    @Override
    ServiceClient getApi() {
        super.api.withHeaders([Authorization: "Bearer $TOKEN" as String, 'X-Beadle-User': USER,
                               'X-Correlation-Id': correlationId])
    }
}
