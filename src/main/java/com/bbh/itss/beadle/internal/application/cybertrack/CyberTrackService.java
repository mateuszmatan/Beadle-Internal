package com.bbh.itss.beadle.internal.application.cybertrack;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@UseCase
@RequiredArgsConstructor
public class CyberTrackService implements CyberTrackUseCase {

    public static final String TICKET = "CyberTrack ticket";

    private final CyberTrackPort cyberTrack;
    private final TrackedTicketPort tracked;
    private final Clock clock;
    private final ConcurrentMap<String, Object> changes = new ConcurrentHashMap<>();

    /**
     * Raises the secure coding ticket of a change once: while a ticket raised for the same change is still followed,
     * a second request (Beadle-GUI retrying after its own time-out) gets that ticket instead of a new one. The answer
     * comes straight after the creation, which is the only call made to CyberTrack; the poll tells Beadle-GUI the
     * status the ticket starts in.
     */
    @Override
    @WithoutTransaction
    public CreatedTicket create(SecureCodingTicket ticket) {
        synchronized (changes.computeIfAbsent(ticket.changeNumber(), change -> new Object())) {
            boolean demo = !cyberTrack.connected();
            Optional<TrackedTicket> raised = tracked.openFor(ticket.changeNumber())
                    .filter(open -> open.demo() == demo);
            if (raised.isPresent()) {
                return raised.get().asCreated();
            }
            CreatedTicket created = cyberTrack.create(ticket);
            tracked.track(TrackedTicket.created(created, ticket.changeNumber(), CallerContext.current().correlationId(),
                    now(clock), demo));
            return created;
        }
    }

    @Override
    @WithoutTransaction
    public CyberTrackTicket read(String key) {
        String trimmed = trimToNull(key);
        if (trimmed == null) {
            throw notFound(TICKET, key);
        }
        return cyberTrack.read(trimmed);
    }
}
