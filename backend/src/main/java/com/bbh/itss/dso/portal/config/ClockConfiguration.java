package com.bbh.itss.dso.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static java.time.Clock.systemUTC;

@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return systemUTC();
    }
}
