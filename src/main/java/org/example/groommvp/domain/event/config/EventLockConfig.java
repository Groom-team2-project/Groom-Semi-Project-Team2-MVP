package org.example.groommvp.domain.event.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventLockProperties.class)
public class EventLockConfig {
}
