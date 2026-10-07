package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

class TextSpec extends Specification {

    def "clean keeps the first occurrence of each value, trimmed keeps repeated values, both in their order"() {
        expect:
        Text.clean(['b', 'a', ' b']) == ['b', 'a']
        Text.trimmed([' -s ', 'settings.xml', '', null, ' ', '-gs', 'settings.xml ']) ==
                ['-s', 'settings.xml', '-gs', 'settings.xml']
        [Text.clean(null), Text.trimmed(null), Text.trimmed([])] == [[], [], []]
    }

    def "abbreviate cuts a text to the UTF-8 bytes of an Oracle column on a character boundary"() {
        expect:
        Text.bytes('zażółć') == 10
        Text.abbreviateBytes(null, 5) == null
        Text.abbreviateBytes('abcdef', 6) == 'abcdef'
        Text.abbreviateBytes('abc  defgh', 8) == 'abc...'
        Text.abbreviateBytes('ąąąąą', 9) == 'ąąą...'
        Text.abbreviateBytes('ab😀cd', 7) == 'ab...'
        Text.bytes(Text.abbreviateBytes('é' * 300, 160)) <= 160
    }

    def "the cleaned and trimmed lists cannot be changed"() {
        when:
        Text."$method"(['a']).add('b')

        then:
        thrown(UnsupportedOperationException)

        where:
        method << ['clean', 'trimmed']
    }
}
