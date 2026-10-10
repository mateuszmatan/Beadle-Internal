package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.SecureCodingTicket.DATE_MESSAGE
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCoding

class SecureCodingTicketSpec extends Specification {

    def "a ticket is named APO-ID_APP-NAME-IMPLEMENTATION-DATE and describes every input and the change"() {
        when:
        def ticket = SecureCodingTicket.of(raised(), secureCoding(), ' 10152026 ')

        then:
        ticket == new SecureCodingTicket('CHG0031001', 'CertScanner', '10152026', secureCoding())
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

    def "a ticket name is cut to what Jira takes"() {
        expect:
        SecureCodingTicket.of(raised(productName: 'ł' * 200), secureCoding(), '10152026').summary()
                .getBytes('UTF-8').length <= SecureCodingTicket.SUMMARY_MAX
    }

    def "the implementation date #date is refused"() {
        when:
        SecureCodingTicket.of(raised(), secureCoding(), date)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('implementationDate', message)]

        where:
        date         || message
        null         || 'is required'
        ' '          || 'is required'
        '2026-10-15' || DATE_MESSAGE
        '15102026'   || DATE_MESSAGE
        '02302026'   || DATE_MESSAGE
        '1015202'    || DATE_MESSAGE
        '101520260'  || DATE_MESSAGE
        '+1015202'   || DATE_MESSAGE
    }

    def "every input of a ticket is required and every link must be one"() {
        when:
        SecureCodingTicket.of(raised(), new SecureCoding(' ', 'https://bitbucket', null, 'cert.qc.bbh.com'), '10152026')

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('apoNumber', 'is required'),
                               new FieldProblem('artifactLink', 'is required'),
                               new FieldProblem('qcApplicationLink', SecureCoding.LINK_MESSAGE)]
    }
}
