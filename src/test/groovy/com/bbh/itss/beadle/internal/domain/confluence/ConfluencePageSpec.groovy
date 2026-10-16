package com.bbh.itss.beadle.internal.domain.confluence

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate.VERSION_MESSAGE
import static com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage.REQUIRED
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate

class ConfluencePageSpec extends Specification {

    def "a new page needs a space, a title of at most 255 bytes and a body; the parent is optional and trimmed"() {
        when:
        def page = NewConfluencePage.of(' DSO ', ' Change CHG0031001 ', ' ', '<p>Evidence</p>')

        then:
        page == new NewConfluencePage('DSO', 'Change CHG0031001', null, '<p>Evidence</p>')
        NewConfluencePage.of('DSO', 'Notes', ' 100001 ', '<p/>').parentId() == '100001'
    }

    def "a new page with #problem is refused"() {
        when:
        NewConfluencePage.of(spaceKey, title, null, body)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        problem               | spaceKey | title     | body    || fields                     | messages
        'no space'            | ' '      | 'Notes'   | '<p/>'  || ['spaceKey']               | [REQUIRED]
        'no title'            | 'DSO'    | null      | '<p/>'  || ['title']                  | [REQUIRED]
        'a title of 256 bytes' | 'DSO'   | 'ł' * 128 | '<p/>'  || ['title']                  | ['is too long: it may take at most 255 bytes']
        'a blank body'        | 'DSO'    | 'Notes'   | '  '    || ['body']                   | [REQUIRED]
        'nothing'             | null     | null      | null    || ['spaceKey', 'title', 'body'] | [REQUIRED, REQUIRED, REQUIRED]
    }

    def "an update names the version it replaces and gives the page the next one"() {
        given:
        def update = ConfluencePageUpdate.of(' Beadle release notes ', '<p>Beadle 1.1</p>', 3)

        expect:
        update == pageUpdate()
        update.replaces(page(version: 3))
        !update.replaces(page(version: 4))
        page().replacedBy(update) == page(version: 4, body: '<p>Beadle 1.1</p>')
        page().withoutBody() == page(body: null)
    }

    def "an update with #problem is refused"() {
        when:
        ConfluencePageUpdate.of(title, body, version)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        problem         | title   | body   | version || fields               | messages
        'no title'      | ' '     | '<p/>' | 3       || ['title']            | [REQUIRED]
        'no body'       | 'Notes' | null   | 3       || ['body']             | [REQUIRED]
        'no version'    | 'Notes' | '<p/>' | null    || ['version']          | [REQUIRED]
        'version 0'     | 'Notes' | '<p/>' | 0       || ['version']          | [VERSION_MESSAGE]
        'nothing at all' | null   | ' '    | -1      || ['title', 'body', 'version'] | [REQUIRED, REQUIRED, VERSION_MESSAGE]
    }
}
