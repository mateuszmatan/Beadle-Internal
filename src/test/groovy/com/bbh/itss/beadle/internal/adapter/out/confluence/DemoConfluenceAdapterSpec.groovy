package com.bbh.itss.beadle.internal.adapter.out.confluence

import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.adapter.out.confluence.DemoConfluenceAdapter.DUPLICATE
import static com.bbh.itss.beadle.internal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate

class DemoConfluenceAdapterSpec extends Specification {

    def confluence = new DemoConfluenceAdapter()

    def "the demo starts with a few pages, found by space and title whatever the case, without their bodies"() {
        when:
        def found = confluence.find('dso', 'beadle RELEASE notes')

        then:
        !confluence.connected()
        found.size() == 1
        with(found[0]) {
            id() ==~ /\d{6}/
            spaceKey() == 'DSO'
            title() == 'Beadle release notes'
            version() == 3
            url() == null
            body() == null
        }
        confluence.get(found[0].id()).body().contains('Beadle 1.0')
        confluence.find('DSO', 'Nope') == []
        confluence.find('CERT', 'CertScanner 1.4 release')*.version() == [2]
    }

    def "a created page gets a new id and version 1 and is found afterwards"() {
        when:
        def created = confluence.create(newPage(spaceKey: 'dso'))

        then:
        created == new ConfluencePage(created.id(), 'DSO', 'Change CHG0031001', 1, null, '<p>Evidence</p>')
        created.id() ==~ /\d{6}/
        confluence.get(created.id()) == created
        confluence.find('DSO', 'Change CHG0031001') == [created.withoutBody()]
    }

    def "an update replaces the named version with the next one and keeps the id and space"() {
        given:
        def page = confluence.find('DSO', 'Beadle release notes')[0]

        when:
        def updated = confluence.update(page.id(), pageUpdate(title: 'Beadle release notes 1.1'))

        then:
        updated == new ConfluencePage(page.id(), 'DSO', 'Beadle release notes 1.1', 4, null, '<p>Beadle 1.1</p>')
        confluence.get(page.id()) == updated
        confluence.find('DSO', 'Beadle release notes') == []
    }

    def "an update of another version than the current one is refused as stale"() {
        given:
        def page = confluence.find('DSO', 'Beadle release notes')[0]

        when:
        confluence.update(page.id(), pageUpdate(version: 2))

        then:
        def refused = thrown(IllegalStateException)
        refused.message == STALE_VERSION
        confluence.get(page.id()).version() == 3
    }

    def "a second page with the title of another one in the same space is refused, on creation and on renaming"() {
        given:
        def created = confluence.create(newPage(title: 'Evidence of CHG0031001'))

        when:
        confluence.create(newPage(title: ' beadle release notes '))

        then:
        def creation = thrown(IllegalStateException)
        creation.message == DUPLICATE + 'beadle release notes in space DSO'

        when:
        confluence.update(created.id(), pageUpdate(title: 'Beadle release notes', version: 1))

        then:
        def renaming = thrown(IllegalStateException)
        renaming.message == DUPLICATE + 'Beadle release notes in space DSO'
        confluence.get(created.id()).version() == 1

        and: 'the same title in another space is fine'
        confluence.create(newPage(spaceKey: 'CERT', title: 'Beadle release notes')).spaceKey() == 'CERT'
    }

    def "an unknown page is not found"() {
        when:
        confluence.get('1')

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'Confluence page 1 does not exist'

        when:
        confluence.update('1', pageUpdate())

        then:
        thrown(NoSuchElementException)
    }
}
