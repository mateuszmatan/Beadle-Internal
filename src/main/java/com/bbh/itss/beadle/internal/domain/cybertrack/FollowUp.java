package com.bbh.itss.beadle.internal.domain.cybertrack;

import java.util.List;

import static com.bbh.itss.beadle.internal.domain.shared.Text.clean;
import static org.apache.commons.lang3.Strings.CI;

/**
 * How long the CyberTrack tickets Beadle created are followed: until they reach one of the {@code closedStatuses}
 * (compared without case), or until the poll did not find them {@code maxMisses} times in a row, as when a ticket
 * was deleted or moved out of the reach of the Beadle service account.
 */
public record FollowUp(List<String> closedStatuses, int maxMisses) {

    public FollowUp {
        closedStatuses = clean(closedStatuses);
        if (closedStatuses.isEmpty()) {
            throw new IllegalArgumentException("beadle.cybertrack.closed-statuses needs at least one status");
        }
        if (maxMisses < 1) {
            throw new IllegalArgumentException("beadle.cybertrack.max-misses must be 1 or more, not " + maxMisses);
        }
    }

    public boolean closes(String status) {
        return status != null && closedStatuses.stream().anyMatch(closed -> CI.equals(closed, status.trim()));
    }
}
