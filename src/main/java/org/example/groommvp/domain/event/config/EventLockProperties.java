package org.example.groommvp.domain.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "event.lock")
public record EventLockProperties(EventLockStrategy strategy) {

    public EventLockProperties {
        if (strategy == null) {
            strategy = EventLockStrategy.DISTRIBUTED;
        }
    }
}
