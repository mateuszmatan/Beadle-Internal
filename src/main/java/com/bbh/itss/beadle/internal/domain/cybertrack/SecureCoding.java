package com.bbh.itss.beadle.internal.domain.cybertrack;

import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems;

import static com.bbh.itss.beadle.internal.domain.shared.Text.isUrl;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record SecureCoding(String apoNumber, String bitbucketUrl, String artifactLink, String qcApplicationLink) {

    public static final SecureCoding NONE = new SecureCoding(null, null, null, null);
    public static final int NUMBER_MAX = 40;
    public static final int LINK_MAX = 500;
    public static final String LINK_MESSAGE = "must be a link starting with https:// or http://";

    public SecureCoding {
        apoNumber = trimToNull(apoNumber);
        bitbucketUrl = trimToNull(bitbucketUrl);
        artifactLink = trimToNull(artifactLink);
        qcApplicationLink = trimToNull(qcApplicationLink);
    }

    void validate(ValidationProblems problems) {
        problems.fits("apoNumber", apoNumber, NUMBER_MAX);
        link("bitbucketUrl", bitbucketUrl, problems);
        link("artifactLink", artifactLink, problems);
        link("qcApplicationLink", qcApplicationLink, problems);
    }

    void require(ValidationProblems problems) {
        problems.require("apoNumber", apoNumber).require("bitbucketUrl", bitbucketUrl)
                .require("artifactLink", artifactLink).require("qcApplicationLink", qcApplicationLink);
        validate(problems);
    }

    private static void link(String field, String value, ValidationProblems problems) {
        problems.fits(field, value, LINK_MAX);
        if (value != null && !isUrl(value)) {
            problems.add(field, LINK_MESSAGE);
        }
    }
}
