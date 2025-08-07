plugins {
    java
    application
}

group = "org.dcsim"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.typesafe:config:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    testImplementation("junit:junit:4.13.2")
}

// Standard main class
application {
    mainClass.set("org.dcsim.electric.MinimalTest")
}
group = "org.dcsim"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.typesafe:config:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
}

application {
    mainClass.set("org.dcsim.electric.MinimalTest")
}

tasks.register("runMinimalTest", JavaExec::class) {
    description = "Runs the MinimalTest class with test resources"
    group = "application"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.dcsim.electric.MinimalTest")
}
