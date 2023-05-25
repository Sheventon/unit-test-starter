package ru.itis.unitteststarter.service;

import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class TestGenerator {
    public static void generateTests(String name) {
        long startTime = System.currentTimeMillis();
        Reflections reflections = new Reflections(name, new SubTypesScanner(false));
        Set<Class<?>> classes = new HashSet<>(reflections.getSubTypesOf(Object.class))
                .stream()
                .filter(clazz -> !clazz.isInterface() && !clazz.isRecord())
                .collect(Collectors.toSet());
        classes.forEach(TestClassGenerator::generateTestClass);
        long endTime = System.currentTimeMillis();
        log.info(String.format("Unit tests was generated for %s classes in %s ms", classes.size(),
                (endTime - startTime)));
    }
}