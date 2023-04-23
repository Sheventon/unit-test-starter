package ru.itis.unitteststarter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("test-starter.package")
public class TestStarterProperties {

    String name;
}
