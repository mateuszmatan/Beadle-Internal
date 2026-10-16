package com.bbh.itss.beadle.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("beadle.services")
public record ServiceTokenProperties(String token) {
}
