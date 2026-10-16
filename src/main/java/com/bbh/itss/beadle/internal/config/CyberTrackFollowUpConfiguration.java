package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CyberTrackFollowUpConfiguration {

    @Bean
    FollowUp followUp(CyberTrackFollowUpProperties properties) {
        return properties.followUp();
    }
}
