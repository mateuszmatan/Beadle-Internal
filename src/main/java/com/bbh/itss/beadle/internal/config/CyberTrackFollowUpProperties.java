package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * How long the CyberTrack tickets Beadle created are followed: {@code beadle.cybertrack.closed-statuses} end it, and
 * so do {@code beadle.cybertrack.max-misses} polls in a row that did not find a ticket.
 */
@ConfigurationProperties("beadle.cybertrack")
public record CyberTrackFollowUpProperties(
        @DefaultValue({"Done", "Closed", "Resolved", "Cancelled", "Canceled", "Rejected", "Won't Do"})
        List<String> closedStatuses,
        @DefaultValue("12") int maxMisses) {

    FollowUp followUp() {
        return new FollowUp(closedStatuses, maxMisses);
    }
}
