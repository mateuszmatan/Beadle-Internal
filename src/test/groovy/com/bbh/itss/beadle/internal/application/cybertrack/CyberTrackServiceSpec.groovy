package com.bbh.itss.beadle.internal.application.cybertrack

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket

class CyberTrackServiceSpec extends Specification {

    CyberTrackPort cyberTrack = Mock()
    def service = new CyberTrackService(cyberTrack)

    def "a secure coding ticket is created in CyberTrack as it is"() {
        given:
        def created = new CreatedTicket('SCP-1234', null)

        when:
        def result = service.create(ticket())

        then:
        1 * cyberTrack.create(ticket()) >> created
        result == created
    }

    def "a ticket is read by its trimmed key"() {
        given:
        def found = cyberTrackTicket()

        when:
        def result = service.read(' SCP-1234 ')

        then:
        1 * cyberTrack.read('SCP-1234') >> found
        result == found
    }

    def "a ticket without a key is not found without asking CyberTrack"() {
        when:
        service.read(key)

        then:
        thrown(NoSuchElementException)
        0 * cyberTrack._

        where:
        key << [null, '', '  ']
    }
}
