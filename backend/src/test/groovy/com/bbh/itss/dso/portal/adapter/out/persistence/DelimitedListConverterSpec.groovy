package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

class DelimitedListConverterSpec extends Specification {

    static final DelimitedListConverter LINES = new DelimitedListConverter.Lines()
    static final DelimitedListConverter COMMAS = new DelimitedListConverter.Commas()
    static final DelimitedListConverter TOKENS = new DelimitedListConverter.Tokens()
    static final DelimitedListConverter PIPES = new DelimitedListConverter('|') {}
    static final DelimitedListConverter DOTS = new DelimitedListConverter('.', false) {}

    def "#converter.class.simpleName stores #values as #column"() {
        expect:
        converter.convertToDatabaseColumn(values) == column

        where:
        converter | values                                                   || column
        LINES     | ['**/*.jar', ' **/*.war ', '', null, '**/*.jar']         || '**/*.jar\n**/*.war'
        LINES     | ['src/**/*.java,src/**/*.kt', 'docs']                    || 'src/**/*.java,src/**/*.kt\ndocs'
        COMMAS    | ['linux', ' docker ', 'linux']                           || 'linux,docker'
        COMMAS    | null                                                     || null
        COMMAS    | []                                                       || null
        COMMAS    | ['', '  ']                                               || null
        TOKENS    | [' -s ', 'settings.xml', '', null, '-gs', 'settings.xml'] || '-s\nsettings.xml\n-gs\nsettings.xml'
        TOKENS    | ['', ' ', null]                                          || null
        TOKENS    | null                                                     || null
        PIPES     | ['a', 'b', 'a']                                          || 'a|b'
    }

    def "#converter.class.simpleName reads #column as #values"() {
        expect:
        converter.convertToEntityAttribute(column) == values

        where:
        converter | column                                     || values
        LINES     | '**/*.jar\n **/*.war\n\n**/*.jar'          || ['**/*.jar', '**/*.war']
        LINES     | 'src/**/*.java,src/**/*.kt\ndocs'          || ['src/**/*.java,src/**/*.kt', 'docs']
        COMMAS    | 'linux, docker,,'                          || ['linux', 'docker']
        COMMAS    | 'linux\ndocker,arm'                        || ['linux\ndocker', 'arm']
        COMMAS    | null                                       || []
        COMMAS    | ''                                         || []
        COMMAS    | '  '                                       || []
        TOKENS    | '-s\n settings.xml \n\n-gs\nsettings.xml'  || ['-s', 'settings.xml', '-gs', 'settings.xml']
        TOKENS    | '-Dpatterns=a,b\n-q'                       || ['-Dpatterns=a,b', '-q']
        TOKENS    | null                                       || []
        TOKENS    | ' \n '                                     || []
        PIPES     | 'a|b|a'                                    || ['a', 'b']
        DOTS      | 'a.b.a'                                    || ['a', 'b', 'a']
    }
}
