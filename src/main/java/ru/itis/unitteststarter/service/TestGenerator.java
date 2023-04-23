package ru.itis.unitteststarter.service;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;

public class TestGenerator {

    public static void generateTests(String name) {
        Reflections reflections = new Reflections(name, new SubTypesScanner(false));
        Set<Class<?>> classes = new HashSet<>(reflections.getSubTypesOf(Object.class));
        classes.stream()
                .filter(clazz -> !clazz.isInterface() && !clazz.isRecord())
                .forEach(TestClassGenerator::generateTestClass);
    }
}