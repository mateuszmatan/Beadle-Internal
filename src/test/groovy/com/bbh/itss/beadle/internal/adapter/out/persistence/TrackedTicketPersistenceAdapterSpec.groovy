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

import static com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp.DEFAULT
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
        jdbc.queryForMap('SELECT TICKET_KEY, STATUS, CORRELATION_ID, DEMO FROM BEADLE_INTERNAL_CYBERTRACK_TICKET') ==
                [TICKET_KEY: 'SCP-1234', STATUS: 'To Do', CORRELATION_ID: 'corr-1', DEMO: 0]

        when:
        adapter.save(trackedTicket().reported('In Progress', LATER, DEFAULT))

        then:
        adapter.open() == [trackedTicket(status: 'In Progress', lastSeenAt: LATER, lastEventAt: LATER)]

        when:
        adapter.save(trackedTicket().reported('Done', LATER, DEFAULT))

        then:
        adapter.open() == []
        jdbc.queryForObject('SELECT STATUS FROM BEADLE_INTERNAL_CYBERTRACK_TICKET', String) == 'Done'
    }

    def "a ticket the demo made up is read back as one, with the polls that missed it"() {
        when:
        adapter.track(trackedTicket(demo: true, misses: 2))

        then:
        adapter.open() == [trackedTicket(demo: true, misses: 2)]
    }

    def "the open ticket of a change is the newest one not closed, with its link"() {
        given:
        adapter.track(trackedTicket(key: 'SCP-1', createdAt: CREATED.minusSeconds(60), closedAt: CREATED))
        adapter.track(trackedTicket(key: 'SCP-2', createdAt: CREATED))
        adapter.track(trackedTicket(key: 'SCP-3', createdAt: CREATED.plusSeconds(60)))
        adapter.track(trackedTicket(key: 'SCP-4', createdAt: CREATED.plusSeconds(120), changeNumber: 'CHG0031002'))

        expect:
        adapter.openFor('CHG0031001') == Optional.of(trackedTicket(key: 'SCP-3', createdAt: CREATED.plusSeconds(60)))
        adapter.openFor('CHG0031002').get().key() == 'SCP-4'
        adapter.openFor('CHG0031003') == Optional.empty()
        jdbc.queryForMap("SELECT CHANGE_NUMBER, TICKET_URL FROM BEADLE_INTERNAL_CYBERTRACK_TICKET WHERE TICKET_KEY = 'SCP-3'") ==
                [CHANGE_NUMBER: 'CHG0031001', TICKET_URL: 'https://cybertrack.bbh.com/browse/SCP-1234']
    }

    def "a change whose open ticket cannot be looked up has none"() {
        given:
        jdbc.execute('ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET RENAME COLUMN CHANGE_NUMBER TO CHANGE_NO')

        expect:
        adapter.openFor('CHG0031001') == Optional.empty()

        cleanup:
        jdbc.execute('ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET RENAME COLUMN CHANGE_NO TO CHANGE_NUMBER')
    }

    def "a followed ticket is found by its key, open or closed"() {
        given:
        adapter.track(trackedTicket(key: 'SCP-1', closedAt: CREATED))
        adapter.track(trackedTicket(key: 'SCP-2'))

        expect:
        adapter.find('SCP-1') == Optional.of(trackedTicket(key: 'SCP-1', closedAt: CREATED))
        adapter.find('SCP-2') == Optional.of(trackedTicket(key: 'SCP-2'))
        adapter.find('SCP-3') == Optional.empty()
    }

    def "the highest key of a project is the one with the highest number, open or closed"() {
        given:
        keys.each { adapter.track(trackedTicket(key: it, closedAt: it == 'SCP-1000' ? CREATED : null)) }

        expect:
        adapter.highestKey(prefix) == Optional.ofNullable(highest)

        where:
        keys                                   | prefix  || highest
        ['SCP-999', 'SCP-1000', 'SCP-12']      | 'SCP-'  || 'SCP-1000'
        ['SCP-7', 'SCPX-99']                   | 'SCP-'  || 'SCP-7'
        ['SECX1-9', 'SEC_1-5']                 | 'SEC_1-' || 'SEC_1-5'
        ['SECX1-9']                            | 'SEC_1-' || null
        ['50%-1', '50X-9']                     | '50%-'  || '50%-1'
        []                                     | 'SCP-'  || null
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
