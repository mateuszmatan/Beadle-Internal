package com.bbh.itss.beadle.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * How long the CyberTrack tickets Beadle created are followed: {@code beadle.cybertrack.closed-statuses} end it, and
 * so do {@code beadle.cybertrack.max-misses} polls in a row that did not find a ticket.
 */
@ConfigurationProperties("beadle.cybertrack")
public record CyberTrackFollowUpProperties(List<String> closedStatuses, int maxMisses) {
}
