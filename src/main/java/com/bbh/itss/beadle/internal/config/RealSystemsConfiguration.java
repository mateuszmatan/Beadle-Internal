package com.bbh.itss.beadle.internal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Keeps the {@code oracle} profiles (rd, qc and prod) on the real systems. A system whose URL is blank is played by
 * its demo adapter, which answers like the real one: a demo CyberTrack hands out made-up SCP keys that Beadle-GUI
 * records as real secure coding tickets. So these profiles refuse to start while a system's URL is blank, unless
 * {@code beadle.<system>.demo} ({@code <SYSTEM>_DEMO}) asks for its demo on purpose. The check runs before any bean
 * is created, so nothing reaches the database or a system first.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile("oracle")
public class RealSystemsConfiguration {

    static final List<String> SYSTEMS = List.of("jira", "cybertrack", "confluence", "qtest");

    @Bean
    static BeanFactoryPostProcessor realSystemsCheck(Environment environment) {
        return beanFactory -> requireRealSystems(environment);
    }

    static void requireRealSystems(Environment environment) {
        List<String> blank = new ArrayList<>();
        for (String system : SYSTEMS) {
            if (!isBlank(environment.getProperty("beadle." + system + ".url"))) {
                continue;
            }
            String variable = system.toUpperCase(Locale.ROOT);
            if (environment.getProperty("beadle." + system + ".demo", Boolean.class, false)) {
                log.warn("{} runs on its demo adapter: {}_URL is blank and {}_DEMO=true", variable, variable,
                        variable);
            } else {
                blank.add(variable + "_URL");
            }
        }
        if (!blank.isEmpty()) {
            throw new IllegalStateException("The rd, qc and prod profiles need the real systems, but "
                    + String.join(", ", blank) + (blank.size() == 1 ? " is" : " are")
                    + " blank and the demo adapters would answer in their place. Set each URL in the ConfigMap of "
                    + "the environment, or <SYSTEM>_DEMO=true to run that system on its demo on purpose.");
        }
    }
}
