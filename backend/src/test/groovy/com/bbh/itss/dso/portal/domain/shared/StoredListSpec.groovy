package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

class StoredListSpec extends Specification {

    def "a list fits its column when its entries joined by the separator take at most the column's bytes"() {
        expect:
        new StoredList(separator, 10).fits(values) == fits

        where:
        separator | values                     || fits
        '\n'      | []                         || true
        '\n'      | ['12345', '1234']          || true
        '\n'      | ['12345', '12345']         || false
        ','       | ['abc', 'def', 'gh']       || true
        ','       | ['\u017c\u00f3\u0142\u0107', 'a']  || true
        ','       | ['\u017c\u00f3\u0142\u0107', 'ab'] || false
    }

    def "an entry list longer than its column is reported against its field"() {
        given:
        def problems = new ValidationProblems()

        when:
        StoredList.LINES_1000.check(problems.at('flutter'), 'modules', ['m' * 600, 'n' * 400])
        StoredList.COMMAS_1000.check(problems, 'agentLabels', ['a' * 500, 'b' * 499])

        then:
        problems.list()*.field == ['flutter.modules']
        problems.list()*.message == ['is too long: all entries together may take at most 1000 bytes']
    }

    def "the stored lists use the separators and sizes of their columns"() {
        expect:
        [StoredList.LINES_1000, StoredList.LINES_2000, StoredList.LINES_4000, StoredList.COMMAS_1000,
         StoredList.COMMAS_2000].collect { [it.separator(), it.maxBytes()] } ==
                [['\n', 1000], ['\n', 2000], ['\n', 4000], [',', 1000], [',', 2000]]
    }
}
