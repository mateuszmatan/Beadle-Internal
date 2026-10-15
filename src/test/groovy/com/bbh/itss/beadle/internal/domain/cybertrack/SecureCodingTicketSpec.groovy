package com.bbh.itss.beadle.internal.domain.cybertrack

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding.LINK_MESSAGE
import static com.bbh.itss.beadle.internal.support.Fixtures.IMPLEMENTATION_DATE
import static com.bbh.itss.beadle.internal.support.Fixtures.secureCoding

class SecureCodingTicketSpec extends Specification {

    def "a ticket is named APO-ID_APP-NAME-IMPLEMENTATION-DATE and describes every input and the change"() {
        when:
        def ticket = SecureCodingTicket.of(' CHG0031001 ', ' CertScanner ', IMPLEMENTATION_DATE, secureCoding())

        then:
        ticket == new SecureCodingTicket('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE, secureCoding())
        ticket.summary() == 'APO-12345_CertScanner-10152026'
        ticket.description() == '''\
            APO number: APO-12345
            Application: CertScanner
            Implementation date: 10152026
            Bitbucket URL (SAST scan): https://bitbucket.bbh.com/projects/CERT/repos/cert
            Artifact link (OSA - Nexus IQ scan): https://jenkins.bbh.com/job/CERT/job/cert-release/
            QC application link (DAST scan): https://cert.qc.bbh.com
            ProTech change: CHG0031001'''.stripIndent()
    }

    def "a ticket name is cut to what CyberTrack takes"() {
        expect:
        SecureCodingTicket.of('CHG0031001', 'ł' * 120, IMPLEMENTATION_DATE, secureCoding()).summary()
                .getBytes('UTF-8').length <= SecureCodingTicket.SUMMARY_MAX
    }

    def "a ticket #refusal is refused"() {
        when:
        SecureCodingTicket.of(changeNumber, application, date, secureCoding())

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem(field, message)]

        where:
        refusal                       | changeNumber | application   | date                 || field                | message
        'without a change number'     | ' '          | 'CertScanner' | IMPLEMENTATION_DATE  || 'changeNumber'       | 'is required'
        'with a change number too long' | 'C' * 41   | 'CertScanner' | IMPLEMENTATION_DATE  || 'changeNumber'       | 'is too long: it may take at most 40 bytes'
        'without an application'      | 'CHG0031001' | null          | IMPLEMENTATION_DATE  || 'applicationName'    | 'is required'
        'with an application too long' | 'CHG0031001' | 'ł' * 128    | IMPLEMENTATION_DATE  || 'applicationName'    | 'is too long: it may take at most 255 bytes'
        'without a date'              | 'CHG0031001' | 'CertScanner' | null                 || 'implementationDate' | 'is required'
    }

    def "every input of a ticket is required and every link must be one"() {
        when:
        SecureCodingTicket.of('CHG0031001', 'CertScanner', LocalDate.parse('2026-10-20'),
                new SecureCoding(' ', 'https://bitbucket', null, 'cert.qc.bbh.com'))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('inputs.apoNumber', 'is required'),
                               new FieldProblem('inputs.artifactLink', 'is required'),
                               new FieldProblem('inputs.qcApplicationLink', LINK_MESSAGE)]
    }

    def "a ticket without inputs names every missing one"() {
        when:
        SecureCodingTicket.of('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE, null)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field == ['inputs.apoNumber', 'inputs.bitbucketUrl', 'inputs.artifactLink',
                                      'inputs.qcApplicationLink']
        refused.problems()*.message.toSet() == ['is required'] as Set
    }
}
