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

import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.NoSuchElementException;

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

    /**
     * Creates the ticket and follows it from the status it was created in, so that Beadle-GUI hears of its next
     * status; when that status cannot be read now, the first status read later is reported.
     */
    @Override
    @WithoutTransaction
    public CreatedTicket create(SecureCodingTicket ticket) {
        CreatedTicket created = cyberTrack.create(ticket);
        tracked.track(TrackedTicket.created(created.key(), statusOf(created.key()),
                CallerContext.current().correlationId(), now(clock), !cyberTrack.connected()));
        return created;
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

    private String statusOf(String key) {
        try {
            return cyberTrack.read(key).status();
        } catch (UncheckedIOException | NoSuchElementException e) {
            return null;
        }
    }
}
