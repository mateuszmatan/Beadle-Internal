package com.bbh.itss.beadle.internal.application.cybertrack;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class CyberTrackService implements CyberTrackUseCase {

    static final int LOCKS = 64;

    private final CyberTrackPort cyberTrack;
    private final TrackedTicketPort tracked;
    private final Clock clock;
    private final Object[] locks = Stream.generate(Object::new).limit(LOCKS).toArray();

    /**
     * Creates the secure coding ticket of a change once: while a ticket created for the same change is still followed,
     * a second request (Beadle-GUI retrying after its own time-out) gets that ticket instead of a new one. The answer
     * comes straight after the creation, which is the only call made to CyberTrack; the poll tells Beadle-GUI the
     * status the ticket starts in. The requests of a change take one of {@value #LOCKS} locks, so that the locks do not
     * grow with the changes.
     */
    @Override
    public CreatedTicket create(SecureCodingTicket ticket) {
        synchronized (locks[Math.floorMod(ticket.changeNumber().hashCode(), LOCKS)]) {
            Optional<TrackedTicket> existing = tracked.openFor(ticket.changeNumber());
            if (existing.isPresent()) {
                log.info("Change {} asked again for its secure coding ticket and gets {}", ticket.changeNumber(),
                        existing.get().key());
                return existing.get().asCreated();
            }
            CreatedTicket created = cyberTrack.create(ticket);
            log.info("Created the CyberTrack ticket {} for change {}", created.key(), ticket.changeNumber());
            tracked.track(TrackedTicket.created(created, ticket.changeNumber(), CallerContext.current().correlationId(),
                    now(clock)));
            return created;
        }
    }

    @Override
    public CyberTrackTicket read(String key) {
        String trimmed = trimToNull(key);
        if (trimmed == null) {
            throw notFound(CyberTrackTicket.NAME, key);
        }
        return cyberTrack.read(trimmed);
    }
}
