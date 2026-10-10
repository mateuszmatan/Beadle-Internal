package com.bbh.itss.dso.portal.adapter.out.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dso.cybertrack")
public record CyberTrackProperties(String url, String token, @DefaultValue("SCP") String projectKey,
                                   @DefaultValue("Task") String issueType) {

    static final String CONFIGURED = "'${dso.cybertrack.url:}' != ''";
    static final String NOT_CONFIGURED = "'${dso.cybertrack.url:}' == ''";
}
