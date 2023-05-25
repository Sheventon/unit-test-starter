package ru.itis.unitteststarter.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.squareup.javapoet.*;
import org.apache.commons.lang3.tuple.Pair;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.SubTypesScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.isPublic;

public class TestClassGenerator {
    public static void generateTestClass(Class<?> clas) {
        String path = String.format("src/main/java/%s", clas.getName()).replaceAll("\\.", "/").concat(".java");
        List<MethodDeclaration> methodsWithBody = getClassWithMethodsBody(path);
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(String.format("%s%s", clas.getSimpleName(), "UnitTest"))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ContextConfiguration.class)
                        .addMember("classes", String.format("%s.class", clas.getSimpleName()))
                        .build())
                .addAnnotation(AnnotationSpec.builder(ExtendWith.class)
                        .addMember("value", "$T.class", SpringExtension.class)
                        .build());
        FieldSpec self = FieldSpec.builder(clas, clas.getSimpleName().substring(0, 1).toLowerCase().concat(clas.getSimpleName().substring(1)))
                .addModifiers(Modifier.PRIVATE)
                .addAnnotation(Autowired.class)
                .build();
        classBuilder.addField(self);

        Set<Class<?>> beans = new HashSet<>();
        for (Constructor<?> constructor : clas.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                beans.add(parameter.getType());
            }
        }

        Arrays.stream(clas.getDeclaredFields())
                .forEach(field -> createClassField(classBuilder, field));
        Arrays.stream(clas.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && isPublic(method.getModifiers()))
                .forEach(method ->
                        methodsWithBody.forEach(methodDeclaration -> {
                            if (method.getName().equals(methodDeclaration.getNameAsString())) {
                                Set<Pair<String, MethodDeclaration>> methodCalls = findMethodsCall(
                                        methodDeclaration,
                                        methodsWithBody.stream()
                                                .filter(methodDecl -> !methodDecl.getNameAsString().equals(method.getName()))
                                                .collect(Collectors.toList())
                                );
                                Set<Pair<String, Pair<Class<?>, Method>>> beansCalls = findBeansCall(methodDeclaration, beans);
                                if (!methodCalls.isEmpty()) {
                                    methodCalls.forEach(methodCall -> beansCalls.addAll(findBeansCall(methodCall.getRight(), beans)));
                                }
                                createTestMethod(classBuilder, method, clas.getSimpleName(), beansCalls);
                            }
                        }));

        JavaFile javaFile = JavaFile.builder(clas.getPackageName(), classBuilder.build()).indent("    ")
                .skipJavaLangImports(true)
                .addStaticImport(ClassName.get("org.mockito", "Mockito"), "*")
                .addStaticImport(ClassName.get("org.junit.jupiter.api", "Assertions"), "*")
                .build();
        try {
            javaFile.writeToFile(new File("src/test/java/"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<MethodDeclaration> getClassWithMethodsBody(String path) {
        JavaParser javaParser = new JavaParser();
        try {
            ParseResult<CompilationUnit> parse = javaParser.parse(new File(path));
            if (parse.getResult().isPresent()) {
                return parse.getResult().get().findAll(MethodDeclaration.class);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return List.of();
    }

    private static void createClassField(TypeSpec.Builder classBuilder, Field field) {
        if (!field.getType().isEnum() && !field.getType().isPrimitive() && !field.getType().isSynthetic() &&
                !field.getType().equals(String.class) && !field.getType().isAnnotation()) {
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(field.getType(), field.getName())
                    .addModifiers(Modifier.PRIVATE);
            Reflections reflections = new Reflections(field.getType().getPackageName(), new SubTypesScanner(false));
            Set<Class<?>> classes = new HashSet<>();
            try {
                classes = new HashSet<>(reflections.getSubTypesOf(field.getType()));
            } catch (ReflectionsException e) {
                try {
                    classes.add(Class.forName(field.getType().getName()));
                } catch (ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
            if (isMockBean(classes)) {
                fieldBuilder.addAnnotation(MockBean.class);
                classBuilder.addField(fieldBuilder.build());
            }
        }
    }

    private static Set<Pair<String, Pair<Class<?>, Method>>> findBeansCall(MethodDeclaration methodDeclaration, Set<Class<?>> beans) {
        Set<Pair<String, Pair<Class<?>, Method>>> beanMethodCalls = new HashSet<>();
        if (methodDeclaration.getBody().isPresent()) {
            beans.forEach(bean -> {
                String beanName = bean.getSimpleName().substring(0, 1).toLowerCase().concat(bean.getSimpleName().substring(1));
                Arrays.stream(bean.getMethods())
                        .filter(method -> !method.isSynthetic() && isPublic(method.getModifiers()))
                        .forEach(method -> {
                            if (methodDeclaration.getBody().isPresent()) {
                                String body = methodDeclaration.getBody().get().toString();
                                if (body.contains(String.format("%s.%s(", beanName, method.getName())) ||
                                        body.contains(String.format("%s::%s", beanName, method.getName()))) {
                                    beanMethodCalls.add(Pair.of(beanName, Pair.of(bean, method)));
                                }
                            }
                        });
            });
        }
        return beanMethodCalls;
    }

    private static Set<Pair<String, MethodDeclaration>> findMethodsCall(MethodDeclaration methodDeclaration, List<MethodDeclaration> classMethods) {
        Set<Pair<String, MethodDeclaration>> methodsCall = new HashSet<>();
        if (methodDeclaration.getBody().isPresent()) {
            classMethods.forEach(method -> {
                if (methodDeclaration.getBody().isPresent()) {
                    String body = methodDeclaration.getBody().get().toString();
                    if (body.contains(String.format("%s(", method.getNameAsString()))) {
                        methodsCall.add(Pair.of(methodDeclaration.getNameAsString(), method));
                    }
                }
            });
        }
        return methodsCall;
    }

    private static void createTestMethod(TypeSpec.Builder classBuilder, Method method, String selfClassName, Set<Pair<String, Pair<Class<?>, Method>>> beansCalls) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        String.format("%s%s%s", "test", method.getName().substring(0, 1).toUpperCase(), method.getName().substring(1))
                )
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .returns(void.class);
        StringBuilder methodParams = new StringBuilder();
        Arrays.stream(method.getParameters()).forEach(parameter -> {
            initializeParameter(parameter, methodBuilder);
            methodParams.append(parameter.getName()).append(", ");
        });
        beansCalls.forEach(beanCall -> {
            StringBuilder beanCallParameters = new StringBuilder();
            List<Parameter> parameters = Arrays.asList(beanCall.getRight().getRight().getParameters());
            for (int i = 0; i < parameters.size(); i++) {
                if (i != parameters.size() - 1) {
                    beanCallParameters.append("any(), ");
                } else {
                    if (parameters.get(i).getType().isPrimitive()) {
                        try {
                            int finalI = i;
                            Optional<Method> anyMethod = Arrays.stream(Class.forName("org.mockito.ArgumentMatchers").getDeclaredMethods())
                                    .filter(methodAny -> methodAny.getName().startsWith("any") &&
                                            methodAny.getReturnType().equals(parameters.get(finalI).getType()))
                                    .findFirst();
                            anyMethod.ifPresent(value -> beanCallParameters.append(String.format("%s(), ", value.getName())));
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException("Mockito class not found");
                        }
                    } else {
                        beanCallParameters.append("any(), ");
                    }
                }
            }
            if (beanCallParameters.length() > 2) {
                beanCallParameters.delete(beanCallParameters.length() - 2, beanCallParameters.length());
            }

            if (beanCall.getRight().getRight().getReturnType().equals(void.class)) {
                methodBuilder.addStatement(String.format("doNothing().when(%s).%s(%s)",
                        beanCall.getLeft(), beanCall.getRight().getRight().getName(), beanCallParameters));
            } else {
                Pair<String, Pair<Type, Type>> instance = createInstanceWithInstancio(beanCall.getRight());
                if (instance.getRight().getRight() != null) {
                    methodBuilder.addStatement(String.format("when(%s.%s(%s)).thenReturn(%s)",
                            beanCall.getLeft(), beanCall.getRight().getRight().getName(), beanCallParameters, instance.getLeft()),
                            instance.getRight().getRight(), instance.getRight().getLeft()
                    );
                } else {
                    methodBuilder.addStatement(String.format("when(%s.%s(%s)).thenReturn(%s)",
                            beanCall.getLeft(), beanCall.getRight().getRight().getName(), beanCallParameters, instance.getLeft()),
                            instance.getRight().getLeft()
                    );
                }
            }
        });

        String methodCallCode = String.format("%s.%s(%s)",
                selfClassName.substring(0, 1).toLowerCase().concat(selfClassName.substring(1)),
                method.getName(),
                methodParams.length() > 0 ? methodParams.substring(0, methodParams.length() - 2) : "");

        methodBuilder.addStatement(String.format("assertDoesNotThrow(() -> %s)", methodCallCode));
        if (!method.getReturnType().equals(void.class)) {
            methodBuilder.addStatement(String.format("assertNotNull(%s)", methodCallCode));
        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private static Pair<String, Pair<Type, Type>> createInstanceWithInstancio(Pair<Class<?>, Method> mockMethod) {
        List<Class<?>> interfaces = Arrays.asList(mockMethod.getRight().getReturnType().getInterfaces());
        if (interfaces.contains(Collection.class) || interfaces.contains(Map.class) ||
                mockMethod.getRight().getReturnType().equals(Optional.class)) {

            Type[] actualTypeArguments = ((ParameterizedType) mockMethod.getRight().getGenericReturnType()).getActualTypeArguments();

            if (mockMethod.getRight().getReturnType().equals(List.class)) {
                return Pair.of("Instancio.ofList($T.class).create()", Pair.of(actualTypeArguments[0], null));
            } else if (mockMethod.getRight().getReturnType().equals(Set.class)) {
                return Pair.of("Instancio.ofSet($T.class).create()", Pair.of(actualTypeArguments[0], null));
            } else if (mockMethod.getRight().getReturnType().equals(Map.class)) {
                return Pair.of("Instancio.ofMap($T.class).create()", Pair.of(actualTypeArguments[0], null));
            } else {
                Class<?> declaringClass = mockMethod.getRight().getDeclaringClass();
                if (declaringClass.isAssignableFrom(mockMethod.getLeft())) {
                    Type[] actualTypeArgumentsForOptional = ((ParameterizedType) mockMethod.getLeft().getGenericInterfaces()[0]).getActualTypeArguments();
                    return Pair.of("$T.ofNullable(Instancio.create($T.class))", Pair.of(actualTypeArgumentsForOptional[0], Optional.class));
                } else {
                    return Pair.of("Instancio.create($T.class)", Pair.of(actualTypeArguments[0], null));
                }
            }
        } else if (mockMethod.getRight().getReturnType().equals(Object.class)) {
            Class<?> declaringClass = mockMethod.getRight().getDeclaringClass();
            if (declaringClass.isAssignableFrom(mockMethod.getLeft())) {
                Type[] actualTypeArguments = ((ParameterizedType) mockMethod.getLeft().getGenericInterfaces()[0]).getActualTypeArguments();
                return Pair.of("Instancio.create($T.class)", Pair.of(actualTypeArguments[0], null));
            } else {
                return Pair.of("Instancio.create($T.class)", Pair.of(mockMethod.getRight().getReturnType(), null));
            }
        } else {
            return Pair.of("Instancio.create($T.class)", Pair.of(mockMethod.getRight().getReturnType(), null));
        }
    }

    private static void initializeParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        List<Class<?>> interfaces = Arrays.asList(parameter.getType().getInterfaces());
        if (interfaces.contains(Collection.class) || parameter.getType().equals(Map.class)) {

            List<Type> actualTypeArguments = Arrays.asList(((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments());
            StringBuilder initCode = new StringBuilder(String.format("%s<", parameter.getType().getSimpleName()));
            actualTypeArguments.forEach(type ->
                    initCode.append(type.getTypeName().substring(type.getTypeName().lastIndexOf(".") + 1)).append(", "));
            initCode.replace(initCode.length() - 2, initCode.length(), "").append("> ");

            if (parameter.getType().equals(List.class)) {
                methodBuilder.addStatement(String.format("$T<$T> %s = $T.ofList(%s.class).create()",
                                parameter.getName(), actualTypeArguments.get(0).getTypeName()),
                        List.class, actualTypeArguments.get(0), Instancio.class);
            } else if (parameter.getType().equals(Set.class)) {
                methodBuilder.addStatement(String.format("$T<$T> %s = $T.ofSet(%s.class).create()",
                                parameter.getName(), actualTypeArguments.get(0).getTypeName()),
                        Set.class, actualTypeArguments.get(0), Instancio.class);
            } else if (parameter.getType().equals(Map.class)) {
                methodBuilder.addStatement(String.format("$T<$T, $T> %s = $T.ofMap(%s.class, %s.class).create()",
                                parameter.getName(), actualTypeArguments.get(0).getTypeName(), actualTypeArguments.get(1).getTypeName()),
                        Map.class, actualTypeArguments.get(0), actualTypeArguments.get(0), Instancio.class);
            }
        } else {
            methodBuilder.addStatement(String.format("$T %s = $T.create(%s.class)", parameter.getName(), parameter.getType().getSimpleName()),
                    parameter.getType(), Instancio.class);
        }
    }

    private static boolean isMockBean(Collection<Class<?>> classes) {
        return classes.stream()
                .allMatch(clazz -> clazz.getAnnotation(Component.class) != null ||
                        clazz.getAnnotation(Service.class) != null ||
                        clazz.getAnnotation(Repository.class) != null ||
                        Arrays.stream(clazz.getInterfaces())
                                .anyMatch(interFace -> interFace.getSimpleName().equals("JpaRepository")) ||
                        Arrays.stream(clazz.getInterfaces())
                                .anyMatch(interFace -> interFace.getSimpleName().equals("CrudRepository")) ||
                        Arrays.stream(clazz.getInterfaces()).anyMatch(interFace -> interFace.getSimpleName().equals("Mapper"))
                );
    }
}
