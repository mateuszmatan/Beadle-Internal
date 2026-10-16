package com.bbh.itss.beadle.internal.adapter.out.persistence

import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.QTEST
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:call-log-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(CallLogPersistenceAdapter)
class CallLogPersistenceAdapterSpec extends Specification {

    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')

    @Subject
    @Autowired
    CallLogPersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    PlatformTransactionManager transactionManager

    def setup() {
        jdbc.update('DELETE FROM BEADLE_INTERNAL_CALL_LOG')
    }

    def "an entry is written with a generated id and read back as it was"() {
        when:
        def recorded = adapter.record(callLogEntry(id: null))
        def failed = adapter.record(callLogEntry(id: null, system: CONFLUENCE, operation: 'update', target: '100001 Notes',
                outcome: FAILURE, message: 'Confluence could not update the page 100001: 409', durationMs: 345L))

        then:
        recorded == callLogEntry(id: recorded.id())
        recorded.id() != null
        failed.id() > recorded.id()
        adapter.find(CallLogQuery.of(null, null, null, 0, 10)).entries() == [failed, recorded]
        jdbc.queryForMap('SELECT SYSTEM, OPERATION, OUTCOME, MESSAGE, DURATION_MS FROM BEADLE_INTERNAL_CALL_LOG WHERE ID = ?', failed.id()) ==
                [SYSTEM: 'CONFLUENCE', OPERATION: 'update', OUTCOME: 'FAILURE',
                 MESSAGE: 'Confluence could not update the page 100001: 409', DURATION_MS: 345]
    }

    def "the entries are found by correlation id and time window, newest first, page by page"() {
        given:
        def entries = (0..<5).collect { index ->
            adapter.record(callLogEntry(id: null, at: NOON.plusSeconds(index * 60), correlationId: index % 2 == 0 ? 'even' : 'odd',
                    system: index == 4 ? QTEST : callLogEntry().system(), operation: "call-$index"))
        }

        expect:
        adapter.find(CallLogQuery.of(correlationId, from, to, page, size)) ==
                new com.bbh.itss.beadle.internal.domain.calllog.CallLogPage(page, size, total, expected.collect { entries[it] })

        where:
        correlationId | from                   | to                     | page | size || total | expected
        null          | null                   | null                   | 0    | 10   || 5     | [4, 3, 2, 1, 0]
        null          | null                   | null                   | 1    | 2    || 5     | [2, 1]
        null          | null                   | null                   | 3    | 2    || 5     | []
        'even'        | null                   | null                   | 0    | 10   || 3     | [4, 2, 0]
        ' odd '       | null                   | null                   | 0    | 10   || 2     | [3, 1]
        null          | NOON.plusSeconds(60)   | NOON.plusSeconds(180)  | 0    | 10   || 2     | [2, 1]
        'even'        | NOON.plusSeconds(60)   | null                   | 0    | 10   || 2     | [4, 2]
        null          | null                   | NOON                   | 0    | 10   || 0     | []
    }

    def "an entry is kept even when the transaction around it rolls back"() {
        when:
        new TransactionTemplate(transactionManager).execute { status ->
            adapter.record(callLogEntry(id: null))
            status.setRollbackOnly()
        }

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_INTERNAL_CALL_LOG', Integer) == 1
    }
}
