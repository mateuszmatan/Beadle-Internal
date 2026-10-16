package com.bbh.itss.beadle.internal.adapter.out.confluence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beadle.confluence")
public record ConfluenceProperties(String url, String token) {

    static final String CONFIGURED = "'${beadle.confluence.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.confluence.url:}' == ''";
}
