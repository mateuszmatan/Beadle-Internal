package com.bbh.itss.beadle.internal.application.cybertrack;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackPollUseCase;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.callback.PollReport;
import com.bbh.itss.beadle.internal.domain.callback.StatusCallback;
import com.bbh.itss.beadle.internal.domain.calllog.Caller;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.SYSTEM_USER;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CYBERTRACK;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static java.util.UUID.randomUUID;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Follows the CyberTrack tickets Beadle created: the demo adapter moves them on its clock, the live adapter reads
 * them from CyberTrack. The poll reads all open tickets in one call made by {@code system}; each callback carries the
 * correlation id of the user action that created its ticket.
 */
@UseCase
@RequiredArgsConstructor
public class CyberTrackPollService implements CyberTrackPollUseCase {

    static final String TICKET = "CyberTrack ticket";

    private final TrackedTicketPort tracked;
    private final CyberTrackPort cyberTrack;
    private final CallbackPort callbacks;
    private final Clock clock;

    @Override
    @WithoutTransaction
    public synchronized PollReport poll() {
        List<TrackedTicket> open = tracked.open();
        if (open.isEmpty()) {
            return PollReport.NOTHING;
        }
        Map<String, CyberTrackTicket> current = CallerContext.as(new Caller(SYSTEM_USER, randomUUID().toString()),
                        () -> cyberTrack.readAll(open.stream().map(TrackedTicket::key).toList())).stream()
                .collect(toMap(CyberTrackTicket::key, identity(), (first, second) -> first));
        Instant now = now(clock);
        List<StatusCallback> sent = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (TrackedTicket ticket : open) {
            CyberTrackTicket found = current.get(ticket.key());
            try {
                if (found == null) {
                    throw notFound(TICKET, ticket.key());
                }
                if (ticket.movedIn(found)) {
                    sent.add(report(ticket, found, now));
                } else {
                    tracked.save(ticket.withLastSeenAt(now));
                }
            } catch (RuntimeException e) {
                failures.put(ticket.key(), defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
        }
        return new PollReport(List.copyOf(sent), Collections.unmodifiableMap(failures));
    }

    private StatusCallback report(TrackedTicket ticket, CyberTrackTicket found, Instant now) {
        StatusCallback callback = new StatusCallback(CYBERTRACK, ticket.key(), found.status(),
                getIfNull(found.updated(), now), ticket.correlationId());
        CallerContext.run(new Caller(SYSTEM_USER, ticket.correlationId()), () -> callbacks.notify(callback));
        tracked.save(ticket.reported(found.status(), now));
        return callback;
    }
}
