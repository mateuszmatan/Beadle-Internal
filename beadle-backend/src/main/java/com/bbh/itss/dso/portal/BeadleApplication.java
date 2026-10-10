package com.bbh.itss.dso.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BeadleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeadleApplication.class, args);
    }
}
