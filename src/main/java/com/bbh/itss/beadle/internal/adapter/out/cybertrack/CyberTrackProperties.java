package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beadle.cybertrack")
public record CyberTrackProperties(String url, String token, String projectKey, String issueType) {
}
