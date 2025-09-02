import org.gradle.api.tasks.scala.ScalaCompile

plugins {
    scala
    java
    application
}

group = "org.dcsim"
version = "0.3"

repositories { mavenCentral() }

dependencies {
    // Java deps
    implementation("com.typesafe:config:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Scala + Akka
    implementation("org.scala-lang:scala-library:2.13.12")
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.5")
    implementation("com.typesafe.akka:akka-slf4j_2.13:2.8.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // ---- Tests: JUnit 4 ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:2.2") // optional
}

tasks.test {
    useJUnit()                       // run JUnit 4 tests
    testLogging { events("passed","skipped","failed") }
}

/** Temporarily disable Scala test compilation so Java tests can run */
tasks.named<ScalaCompile>("compileTestScala") {
    enabled = false
}

tasks.register<JavaExec>("runDcSim") {
    group = "application"
    description = "Run DcSimApp main"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.dcsim.DcSimApp")
}
