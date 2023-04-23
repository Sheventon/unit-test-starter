package ru.itis.unitteststarter.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import ru.itis.unitteststarter.properties.TestStarterProperties;
import ru.itis.unitteststarter.service.TestGenerator;

@RequiredArgsConstructor
public class ApplicationContextListener implements ApplicationListener<ContextRefreshedEvent> {

    private static int count = 0;

    @Autowired
    private TestStarterProperties testStarterProperties;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        count++;
        if (count == 1) {
            if (testStarterProperties.getName() != null) {
                TestGenerator.generateTests(testStarterProperties.getName());
            }
        }
    }
}
