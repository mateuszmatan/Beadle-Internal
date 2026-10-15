package com.bbh.itss.beadle.internal.domain.cybertrack;

import lombok.With;

import java.time.Instant;

/**
 * A CyberTrack ticket Beadle created, followed until it reaches a closed status so that Beadle-GUI hears of every
 * status it moves to. {@code status} is the last status Beadle-GUI knows of (the status the ticket was created in, or
 * the one of the last callback), {@code null} while CyberTrack could not tell it yet. {@code demo} tells a ticket the
 * demo adapter made up from one of the real CyberTrack: each is only followed where it was created. {@code misses}
 * counts the polls in a row that did not find the ticket; it is no longer followed once they reach the limit of the
 * {@link FollowUp}.
 */
@With
public record TrackedTicket(String key, String status, String correlationId, Instant createdAt, Instant lastSeenAt,
                            Instant lastEventAt, Instant closedAt, boolean demo, int misses) {

    public static TrackedTicket created(String key, String status, String correlationId, Instant at, boolean demo,
                                        FollowUp followUp) {
        return new TrackedTicket(key, status, correlationId, at, status == null ? null : at, null,
                followUp.closes(status) ? at : null, demo, 0);
    }

    public boolean open() {
        return closedAt == null;
    }

    public boolean movedIn(CyberTrackTicket current) {
        return current.status() != null && !current.status().equals(status);
    }

    public TrackedTicket reported(String newStatus, Instant at, FollowUp followUp) {
        return new TrackedTicket(key, newStatus, correlationId, createdAt, at, at,
                followUp.closes(newStatus) ? at : null, demo, 0);
    }

    public TrackedTicket seen(Instant at) {
        return withLastSeenAt(at).withMisses(0);
    }

    /**
     * The ticket after a poll that did not find it, closed when that makes {@code maxMisses} polls in a row.
     */
    public TrackedTicket missed(Instant at, FollowUp followUp) {
        int missed = misses + 1;
        return withMisses(missed).withClosedAt(missed >= followUp.maxMisses() ? at : null);
    }
}
