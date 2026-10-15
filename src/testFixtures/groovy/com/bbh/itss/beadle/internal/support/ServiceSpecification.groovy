package com.bbh.itss.beadle.internal.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

abstract class ServiceSpecification extends Specification {

    @Value('${local.server.port}')
    int port

    @Autowired
    JdbcTemplate jdbc

    ServiceClient getApi() {
        new ServiceClient("http://localhost:$port")
    }
}
