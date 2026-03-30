package com.example.clocking.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** IANA zone id for "today" and displayed clock times (e.g. Europe/Madrid). */
    private String timezone = "Europe/Madrid";

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
