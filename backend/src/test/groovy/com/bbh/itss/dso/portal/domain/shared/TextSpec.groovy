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

    def "clean keeps the order of the first occurrence"() {
        expect:
        Text.clean(['b', 'a', ' b']) == ['b', 'a']
        Text.clean(null) == []
    }

    def "trimmed drops blanks but keeps the order and repeated values"() {
        expect:
        Text.trimmed([' -s ', 'settings.xml', '', null, ' ', '-gs', 'settings.xml ']) ==
                ['-s', 'settings.xml', '-gs', 'settings.xml']
        Text.trimmed(null) == []
        Text.trimmed([]) == []
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
