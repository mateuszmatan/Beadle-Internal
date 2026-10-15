package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_INTERNAL_CYBERTRACK_TICKET")
@NoArgsConstructor(access = PROTECTED)
class TrackedTicketEntity {

    @Id
    private String ticketKey;

    private String status;

    private String correlationId;

    private Instant createdAt;

    private Instant lastSeenAt;

    private Instant lastEventAt;

    private Instant closedAt;

    static TrackedTicketEntity of(TrackedTicket ticket) {
        TrackedTicketEntity entity = new TrackedTicketEntity();
        entity.ticketKey = ticket.key();
        entity.status = ticket.status();
        entity.correlationId = ticket.correlationId();
        entity.createdAt = ticket.createdAt();
        entity.lastSeenAt = ticket.lastSeenAt();
        entity.lastEventAt = ticket.lastEventAt();
        entity.closedAt = ticket.closedAt();
        return entity;
    }

    TrackedTicket toDomain() {
        return new TrackedTicket(ticketKey, status, correlationId, createdAt, lastSeenAt, lastEventAt, closedAt);
    }
}
