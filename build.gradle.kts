import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.5.1"
}

group = "dev.snowflake"
version = "1.0.0"

val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/releases/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.flywaydb:flyway-core:11.8.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.8.2")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "192m"
    maxParallelForks = 1
    jvmArgs(
        "-XX:+UseSerialGC",
        "-XX:MaxMetaspaceSize=128m",
        "-XX:ReservedCodeCacheSize=48m",
        "-XX:ActiveProcessorCount=1",
        "-Xss256k"
    )
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("LootBox")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE")
    manifest.attributes["Implementation-Title"] = "LootBox"
    manifest.attributes["Implementation-Version"] = project.version
}

tasks.register<Copy>("releaseJar") {
    group = "build"
    description = "Builds, tests, and copies the deployable plugin JAR to ../build_file."
    dependsOn(tasks.named("test"), tasks.named("shadowJar"))
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../build_file"))
}

tasks.named("build") {
    dependsOn("releaseJar")
}

