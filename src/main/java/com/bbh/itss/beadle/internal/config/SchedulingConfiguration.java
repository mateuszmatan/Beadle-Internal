package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * The CyberTrack poll: scheduled on every pod, run by one pod at a time through a ShedLock row in
 * {@value #LOCK_TABLE} that holds the database time, and following the tickets as long as {@link FollowUp} says.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfiguration {

    public static final String LOCK_TABLE = "BEADLE_INTERNAL_SHEDLOCK";

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource)).withTableName(LOCK_TABLE).usingDbTime().build());
    }

    @Bean
    FollowUp followUp(CyberTrackFollowUpProperties properties) {
        return new FollowUp(properties.closedStatuses(), properties.maxMisses());
    }
}
