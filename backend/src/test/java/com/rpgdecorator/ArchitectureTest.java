package com.rpgdecorator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureTest {

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_DECLARATION =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.$]+(?:\\.\\*)?)\\s*;",
                    Pattern.MULTILINE);
    private static final List<String> FORBIDDEN_LIBRARIES = List.of(
            "org.springframework",
            "jakarta",
            "javax.servlet",
            "javax.enterprise",
            "javax.inject",
            "javax.annotation",
            "javax.persistence",
            "javax.ejb",
            "javax.faces",
            "javax.validation",
            "javax.transaction",
            "javax.xml.bind",
            "javax.ws.rs",
            "com.fasterxml.jackson",
            "org.codehaus.jackson",
            "com.google.gson",
            "lombok",
            "com.google.common",
            "org.apache.commons",
            "org.aspectj",
            "org.aopalliance",
            "net.sf.cglib",
            "net.bytebuddy",
            "org.javassist",
            "javassist",
            "org.jboss.aop",
            "org.jodd",
            "com.google.inject"
    );

    @Test
    void productionSourcesRespectLayerDependenciesAndForbiddenImports() throws IOException {
        Path sourceRoot = findMainJavaRoot();
        List<Path> sourceFiles;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            sourceFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
        assertFalse(sourceFiles.isEmpty(), "No production Java sources found under " + sourceRoot);

        List<String> violations = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            inspectSource(sourceRoot, sourceFile, violations);
        }
        assertTrue(violations.isEmpty(), () -> "Architecture violations:\n" + String.join("\n", violations));
    }

    private static void inspectSource(Path sourceRoot, Path sourceFile, List<String> violations)
            throws IOException {
        String source = Files.readString(sourceFile);
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(source);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        String relativePath = sourceRoot.relativize(sourceFile).toString().replace('\\', '/');

        Matcher importMatcher = IMPORT_DECLARATION.matcher(source);
        while (importMatcher.find()) {
            String importedType = importMatcher.group(1);
            if (isForbiddenImport(importedType)) {
                violations.add(relativePath + " imports forbidden type " + importedType);
            }
            if (isInPackage(packageName, "com.rpgdecorator.domain")
                    && importsPackage(importedType, "com.rpgdecorator.engine")) {
                violations.add(relativePath + " imports engine from domain: " + importedType);
            }
            if (isInPackage(packageName, "com.rpgdecorator.domain")
                    && importsPackage(importedType, "com.rpgdecorator.api")) {
                violations.add(relativePath + " imports api from domain: " + importedType);
            }
            if (isInPackage(packageName, "com.rpgdecorator.domain")
                    && importsPackage(importedType, "com.rpgdecorator.infrastructure")) {
                violations.add(relativePath + " imports infrastructure from domain: " + importedType);
            }
            if (isInPackage(packageName, "com.rpgdecorator.engine")
                    && importsPackage(importedType, "com.rpgdecorator.api")) {
                violations.add(relativePath + " imports api from engine: " + importedType);
            }
        }
    }

    private static boolean isForbiddenImport(String importedType) {
        if (importedType.equals("java.lang.reflect.Proxy")
                || importedType.startsWith("java.lang.reflect.Proxy.")
                || importedType.equals("java.lang.reflect.*")) {
            return true;
        }
        return FORBIDDEN_LIBRARIES.stream().anyMatch(prefix -> importsPackage(importedType, prefix));
    }

    private static boolean isInPackage(String packageName, String expectedPackage) {
        return packageName.equals(expectedPackage) || packageName.startsWith(expectedPackage + ".");
    }

    private static boolean importsPackage(String importedType, String packageName) {
        return importedType.equals(packageName)
                || importedType.startsWith(packageName + ".")
                || importedType.equals(packageName + ".*");
    }

    private static Path findMainJavaRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            for (Path candidate : List.of(directory.resolve("src/main/java"),
                    directory.resolve("backend/src/main/java"))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("Could not locate backend/src/main/java from the working directory");
    }
}
