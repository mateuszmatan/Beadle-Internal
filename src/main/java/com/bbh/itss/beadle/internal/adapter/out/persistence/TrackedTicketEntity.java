package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;
import static org.hibernate.type.SqlTypes.NUMERIC;

/**
 * A followed ticket; its NUMBER columns are NUMERIC to JDBC on Oracle and H2 alike.
 */
@Entity
@Table(name = "BEADLE_INTERNAL_CYBERTRACK_TICKET")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
class TrackedTicketEntity {

    @Id
    private String ticketKey;
    private String status;
    private String correlationId;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant lastEventAt;
    private Instant closedAt;
    @JdbcTypeCode(NUMERIC)
    private int misses;
    private String changeNumber;
    private String ticketUrl;

    static TrackedTicketEntity of(TrackedTicket ticket) {
        return new TrackedTicketEntity(ticket.key(), ticket.status(), ticket.correlationId(), ticket.createdAt(),
                ticket.lastSeenAt(), ticket.lastEventAt(), ticket.closedAt(), ticket.misses(), ticket.changeNumber(),
                ticket.url());
    }

    TrackedTicket toDomain() {
        return new TrackedTicket(ticketKey, status, correlationId, createdAt, lastSeenAt, lastEventAt, closedAt, misses,
                changeNumber, ticketUrl);
    }
}
