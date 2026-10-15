package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.NOT_CONFIGURED;

@Component
@ConditionalOnExpression(NOT_CONFIGURED)
@RequiredArgsConstructor
class DemoCyberTrackAdapter implements CyberTrackPort {

    private final AtomicInteger tickets = new AtomicInteger(1_000 + new SecureRandom().nextInt(8_000));
    private final CyberTrackProperties properties;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public String create(SecureCodingTicket ticket) {
        return properties.projectKey() + "-" + tickets.incrementAndGet();
    }
}
