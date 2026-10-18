package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The CyberTrack poll, following the tickets as long as {@link FollowUp} says.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {

    @Bean
    FollowUp followUp(CyberTrackFollowUpProperties properties) {
        return new FollowUp(properties.closedStatuses(), properties.maxMisses());
    }
}
