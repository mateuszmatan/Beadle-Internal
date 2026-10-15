package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.NOT_CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static java.lang.Math.min;
import static java.time.Duration.between;
import static java.time.Duration.ofMinutes;

@Component
@ConditionalOnExpression(NOT_CONFIGURED)
@RequiredArgsConstructor
class DemoCyberTrackAdapter implements CyberTrackPort {

    static final Duration STEP = ofMinutes(2);
    static final List<String> STATUSES = List.of("To Do", "In Progress", "Done");
    static final String TICKET = "CyberTrack ticket";

    private final AtomicInteger tickets = new AtomicInteger(1_000 + new SecureRandom().nextInt(8_000));
    private final Map<String, Instant> created = new ConcurrentHashMap<>();
    private final Clock clock;
    private final CyberTrackProperties properties;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public CreatedTicket create(SecureCodingTicket ticket) {
        String key = properties.projectKey() + "-" + tickets.incrementAndGet();
        created.put(key, now(clock));
        return new CreatedTicket(key, null);
    }

    @Override
    public CyberTrackTicket read(String key) {
        Instant now = now(clock);
        Instant createdAt = created.computeIfAbsent(key, missing -> adopted(missing, now));
        int step = (int) min(between(createdAt, now).dividedBy(STEP), STATUSES.size() - 1L);
        return new CyberTrackTicket(key, STATUSES.get(step), null, createdAt.plus(STEP.multipliedBy(step)));
    }

    private Instant adopted(String key, Instant now) {
        if (!key.matches(properties.projectKey() + "-\\d+")) {
            throw notFound(TICKET, key);
        }
        return now.minus(STEP.multipliedBy(STATUSES.size() - 1L));
    }
}
