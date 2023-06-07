package ru.itis.unitteststarter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itis.unitteststarter.listener.ApplicationReadyEventListener;
import ru.itis.unitteststarter.properties.TestStarterProperties;

@Configuration
@EnableConfigurationProperties(TestStarterProperties.class)
public class TestStarterConfiguration {

    @Bean
    @ConditionalOnProperty("test-starter.package.name")
    public ApplicationReadyEventListener applicationReadyEventListener() {
        return new ApplicationReadyEventListener();
    }
}
