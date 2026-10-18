package com.bbh.itss.beadle.internal.application.cybertrack;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackPollUseCase;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.callback.PollReport;
import com.bbh.itss.beadle.internal.domain.callback.StatusCallback;
import com.bbh.itss.beadle.internal.domain.calllog.Caller;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp;
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
 * Follows the CyberTrack tickets Beadle created. The poll reads all open tickets in one call of the port made by
 * {@code system} (one CyberTrack search per 50 keys); each callback carries the correlation id of the user action that created its ticket. A ticket CyberTrack no longer
 * finds is tried again on the next polls and dropped once the {@link FollowUp} gives up on it.
 */
@UseCase
@RequiredArgsConstructor
public class CyberTrackPollService implements CyberTrackPollUseCase {

    private final TrackedTicketPort tracked;
    private final CyberTrackPort cyberTrack;
    private final CallbackPort callbacks;
    private final FollowUp followUp;
    private final Clock clock;

    @Override
    public synchronized PollReport poll() {
        List<TrackedTicket> open = tracked.open();
        if (open.isEmpty()) {
            return PollReport.NOTHING;
        }
        Instant now = now(clock);
        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, String> dropped = new LinkedHashMap<>();
        Map<String, CyberTrackTicket> current = CallerContext.as(new Caller(SYSTEM_USER, randomUUID().toString()),
                        () -> cyberTrack.readAll(open.stream().map(TrackedTicket::key).toList())).stream()
                .collect(toMap(CyberTrackTicket::key, identity(), (first, second) -> first));
        List<StatusCallback> sent = new ArrayList<>();
        for (TrackedTicket ticket : open) {
            CyberTrackTicket found = current.get(ticket.key());
            try {
                if (found == null) {
                    missed(ticket, now, dropped, failures);
                } else if (ticket.movedIn(found)) {
                    sent.add(report(ticket, found, now));
                } else {
                    tracked.save(ticket.seen(now));
                }
            } catch (RuntimeException e) {
                failures.put(ticket.key(), defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
        }
        return pollReport(sent, failures, dropped);
    }

    private void missed(TrackedTicket ticket, Instant now, Map<String, String> dropped, Map<String, String> failures) {
        TrackedTicket missed = ticket.missed(now, followUp);
        tracked.save(missed);
        String reason = notFound(CyberTrackTicket.NAME, ticket.key()).getMessage();
        if (missed.open()) {
            failures.put(ticket.key(), reason + " (" + missed.misses() + " of " + followUp.maxMisses()
                    + " polls in a row before it is dropped)");
        } else {
            dropped.put(ticket.key(), reason + ": CyberTrack did not find it " + missed.misses() + " polls in a row");
        }
    }

    private static PollReport pollReport(List<StatusCallback> sent, Map<String, String> failures,
                                         Map<String, String> dropped) {
        return new PollReport(List.copyOf(sent), Collections.unmodifiableMap(failures),
                Collections.unmodifiableMap(dropped));
    }

    private StatusCallback report(TrackedTicket ticket, CyberTrackTicket found, Instant now) {
        StatusCallback callback = new StatusCallback(CYBERTRACK, ticket.key(), found.status(),
                getIfNull(found.updated(), now));
        CallerContext.run(new Caller(SYSTEM_USER, ticket.correlationId()), () -> callbacks.notify(callback));
        tracked.save(ticket.reported(found.status(), now, followUp));
        return callback;
    }
}
