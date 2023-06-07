package ru.itis.unitteststarter.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import ru.itis.unitteststarter.properties.TestStarterProperties;
import ru.itis.unitteststarter.service.TestGenerator;

public class ApplicationReadyEventListener {

    @Autowired
    private TestStarterProperties testStarterProperties;

    @EventListener(value = ApplicationReadyEvent.class)
    public void onApplicationReadyEvent() {
        if (testStarterProperties.getName() != null) {
            TestGenerator.generateTests(testStarterProperties.getName());
        }
    }
}
