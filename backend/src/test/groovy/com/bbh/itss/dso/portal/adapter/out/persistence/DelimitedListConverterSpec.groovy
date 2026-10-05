package com.bbh.itss.dso.portal.adapter.out.persistence

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

    def "command line tokens are stored one per line, keeping repeated tokens in their order"() {
        given:
        def converter = new DelimitedListConverter.Tokens()

        expect:
        converter.convertToDatabaseColumn([' -s ', 'settings.xml', '', null, '-gs', 'settings.xml']) ==
                '-s\nsettings.xml\n-gs\nsettings.xml'
        converter.convertToEntityAttribute('-s\n settings.xml \n\n-gs\nsettings.xml') == ['-s', 'settings.xml', '-gs', 'settings.xml']
    }

    def "tokens that are all blank are stored as null and read back empty"() {
        given:
        def converter = new DelimitedListConverter.Tokens()

        expect:
        converter.convertToDatabaseColumn(['', ' ', null]) == null
        converter.convertToDatabaseColumn(null) == null
        converter.convertToEntityAttribute(null) == []
        converter.convertToEntityAttribute(' \n ') == []
    }

    def "only a converter that keeps distinct values drops a repeated value: #converter.class.simpleName"() {
        expect:
        converter.convertToEntityAttribute(converter.convertToDatabaseColumn(['clean', 'build', 'clean'])) == values

        where:
        converter                              || values
        new DelimitedListConverter.Tokens()    || ['clean', 'build', 'clean']
        new DelimitedListConverter.Lines()     || ['clean', 'build']
        new DelimitedListConverter.Commas()    || ['clean', 'build']
    }

    def "a converter splits on its delimiter only, so a value may contain the other one"() {
        expect:
        new DelimitedListConverter.Lines().convertToDatabaseColumn(['src/**/*.java,src/**/*.kt', 'docs']) ==
                'src/**/*.java,src/**/*.kt\ndocs'
        new DelimitedListConverter.Lines().convertToEntityAttribute('src/**/*.java,src/**/*.kt\ndocs') ==
                ['src/**/*.java,src/**/*.kt', 'docs']
        new DelimitedListConverter.Tokens().convertToEntityAttribute('-Dpatterns=a,b\n-q') == ['-Dpatterns=a,b', '-q']
        new DelimitedListConverter.Commas().convertToEntityAttribute('linux\ndocker,arm') == ['linux\ndocker', 'arm']
    }

    def "a delimiter is matched literally, not as a pattern"() {
        given:
        def pipes = new DelimitedListConverter('|') {}
        def dots = new DelimitedListConverter('.', false) {}

        expect:
        pipes.convertToDatabaseColumn(['a', 'b', 'a']) == 'a|b'
        pipes.convertToEntityAttribute('a|b|a') == ['a', 'b']
        dots.convertToEntityAttribute('a.b.a') == ['a', 'b', 'a']
    }
}
