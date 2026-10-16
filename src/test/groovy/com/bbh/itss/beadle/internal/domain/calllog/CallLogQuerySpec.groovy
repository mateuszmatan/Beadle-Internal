package com.bbh.itss.beadle.internal.domain.calllog

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.PAGE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.RANGE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.SIZE_MESSAGE

class CallLogQuerySpec extends Specification {

    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')

    def "a query trims the correlation id and takes the first page of 50 entries by default"() {
        expect:
        CallLogQuery.of(' corr-1 ', null, null, null, null) == new CallLogQuery('corr-1', null, null, 0, 50)
        CallLogQuery.of(' ', NOON, NOON, 2, 500) == new CallLogQuery(null, NOON, NOON, 2, 500)
    }

    def "a query with #problem is refused"() {
        when:
        CallLogQuery.of(null, from, to, page, size)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        problem               | from                 | to   | page | size || fields           | messages
        'a negative page'     | null                 | null | -1   | 10   || ['page']         | [PAGE_MESSAGE]
        'no entries per page' | null                 | null | 0    | 0    || ['size']         | [SIZE_MESSAGE]
        'too many per page'   | null                 | null | 0    | 501  || ['size']         | [SIZE_MESSAGE]
        'from after to'       | NOON.plusSeconds(1)  | NOON | 0    | 10   || ['from']         | [RANGE_MESSAGE]
        'everything wrong'    | NOON.plusSeconds(1)  | NOON | -2   | 0    || ['page', 'size', 'from'] | [PAGE_MESSAGE, SIZE_MESSAGE, RANGE_MESSAGE]
    }
}
