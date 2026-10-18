package com.bbh.itss.beadle.internal.support

import com.bbh.itss.beadle.internal.domain.callback.StatusCallback
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket
import com.bbh.itss.beadle.internal.domain.jira.JiraIssue
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion
import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.JIRA
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.SUCCESS

class Fixtures {

    static final LocalDate IMPLEMENTATION_DATE = LocalDate.parse('2026-10-15')
    static final Instant UPDATED = Instant.parse('2026-10-15T09:00:00Z')
    static final Instant CREATED = Instant.parse('2026-10-15T08:30:00Z')

    private Fixtures() {
    }

    static SecureCoding secureCoding(Map changes = [:]) {
        copy(changes, new SecureCoding('APO-12345', 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                'https://jenkins.bbh.com/job/CERT/job/cert-release/', 'https://cert.qc.bbh.com'))
    }

    static SecureCodingTicket ticket(Map changes = [:]) {
        copy(changes, new SecureCodingTicket('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE, secureCoding()))
    }

    static CyberTrackTicket cyberTrackTicket(Map changes = [:]) {
        copy(changes, new CyberTrackTicket('SCP-1234', 'To Do', 'https://cybertrack.bbh.com/browse/SCP-1234', UPDATED))
    }

    static TrackedTicket trackedTicket(Map changes = [:]) {
        copy(changes, new TrackedTicket('SCP-1234', 'To Do', 'corr-1', CREATED, CREATED, null, null, 0, 'CHG0031001',
                'https://cybertrack.bbh.com/browse/SCP-1234'))
    }

    static StatusCallback statusCallback(Map changes = [:]) {
        copy(changes, new StatusCallback(CYBERTRACK, 'SCP-1234', 'In Progress', UPDATED))
    }

    static Map secureCodingJson(SecureCoding inputs = secureCoding()) {
        [apoNumber: inputs.apoNumber(), bitbucketUrl: inputs.bitbucketUrl(), artifactLink: inputs.artifactLink(),
         qcApplicationLink: inputs.qcApplicationLink()]
    }

    static Map ticketJson(Map changes = [:]) {
        [changeNumber: 'CHG0031001', applicationName: 'CertScanner', implementationDate: IMPLEMENTATION_DATE.toString(),
         inputs: secureCodingJson()] + changes
    }

    static JiraVersion version(Map changes = [:]) {
        copy(changes, new JiraVersion('CERT 1.4', false, LocalDate.parse('2026-11-01')))
    }

    static JiraIssue issue(Map changes = [:]) {
        copy(changes, new JiraIssue('CERT-12', 'Single sign-on with BBH SSO', 'In Progress', null,
                LocalDate.parse('2026-10-01')))
    }

    static ConfluencePage page(Map changes = [:]) {
        copy(changes, new ConfluencePage('100001', 'DSO', 'Beadle release notes', 3,
                'https://confluence.bbh.com/pages/viewpage.action?pageId=100001', '<p>Beadle 1.0</p>'))
    }

    static NewConfluencePage newPage(Map changes = [:]) {
        copy(changes, new NewConfluencePage('DSO', 'Change CHG0031001', '100001', '<p>Evidence</p>'))
    }

    static Map newPageJson(Map changes = [:]) {
        [spaceKey: 'DSO', title: 'Change CHG0031001', parentId: '100001', body: '<p>Evidence</p>'] + changes
    }

    static ConfluencePageUpdate pageUpdate(Map changes = [:]) {
        copy(changes, new ConfluencePageUpdate('Beadle release notes', '<p>Beadle 1.1</p>', 3))
    }

    static Map pageUpdateJson(Map changes = [:]) {
        [title: 'Beadle release notes', body: '<p>Beadle 1.1</p>', version: 3] + changes
    }

    static QTestProject project(Map changes = [:]) {
        copy(changes, new QTestProject(1L, 'CertScanner'))
    }

    static QTestCycle cycle(Map changes = [:]) {
        copy(changes, new QTestCycle(10L, 'Release 1.4', 'CL-10'))
    }

    static QTestRun run(Map changes = [:]) {
        copy(changes, new QTestRun(100L, 'Sign in with SSO', 'TR-100', 'Passed', UPDATED))
    }

    static CallLogEntry callLogEntry(Map changes = [:]) {
        copy(changes, new CallLogEntry(1L, UPDATED, 'Mateusz Matan', 'corr-1', JIRA, 'versions', 'CERT', SUCCESS, null, 12L))
    }

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }
}
