plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("org.beryx.jlink") version "2.25.0"
    id("io.freefair.lombok") version "8.11"
}

group = "com.juanpablo.evermail"
version = "0.0.1"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

application {
    mainModule.set("com.juanpablo.evermail")
    mainClass.set("com.juanpablo.evermail.App")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.helger:ph-css:7.0.4")
    implementation("org.slf4j:slf4j-api:2.0.9")
    // Explicitly preserve the current no-logging behavior for third-party libraries.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.9")
    // Jakarta Mail - IMAP/SMTP protocols
    implementation("org.eclipse.angus:jakarta.mail:2.0.3")

    // SQLite - local caching
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // dotenv-java - .env loading for OAuth Client Secrets
    implementation("io.github.cdimascio:dotenv-java:3.0.2")

    implementation("com.google.code.gson:gson:2.11.0")

    // java-keyring - OS credential store (Windows Credential Manager / macOS Keychain / Linux Secret Service)
    implementation("com.github.javakeyring:java-keyring:1.0.4")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Exercise the real module path too: unit tests can mask unresolved runtime modules.
tasks.register<JavaExec>("verifyMailRuntime") {
    group = "verification"
    description = "Checks HTML/CSS parsing on the application module path without accounts or network."
    dependsOn(tasks.testClasses)
    // Include filename-derived automatic modules such as java.keyring as well.
    classpath = files()
    mainModule.set("com.juanpablo.evermail")
    mainClass.set("com.juanpablo.evermail.util.MailRuntimeProbe")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    doFirst {
        jvmArgs("--module-path", sourceSets.main.get().runtimeClasspath.asPath,
                "--patch-module", "com.juanpablo.evermail=${sourceSets.test.get().output.classesDirs.asPath}")
    }
}

tasks.check { dependsOn("verifyMailRuntime") }

tasks.register<JavaExec>("diagnoseMail") {
    group = "verification"
    description = "Checks saved mail sessions without sending messages or exposing credentials."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.juanpablo.evermail.diagnostics.MailDiagnostics")
    modularity.inferModulePath.set(false)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

tasks.register<JavaExec>("measureMailReads") {
    group = "verification"
    description = "Measures first and cached reads of up to three messages without logging private data."
    dependsOn(tasks.classes)
    classpath = files()
    mainModule.set("com.juanpablo.evermail")
    mainClass.set("com.juanpablo.evermail.diagnostics.ReadTimings")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    doFirst { jvmArgs("--module-path", sourceSets.main.get().runtimeClasspath.asPath) }
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "app"
    }
}
