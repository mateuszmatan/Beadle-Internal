package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

class TextSpec extends Specification {

    def "blank text is #description"() {
        expect:
        Text.isBlank(value) == blank
        Text.trimToNull(value) == trimmed
        Text.orDefault(value, 'fallback') == (trimmed ?: 'fallback')

        where:
        description           | value     || blank | trimmed
        'null'                | null      || true  | null
        'empty'               | ''        || true  | null
        'whitespace'          | '  \t'    || true  | null
        'text with padding'   | ' main '  || false | 'main'
        'text without spaces' | 'develop' || false | 'develop'
    }

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
        Text.abbreviate(null, 5) == null
        Text.abbreviate('abcdef', 6) == 'abcdef'
        Text.abbreviate('abc  defgh', 8) == 'abc...'
        Text.abbreviate('ąąąąą', 9) == 'ąąą...'
        Text.abbreviate('ab😀cd', 7) == 'ab...'
        Text.bytes(Text.abbreviate('é' * 300, 160)) <= 160
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
