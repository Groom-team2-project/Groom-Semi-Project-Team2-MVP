package org.example.groommvp.domain.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KakaoOAuthProperties.class,
        JwtProperties.class
})
public class AuthPropertiesConfig {
}
