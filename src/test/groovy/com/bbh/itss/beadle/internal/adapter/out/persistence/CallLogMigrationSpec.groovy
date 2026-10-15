package com.bbh.itss.beadle.internal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:file:./build/call-log-migration/db;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class CallLogMigrationSpec extends MigrationSpecification {

    static final String TABLE = 'BEADLE_INTERNAL_CALL_LOG'
    static final List<String> COLUMNS = ['ID', 'AT', 'USER_NAME', 'CORRELATION_ID', 'SYSTEM', 'OPERATION', 'TARGET',
                                         'OUTCOME', 'MESSAGE', 'DURATION_MS']
    static final String INSERT = """INSERT INTO $TABLE (AT, USER_NAME, CORRELATION_ID, SYSTEM, OPERATION, TARGET, OUTCOME,
            MESSAGE, DURATION_MS) VALUES (CURRENT_TIMESTAMP, 'Mateusz Matan', 'corr-1', ?, 'versions', 'CERT', ?, NULL, 12)"""

    def setup() {
        jdbc.execute('DROP ALL OBJECTS')
    }

    def "001 creates the call log with its columns, generated ids and indexes, and rolls back cleanly"() {
        when:
        liquibase.update('')

        then:
        TABLE in tables()
        columns(TABLE) == COLUMNS
        ['USER_NAME', 'CORRELATION_ID', 'TARGET', 'MESSAGE'].every { nullable(TABLE, it) }
        ['AT', 'SYSTEM', 'OPERATION', 'OUTCOME', 'DURATION_MS'].every { !nullable(TABLE, it) }
        indexes(TABLE).containsAll(['IX_BEADLE_INTERNAL_CALL_LOG_AT', 'IX_BEADLE_INTERNAL_CALL_LOG_CORRELATION'])

        when:
        jdbc.update(INSERT, 'JIRA', 'SUCCESS')
        jdbc.update(INSERT, 'GUI', 'FAILURE')

        then:
        jdbc.queryForList("SELECT ID FROM $TABLE ORDER BY ID", Long) == [1L, 2L]

        when:
        rollBackSince('001-')

        then:
        !(TABLE in tables())

        when:
        liquibase.update('')

        then:
        TABLE in tables()
        jdbc.queryForObject("SELECT COUNT(*) FROM $TABLE", Integer) == 0
    }

    def "a row with #problem is refused by the table"() {
        given:
        liquibase.update('')

        when:
        jdbc.update(INSERT, system, outcome)

        then:
        thrown(DataIntegrityViolationException)

        where:
        problem             | system    | outcome
        'an unknown system' | 'PROTECH' | 'SUCCESS'
        'an unknown outcome' | 'JIRA'   | 'PARTIAL'
        'no system'         | null      | 'SUCCESS'
    }

    private List<String> indexes(String table) {
        jdbc.queryForList('SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ?', String, table)
    }
}
