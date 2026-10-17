package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.NOT_CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static java.lang.Math.min;
import static java.time.Duration.between;
import static java.time.Duration.ofMinutes;

/**
 * Plays CyberTrack while {@code beadle.cybertrack.url} is blank: hands out keys of its project one after another and
 * moves each ticket To Do, In Progress, Done on the clock, {@link #STEP} per step from its creation. The creation
 * times of this run are kept in memory; after a restart a ticket Beadle follows moves on from the creation time its
 * row keeps, and the keys continue after the highest one followed, so that no key is handed out twice.
 */
@Slf4j
@Component
@ConditionalOnExpression(NOT_CONFIGURED)
@RequiredArgsConstructor
class DemoCyberTrackAdapter implements CyberTrackPort {

    static final Duration STEP = ofMinutes(2);
    static final List<String> STATUSES = List.of("To Do", "In Progress", "Done");

    private final Map<String, Instant> created = new ConcurrentHashMap<>();
    private final Clock clock;
    private final CyberTrackProperties properties;
    private final TrackedTicketPort tracked;
    private long next;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public synchronized CreatedTicket create(SecureCodingTicket ticket) {
        if (next == 0) {
            next = firstNumber();
        }
        String key = prefix() + next++;
        created.put(key, now(clock));
        return new CreatedTicket(key, null);
    }

    @Override
    public CyberTrackTicket read(String key) {
        Instant now = now(clock);
        Instant createdAt = created.computeIfAbsent(key, missing -> createdAt(missing, now));
        int step = (int) min(between(createdAt, now).dividedBy(STEP), STATUSES.size() - 1L);
        return new CyberTrackTicket(key, STATUSES.get(step), null, createdAt.plus(STEP.multipliedBy(step)));
    }

    @Override
    public List<CyberTrackTicket> readAll(Collection<String> keys) {
        List<CyberTrackTicket> found = new ArrayList<>();
        for (String key : keys) {
            try {
                found.add(read(key));
            } catch (NoSuchElementException e) {
                // a key the demo does not know is left out, as CyberTrack leaves out an issue it does not have
            }
        }
        return found;
    }

    private String prefix() {
        return properties.projectKey() + "-";
    }

    /**
     * The number after the highest key followed, or a random one in the thousands when none is.
     */
    private long firstNumber() {
        try {
            Optional<Long> highest = tracked.highestKey(prefix()).map(key -> key.substring(prefix().length()))
                    .filter(number -> number.matches("\\d{1,18}")).map(Long::parseLong);
            if (highest.isPresent()) {
                return highest.get() + 1;
            }
        } catch (RuntimeException e) {
            log.warn("The demo CyberTrack could not find the highest key followed and starts at random: {}",
                    e.getMessage());
        }
        return 1_000 + new SecureRandom().nextInt(8_000);
    }

    /**
     * When a ticket of a former run was created: the time its row keeps, or two steps ago (Done) for a key of the
     * project Beadle never followed.
     */
    private Instant createdAt(String key, Instant now) {
        if (!key.matches(Pattern.quote(prefix()) + "\\d+")) {
            throw notFound(CyberTrackTicket.NAME, key);
        }
        return tracked.find(key).map(TrackedTicket::createdAt)
                .orElseGet(() -> now.minus(STEP.multipliedBy(STATUSES.size() - 1L)));
    }
}
