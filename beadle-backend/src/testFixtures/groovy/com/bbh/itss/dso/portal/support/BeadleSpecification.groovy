package com.bbh.itss.dso.portal.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'dso.demo-data=false',
        'dso.demo.protech-apply-delay=PT0S'])
@ActiveProfiles('local')
abstract class BeadleSpecification extends ServiceSpecification {

    static Map product(Map overrides = [:]) {
        [code: 'CERT', name: 'CertScanner', departmentId: 3, ownerTeam: 'Technology Architecture',
         contactEmail: 'ta-team@bbh.com'] + overrides
    }
}
