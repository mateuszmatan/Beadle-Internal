package com.bbh.itss.beadle.internal.adapter.out.qtest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beadle.qtest")
public record QTestProperties(String url, String token) {

    static final String CONFIGURED = "'${beadle.qtest.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.qtest.url:}' == ''";
}
