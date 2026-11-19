package com.webhookplatform.config;

import com.webhookplatform.security.SecretCipher;
import com.webhookplatform.security.UrlGuard;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretCipher secretCipher(AppProperties props) {
        return SecretCipher.fromBase64(props.encryptionKey());
    }

    @Bean
    UrlGuard urlGuard(AppProperties props) {
        boolean allowPrivate = props.ssrf().allowPrivateNetworks();
        if (allowPrivate) {
            log.warn("SSRF protection is DISABLED (ALLOW_PRIVATE_WEBHOOK_TARGETS=true). Never run this in production.");
        }
        return new UrlGuard(allowPrivate);
    }
}
