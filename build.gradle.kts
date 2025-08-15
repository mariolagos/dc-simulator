plugins {
    scala
    java
    application
}

group = "org.dcsim"
version = "0.3"

repositories {
    mavenCentral()
}

dependencies {
    // Java
    implementation("com.typesafe:config:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Scala + Akka
    implementation("org.scala-lang:scala-library:2.13.12")
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.5")
    implementation("com.typesafe.akka:akka-slf4j_2.13:2.8.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Test
    testImplementation("junit:junit:4.13.2")
}

dependencies {

    // Akka + SLF4J backend
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.5")
    implementation("com.typesafe.akka:akka-slf4j_2.13:2.8.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    testImplementation("junit:junit:4.13.2")
}


application {
    mainClass.set("org.dcsim.electric.MinimalTest")  // ← du kan ändra detta senare
}

tasks.register("runMinimalTest", JavaExec::class) {
    description = "Runs the MinimalTest class with test resources"
    group = "application"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.dcsim.electric.MinimalTest")
}
