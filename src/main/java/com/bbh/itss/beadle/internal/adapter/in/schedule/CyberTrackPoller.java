package com.bbh.itss.beadle.internal.adapter.in.schedule;

import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackPollUseCase;
import com.bbh.itss.beadle.internal.domain.callback.PollReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the CyberTrack tickets Beadle created: every {@value #DEMO_INTERVAL} while the demo adapter moves them on
 * its clock, every {@code beadle.cybertrack.poll} (default {@value #LIVE_INTERVAL}) once CyberTrack is configured. A
 * tick never throws: what failed is logged and tried again on the next tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CyberTrackPoller {

    public static final String DEMO_INTERVAL = "PT10S";
    public static final String LIVE_INTERVAL = "PT5M";
    static final String INTERVAL = "#{'${beadle.cybertrack.url:}' == '' ? '" + DEMO_INTERVAL
            + "' : '${beadle.cybertrack.poll:" + LIVE_INTERVAL + "}'}";

    private final CyberTrackPollUseCase cyberTrack;

    @Scheduled(fixedDelayString = INTERVAL, initialDelayString = INTERVAL)
    public void tick() {
        try {
            PollReport report = cyberTrack.poll();
            report.sent().forEach(callback -> log.info("Told Beadle-GUI that CyberTrack ticket {} is now {}",
                    callback.key(), callback.status()));
            report.failures().forEach((key, reason) -> log.warn(
                    "CyberTrack ticket {} could not be followed up and is tried again on the next tick: {}", key,
                    reason));
        } catch (RuntimeException e) {
            log.warn("The CyberTrack poll failed and is tried again on the next tick: {}", e.getMessage(), e);
        }
    }
}
