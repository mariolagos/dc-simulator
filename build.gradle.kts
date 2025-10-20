plugins {
    id("java")
    id("java-test-fixtures")
    id("jacoco")
}

java {
    // Justera om ni kör annan Java-version
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ===== Main (Java) =====
    implementation("com.typesafe:config:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.commons:commons-math3:3.6.1")

    // Akka Typed (Java-API använder artefakter suffixed med _2.13 men
    // kräver inte scala-plugin i Gradle – de drar in scala-library transitivt)
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.5")
    implementation("com.typesafe.akka:akka-slf4j_2.13:2.8.5")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // ===== Test (JUnit 4) =====
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:2.2")

    // ===== Test Fixtures =====
    // Använd add(...) för maximal kompatibilitet även om typesafe accessors saknas
    add("testFixturesImplementation", "junit:junit:4.13.2")
    add("testFixturesImplementation", "org.hamcrest:hamcrest:2.2")
    add("testFixturesImplementation", "org.apache.commons:commons-math3:3.6.1")
}

sourceSets {
    // Lås test-källor till Java så ev. Scala-test (src/test/scala) ignoreras
    named("test") {
        java.setSrcDirs(listOf("src/test/java"))
        resources.setSrcDirs(listOf("src/test/resources"))
    }
    // Test fixtures (standardplatser används: src/testFixtures/java, src/testFixtures/resources)
    named("testFixtures") {
        // inga specialdir behövs om ni följer standard
    }
}

tasks.test {
    // Viktigt: JUnit 4 (inte useJUnitPlatform)
    useJUnit()
    // Gör verbosity opt-in via JVM-flagga
    jvmArgs("-Ddcsim.verbose=" + (System.getProperty("dcsim.verbose") ?: "false"))

    // Trevligare logg
    testLogging {
        events("FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// (Valfritt) Task för att printa Graphviz-dot om ni använder det i fixtures
// tasks.register("printDotPath") {
//     doLast { println("dot in PATH = " + (System.getenv("GRAPHVIZ_DOT") ?: "dot")) }
// }
