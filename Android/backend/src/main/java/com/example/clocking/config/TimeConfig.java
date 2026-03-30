package com.example.clocking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class})
public class TimeConfig {

    @Bean
    ZoneId appZoneId(AppProperties appProperties) {
        return appProperties.zoneId();
    }

    @Bean
    Clock appClock(ZoneId appZoneId) {
        return Clock.system(appZoneId);
    }
}
