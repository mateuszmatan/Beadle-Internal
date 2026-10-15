package com.bbh.itss.beadle.internal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:file:./build/cybertrack-ticket-migration/db;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class CyberTrackTicketMigrationSpec extends MigrationSpecification {

    static final String TABLE = 'BEADLE_INTERNAL_CYBERTRACK_TICKET'
    static final List<String> COLUMNS_002 = ['TICKET_KEY', 'STATUS', 'CORRELATION_ID', 'CREATED_AT', 'LAST_SEEN_AT',
                                             'LAST_EVENT_AT', 'CLOSED_AT']
    static final List<String> COLUMNS_003 = COLUMNS_002 + ['DEMO']
    static final List<String> COLUMNS = COLUMNS_003 + ['MISSES']
    static final String INSERT = "INSERT INTO $TABLE (TICKET_KEY, STATUS, CORRELATION_ID, CREATED_AT) VALUES (?, 'To Do', 'corr-1', ?)"

    def setup() {
        jdbc.execute('DROP ALL OBJECTS')
    }

    def "002 creates the followed CyberTrack tickets on top of the call log, and rolls back to the call log alone"() {
        when:
        liquibase.update('')

        then:
        ['BEADLE_INTERNAL_CALL_LOG', TABLE].every { it in tables() }
        columns(TABLE) == COLUMNS
        ['STATUS', 'CORRELATION_ID', 'LAST_SEEN_AT', 'LAST_EVENT_AT', 'CLOSED_AT'].every { nullable(TABLE, it) }
        ['TICKET_KEY', 'CREATED_AT'].every { !nullable(TABLE, it) }
        'IX_BEADLE_INTERNAL_CYBERTRACK_TICKET_CLOSED' in indexes(TABLE)

        when:
        jdbc.update(INSERT, 'SCP-1234', new java.sql.Timestamp(0))

        then:
        jdbc.queryForObject("SELECT STATUS FROM $TABLE WHERE TICKET_KEY = 'SCP-1234'", String) == 'To Do'

        when:
        rollBackSince('002-')

        then:
        !(TABLE in tables())
        'BEADLE_INTERNAL_CALL_LOG' in tables()

        when:
        liquibase.update('')

        then:
        TABLE in tables()
        jdbc.queryForObject("SELECT COUNT(*) FROM $TABLE", Integer) == 0
    }

    def "003 marks the tickets the demo made up, a ticket followed before it being a real one, and rolls back to 002"() {
        given:
        liquibase.update('')
        rollBackSince('003-')
        jdbc.update(INSERT, 'SCP-1234', new java.sql.Timestamp(0))

        when:
        liquibase.update('')

        then:
        columns(TABLE).take(COLUMNS_003.size()) == COLUMNS_003
        !nullable(TABLE, 'DEMO')
        jdbc.queryForObject("SELECT DEMO FROM $TABLE WHERE TICKET_KEY = 'SCP-1234'", Integer) == 0

        when:
        rollBackSince('003-')

        then:
        columns(TABLE) == COLUMNS_002
        jdbc.queryForObject("SELECT STATUS FROM $TABLE WHERE TICKET_KEY = 'SCP-1234'", String) == 'To Do'
    }

    def "004 counts the polls that missed a ticket, none for a ticket followed before it, and rolls back to 003"() {
        given:
        liquibase.update('')
        rollBackSince('004-')
        jdbc.update(INSERT, 'SCP-1234', new java.sql.Timestamp(0))

        when:
        liquibase.update('')

        then:
        columns(TABLE).take(COLUMNS.size()) == COLUMNS
        !nullable(TABLE, 'MISSES')
        jdbc.queryForObject("SELECT MISSES FROM $TABLE WHERE TICKET_KEY = 'SCP-1234'", Integer) == 0

        when:
        rollBackSince('004-')

        then:
        columns(TABLE) == COLUMNS_003
    }

    def "a ticket is followed once and always has a key and a creation time"() {
        given:
        liquibase.update('')
        jdbc.update(INSERT, 'SCP-1234', new java.sql.Timestamp(0))

        when:
        jdbc.update(INSERT, key, created)

        then:
        thrown(DataIntegrityViolationException)

        where:
        key        | created
        'SCP-1234' | new java.sql.Timestamp(0)
        null       | new java.sql.Timestamp(0)
        'SCP-1235' | null
    }

    private List<String> indexes(String table) {
        jdbc.queryForList('SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ?', String, table)
    }
}
