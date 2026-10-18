package com.bbh.itss.beadle.internal.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The secret every call between the Beadle services carries, both ways ({@code BEADLE_SERVICE_TOKEN}).
 */
@ConfigurationProperties("beadle.services")
public record ServiceTokenProperties(String token) {
}
