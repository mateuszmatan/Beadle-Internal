package com.bbh.itss.beadle.internal.support

import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket

import java.time.LocalDate

class Fixtures {

    static final LocalDate IMPLEMENTATION_DATE = LocalDate.parse('2026-10-15')

    private Fixtures() {
    }

    static SecureCoding secureCoding(Map changes = [:]) {
        copy(changes, new SecureCoding('APO-12345', 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                'https://jenkins.bbh.com/job/CERT/job/cert-release/', 'https://cert.qc.bbh.com'))
    }

    static SecureCodingTicket ticket(Map changes = [:]) {
        copy(changes, new SecureCodingTicket('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE, secureCoding()))
    }

    static Map secureCodingJson(SecureCoding inputs = secureCoding()) {
        [apoNumber: inputs.apoNumber(), bitbucketUrl: inputs.bitbucketUrl(), artifactLink: inputs.artifactLink(),
         qcApplicationLink: inputs.qcApplicationLink()]
    }

    static Map ticketJson(Map changes = [:]) {
        [changeNumber: 'CHG0031001', applicationName: 'CertScanner', implementationDate: IMPLEMENTATION_DATE.toString(),
         inputs: secureCodingJson()] + changes
    }

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }
}
