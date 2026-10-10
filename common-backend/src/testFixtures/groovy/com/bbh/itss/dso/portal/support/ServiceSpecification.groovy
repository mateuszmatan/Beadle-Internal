package com.bbh.itss.dso.portal.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import static java.lang.System.nanoTime

abstract class ServiceSpecification extends Specification {

    private static final AtomicInteger SEQUENCE = new AtomicInteger()

    @Value('${local.server.port}')
    int port

    @Autowired
    JdbcTemplate jdbc

    PortalClient getApi() {
        new PortalClient("http://localhost:$port")
    }

    static String uniqueCode(String prefix = 'REG') {
        "${prefix}${SEQUENCE.incrementAndGet()}${nanoTime() % 100_000}"
    }

    Map createProduct(Map product) {
        def response = api.post('/api/products', product)
        assert response.status == 201: response
        response.json as Map
    }
}
