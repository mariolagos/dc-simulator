plugins {
    java
    application
}

group = "org.supply"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ===== Core =====
    implementation("com.typesafe:config:1.4.3")

    // ===== Excel (POI) =====
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // ===== Akka Typed =====
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.5")

    // ===== CSV / CLI =====
    implementation("com.opencsv:opencsv:5.9")
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.commons:commons-csv:1.10.0")

    // ===== Test (JUnit 4) =====
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:2.2")

    testImplementation("org.mockito:mockito-core:5.12.0")
}

application {
    // Default program (used by `gradlew run`)
    mainClass.set("org.supply.app.DcExporter")
}

tasks.test {
    useJUnit() // JUnit 4
}

//
// ===== Existing run tasks =====
//

tasks.register<JavaExec>("runPivot") {
    group = "application"
    description = "Run PivotToCsvExporter"
    mainClass.set("org.supply.app.PivotToCsvExporter")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runDcSim") {
    group = "application"
    description = "Run DcSimApp"
    mainClass.set("org.dcsim.DcSimApp")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("dcExporter") {
    group = "application"
    description = "Run DcExporter explicitly"
    mainClass.set("org.supply.app.DcExporter")
    classpath = sourceSets.main.get().runtimeClasspath

    providers.gradleProperty("args").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> args(raw.split(Regex("\\s+"))) }

    jvmArgs("-Dfile.encoding=UTF-8")
}

//
// ===== NEW: Track Debug =====
//

tasks.register<JavaExec>("trackDebug") {
    group = "application"
    description = "Run TrackDebugMain for simplified track debugging"

    mainClass.set("org.supply.app.TrackDebugMain")
    classpath = sourceSets.main.get().runtimeClasspath

    val confFile = providers.gradleProperty("confFile").orNull
    val coordinate = providers.gradleProperty("coordinate").orNull

    if (!confFile.isNullOrBlank()) {
        args(confFile)
    }

    if (!coordinate.isNullOrBlank()) {
        args(coordinate)
    }

    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

sourceSets {
    create("trackTest") {
        java {
            srcDir("src/test/java")
            include("org/supply/track/**")
        }

        compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
    }
}

configurations.named("trackTestImplementation") {
    extendsFrom(configurations["testImplementation"])
}

configurations.named("trackTestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
}

tasks.register<Test>("trackTest") {
    description = "Run only track-related tests"
    group = "verification"

    testClassesDirs = sourceSets["trackTest"].output.classesDirs
    classpath = sourceSets["trackTest"].runtimeClasspath

    useJUnit()
}

tasks.register<JavaExec>("trackExport") {
    group = "application"
    description = "Export track CSVs without loading grid model"

    mainClass.set("org.supply.app.TrackExporterMain")
    classpath = sourceSets.main.get().runtimeClasspath

    val confFile = providers.gradleProperty("confFile").orNull
    val outputDir = providers.gradleProperty("outputDir").orNull

    if (!confFile.isNullOrBlank()) {
        args(confFile)
    }

    if (!outputDir.isNullOrBlank()) {
        args(outputDir)
    }

    jvmArgs("-Dfile.encoding=UTF-8")
}