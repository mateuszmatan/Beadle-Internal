package com.bbh.dso.portal.common

import spock.lang.Specification

class DelimitedListConverterSpec extends Specification {

    def "values are stored one per line, cleaned of blanks and duplicates"() {
        given:
        def converter = new DelimitedListConverter.Lines()

        expect:
        converter.convertToDatabaseColumn(['**/*.jar', ' **/*.war ', '', null, '**/*.jar']) == '**/*.jar\n**/*.war'
        converter.convertToEntityAttribute('**/*.jar\n **/*.war\n\n**/*.jar') == ['**/*.jar', '**/*.war']
    }

    def "agent labels are stored comma separated"() {
        given:
        def converter = new DelimitedListConverter.Commas()

        expect:
        converter.convertToDatabaseColumn(['linux', ' docker ']) == 'linux,docker'
        converter.convertToEntityAttribute('linux, docker,,') == ['linux', 'docker']
    }

    def "an empty list is stored as null and read back empty: #values"() {
        given:
        def converter = new DelimitedListConverter.Commas()

        expect:
        converter.convertToDatabaseColumn(values) == null
        converter.convertToEntityAttribute(column) == []

        where:
        values      | column
        null        | null
        []          | ''
        ['', '  ']  | '  '
    }

    def "clean keeps the order of the first occurrence"() {
        expect:
        DelimitedListConverter.clean(['b', 'a', ' b']) == ['b', 'a']
        DelimitedListConverter.clean(null) == []
    }
}
