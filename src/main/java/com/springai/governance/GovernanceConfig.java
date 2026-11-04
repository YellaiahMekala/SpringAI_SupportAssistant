package com.springai.governance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({GovernanceProperties.class, ResilienceProperties.class})
public class GovernanceConfig {

    /** UTC clock, injectable so tests can fix the day. The daily budget resets at 00:00 UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
