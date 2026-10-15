package com.bbh.itss.beadle.internal.domain.cybertrack;

import lombok.With;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;

/**
 * A CyberTrack ticket Beadle created, followed until it reaches a closed status so that Beadle-GUI hears of every
 * status it moves to. {@code status} is the last status Beadle-GUI knows of (the status the ticket was created in, or
 * the one of the last callback), {@code null} while CyberTrack could not tell it yet.
 */
@With
public record TrackedTicket(String key, String status, String correlationId, Instant createdAt, Instant lastSeenAt,
                            Instant lastEventAt, Instant closedAt) {

    public static final List<String> CLOSED_STATUSES =
            List.of("Done", "Closed", "Resolved", "Cancelled", "Canceled", "Rejected", "Won't Do");

    public static TrackedTicket created(String key, String status, String correlationId, Instant at) {
        return new TrackedTicket(key, status, correlationId, at, status == null ? null : at, null,
                closed(status) ? at : null);
    }

    public static boolean closed(String status) {
        return status != null && equalsAnyIgnoreCase(status.trim(), CLOSED_STATUSES.toArray(String[]::new));
    }

    public boolean open() {
        return closedAt == null;
    }

    public boolean movedIn(CyberTrackTicket current) {
        return current.status() != null && !current.status().equals(status);
    }

    public TrackedTicket reported(String newStatus, Instant at) {
        return new TrackedTicket(key, newStatus, correlationId, createdAt, at, at, closed(newStatus) ? at : null);
    }
}
