package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("beadle.cybertrack")
public record CyberTrackProperties(String url, String token, @DefaultValue("SCP") String projectKey,
                                   @DefaultValue("Task") String issueType) {

    static final String CONFIGURED = "'${beadle.cybertrack.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.cybertrack.url:}' == ''";
}
