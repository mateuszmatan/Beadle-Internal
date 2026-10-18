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
 * Follows the CyberTrack tickets Beadle created: the demo adapter moves them on its clock, the live adapter reads
 * them from CyberTrack. The poll reads all open tickets in one call made by {@code system}; each callback carries the
 * correlation id of the user action that created its ticket. A ticket the demo made up is never looked for in the
 * real CyberTrack, where its key may belong to another ticket, nor a real one in the demo: once the adapter changed,
 * such a ticket is dropped. A ticket CyberTrack no longer finds is tried again on the next polls and dropped once
 * the {@link FollowUp} gives up on it.
 */
@UseCase
@RequiredArgsConstructor
public class CyberTrackPollService implements CyberTrackPollUseCase {

    static final String DEMO_TICKET = "the demo CyberTrack made it up, and CyberTrack is configured now";
    static final String REAL_TICKET = "it was created in CyberTrack, and the demo plays CyberTrack now";

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
        boolean demo = !cyberTrack.connected();
        Map<String, String> failures = new LinkedHashMap<>();
        Map<String, String> dropped = new LinkedHashMap<>();
        List<TrackedTicket> followed = new ArrayList<>();
        for (TrackedTicket ticket : open) {
            if (ticket.demo() == demo) {
                followed.add(ticket);
            } else {
                drop(ticket, ticket.demo() ? DEMO_TICKET : REAL_TICKET, now, dropped, failures);
            }
        }
        if (followed.isEmpty()) {
            return pollReport(List.of(), failures, dropped);
        }
        Map<String, CyberTrackTicket> current = CallerContext.as(new Caller(SYSTEM_USER, randomUUID().toString()),
                        () -> cyberTrack.readAll(followed.stream().map(TrackedTicket::key).toList())).stream()
                .collect(toMap(CyberTrackTicket::key, identity(), (first, second) -> first));
        List<StatusCallback> sent = new ArrayList<>();
        for (TrackedTicket ticket : followed) {
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

    private void drop(TrackedTicket ticket, String reason, Instant now, Map<String, String> dropped,
                      Map<String, String> failures) {
        try {
            tracked.save(ticket.withClosedAt(now));
            dropped.put(ticket.key(), reason);
        } catch (RuntimeException e) {
            failures.put(ticket.key(), defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
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
