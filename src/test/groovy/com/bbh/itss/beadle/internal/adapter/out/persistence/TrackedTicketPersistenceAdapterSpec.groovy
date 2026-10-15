package com.bbh.itss.beadle.internal.adapter.out.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.beadle.internal.support.Fixtures.CREATED
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:tracked-ticket-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(TrackedTicketPersistenceAdapter)
class TrackedTicketPersistenceAdapterSpec extends Specification {

    static final Instant LATER = Instant.parse('2026-10-15T09:30:00Z')

    @Subject
    @Autowired
    TrackedTicketPersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    def setup() {
        jdbc.update('DELETE FROM BEADLE_INTERNAL_CYBERTRACK_TICKET')
    }

    def "a followed ticket is written down and read back among the open ones until it is closed"() {
        when:
        adapter.track(trackedTicket())

        then:
        adapter.open() == [trackedTicket()]
        jdbc.queryForMap('SELECT TICKET_KEY, STATUS, CORRELATION_ID FROM BEADLE_INTERNAL_CYBERTRACK_TICKET') ==
                [TICKET_KEY: 'SCP-1234', STATUS: 'To Do', CORRELATION_ID: 'corr-1']

        when:
        adapter.save(trackedTicket().reported('In Progress', LATER))

        then:
        adapter.open() == [trackedTicket(status: 'In Progress', lastSeenAt: LATER, lastEventAt: LATER)]

        when:
        adapter.save(trackedTicket().reported('Done', LATER))

        then:
        adapter.open() == []
        jdbc.queryForObject('SELECT STATUS FROM BEADLE_INTERNAL_CYBERTRACK_TICKET', String) == 'Done'
    }

    def "the open tickets come oldest first"() {
        given:
        adapter.track(trackedTicket(key: 'SCP-3', createdAt: CREATED.plusSeconds(60)))
        adapter.track(trackedTicket(key: 'SCP-2', createdAt: CREATED))
        adapter.track(trackedTicket(key: 'SCP-1', createdAt: CREATED))
        adapter.track(trackedTicket(key: 'SCP-0', createdAt: CREATED.minusSeconds(60), closedAt: CREATED))

        expect:
        adapter.open()*.key() == ['SCP-1', 'SCP-2', 'SCP-3']
    }

    def "a ticket that cannot be written down is not followed and does not fail its creation"() {
        when:
        adapter.track(trackedTicket(key: 'SCP-' + '9' * 200))

        then:
        noExceptionThrown()
        adapter.open() == []
    }

    def "a ticket that cannot be saved during a poll fails the save"() {
        when:
        adapter.save(trackedTicket(createdAt: null))

        then:
        thrown(RuntimeException)
    }
}
