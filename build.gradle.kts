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
}

application {
    mainModule.set("com.juanpablo.evermail.evermail")
    mainClass.set("com.juanpablo.evermail.evermail.HelloApplication")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    // Jakarta Mail - IMAP/SMTP protocols
    implementation("org.eclipse.angus:jakarta.mail:2.0.3")

    // SQLite - local caching
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // dotenv-java - .env loading for OAuth Client Secrets
    implementation("io.github.cdimascio:dotenv-java:3.0.2")

    // java-keyring - OS credential store (Windows Credential Manager / macOS Keychain / Linux Secret Service)
    implementation("com.github.javakeyring:java-keyring:1.0.4")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "app"
    }
}