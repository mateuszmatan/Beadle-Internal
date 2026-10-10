package com.bbh.itss.dso.portal.adapter

import com.bbh.itss.dso.portal.adapter.Samples.Holder
import com.bbh.itss.dso.portal.adapter.Samples.Person
import com.bbh.itss.dso.portal.adapter.Samples.PersonRequest
import com.bbh.itss.dso.portal.adapter.Samples.PersonView
import com.bbh.itss.dso.portal.adapter.Samples.Team
import com.bbh.itss.dso.portal.adapter.Samples.TeamView
import spock.lang.Specification

import static java.time.DayOfWeek.FRIDAY
import static java.time.DayOfWeek.MONDAY

class RecordMapperSharedSpec extends Specification {

    def "a record is copied into another record by component name, with nested records, lists and maps converted"() {
        given:
        def source = new Team('Core', new Person(' Ada ', 36), [new Person('Bob', 41)], [(FRIDAY): new Person('Eve', 29)])

        when:
        def copy = RecordMapper.map(source, TeamView)

        then:
        copy == new TeamView('Core', new PersonView('Ada'), [new PersonView('Bob')], [(FRIDAY): new PersonView('Eve')])
        copy.rota() instanceof EnumMap
        RecordMapper.map(null, TeamView) == null
        RecordMapper.map('plain text', String) == 'plain text'
    }

    def "a record is built from the first source that has each component, as a method or as a field"() {
        expect:
        RecordMapper.map(PersonView, new Person('Ada', 36), new Holder('Grace')) == new PersonView('Ada')
        RecordMapper.map(PersonView, new Object(), new Holder('Grace')) == new PersonView('Grace')
        RecordMapper.map(PersonView, null, new Holder(null)) == new PersonView(null)
    }

    def "a record that mirrors another one passes through it, so the mirrored rules apply"() {
        expect:
        RecordMapper.map(new PersonRequest(' Ada ', 36), PersonRequest) == new PersonRequest('Ada', 36)
    }

    def "a refusal of the target record is passed on unchanged"() {
        when:
        RecordMapper.map(new Person('Ada', -1), Person)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'age must not be negative'
    }
}
