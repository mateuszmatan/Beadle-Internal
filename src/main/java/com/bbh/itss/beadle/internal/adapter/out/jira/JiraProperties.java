package com.bbh.itss.beadle.internal.adapter.out.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beadle.jira")
public record JiraProperties(String url, String token, String epicLinkField) {
}
