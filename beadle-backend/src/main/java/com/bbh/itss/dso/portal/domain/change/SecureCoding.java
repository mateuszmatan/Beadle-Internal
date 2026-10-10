package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NUMBER_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.dso.portal.domain.shared.Text.isUrl;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record SecureCoding(String apoNumber, String bitbucketUrl, String artifactLink, String qcApplicationLink) {

    public static final SecureCoding NONE = new SecureCoding(null, null, null, null);
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
        problems.require("apoNumber", apoNumber, REQUIRED)
                .require("bitbucketUrl", bitbucketUrl, REQUIRED)
                .require("artifactLink", artifactLink, REQUIRED)
                .require("qcApplicationLink", qcApplicationLink, REQUIRED);
        validate(problems);
    }

    private static void link(String field, String value, ValidationProblems problems) {
        problems.fits(field, value, LINK_MAX);
        if (value != null && !isUrl(value)) {
            problems.add(field, LINK_MESSAGE);
        }
    }
}
