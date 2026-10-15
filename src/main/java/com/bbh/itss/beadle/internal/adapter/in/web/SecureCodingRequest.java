package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding;

public record SecureCodingRequest(String apoNumber, String bitbucketUrl, String artifactLink,
                                  String qcApplicationLink) {

    SecureCoding toInputs() {
        return new SecureCoding(apoNumber, bitbucketUrl, artifactLink, qcApplicationLink);
    }
}
