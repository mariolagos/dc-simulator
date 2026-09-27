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
    implementation("org.apache.commons:commons-math3:3.6.1")

    // ===== Excel (POI) =====
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.23.1")

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

val studyWorkingDir =
    providers.gradleProperty("workingDir")
        .orElse(project.projectDir.absolutePath)

tasks.register<JavaExec>("dcExporter") {
    group = "application"
    description = "Run DcExporter"
    mainClass.set("org.supply.app.DcExporter")
    classpath = sourceSets.main.get().runtimeClasspath

    workingDir(studyWorkingDir.get())

    providers.gradleProperty("args").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> args(raw.split(Regex("\\s+"))) }
}

tasks.register<JavaExec>("dcCheck") {
    group = "verification"
    description = "Check study input and print electrical route topology without solving"
    mainClass.set("org.supply.app.DcCheck")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir(studyWorkingDir.get())

    val confFile = providers.gradleProperty("confFile").orNull
    if (!confFile.isNullOrBlank()) {
        args(confFile)
    } else {
        providers.gradleProperty("args").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> args(raw.split(Regex("\\s+"))) }
    }
    providers.gradleProperty("routeId").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { args("--route", it) }

    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("dcSolver") {
    group = "application"
    description = "Run DcSolver"
    mainClass.set("org.supply.app.DcSolver")
    classpath = sourceSets.main.get().runtimeClasspath

    workingDir(studyWorkingDir.get())

    providers.gradleProperty("args").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> args(raw.split(Regex("\\s+"))) }

    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("dcReporter") {
    group = "application"
    description = "Run DcReporter"
    mainClass.set("org.supply.app.DcReporter")
    classpath = sourceSets.main.get().runtimeClasspath

    workingDir(studyWorkingDir.get())

    providers.gradleProperty("args").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> args(raw.split(Regex("\\s+"))) }

    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.named("dcSolver") {
    mustRunAfter("dcExporter")
}

tasks.named("dcReporter") {
    mustRunAfter("dcSolver")
}

tasks.register("dcStudy") {
    group = "application"
    description = "Run DcExporter -> DcSolver -> DcReporter"

    dependsOn(
        "dcExporter",
        "dcSolver",
        "dcReporter"
    )
}

tasks.register<Exec>("dcStudyGt") {
    group = "application"
    description = "Run dcStudy and generate the graphical timetable SVG"
    dependsOn("dcStudy")

    val configFile = providers.gradleProperty("args")
    val configuredStudyId = providers.gradleProperty("studyId")
    val splotProjectDir = providers.gradleProperty("splotProjectDir")
        .orElse(providers.environmentVariable("SPLOT_PROJECT_DIR"))

    doFirst {
        if (!configFile.isPresent) {
            throw GradleException("dcStudyGt requires -Pargs=<study.conf>")
        }
        if (!splotProjectDir.isPresent) {
            throw GradleException(
                "dcStudyGt requires -PsplotProjectDir=<allProjects> " +
                    "or environment variable SPLOT_PROJECT_DIR"
            )
        }

        val configuration = file(configFile.get())
        val studyId = configuredStudyId.orNull
            ?: Regex(
                """(?s)\bstudy\s*\{.*?\bid\s*=\s*\"([^\"]+)\""""
            ).find(configuration.readText())?.groupValues?.get(1)
            ?: throw GradleException(
                "Cannot read study.id from $configuration; " +
                    "use -PstudyId=<id> when study is defined in an include"
            )
        val resultsDirectory = file(studyWorkingDir.get())
            .resolve("dc")
            .resolve(studyId)
            .resolve("results")
        val timetableInput = resultsDirectory.resolve(
            "${studyId}_graphical_timetable.csv"
        )
        val markerInput = resultsDirectory.resolve(
            "${studyId}_graphical_timetable_markers.csv"
        )
        val svgOutput = resultsDirectory.resolve(
            "${studyId}_graphical_timetable.svg"
        )
        val splotDirectory = file(splotProjectDir.get())
        val windows = System.getProperty("os.name")
            .lowercase().contains("windows")
        val wrapper = splotDirectory.resolve(
            if (windows) "gradlew.bat" else "gradlew"
        )

        if (!timetableInput.isFile) {
            throw GradleException(
                "Graphical timetable input not found: $timetableInput"
            )
        }
        if (!markerInput.isFile) {
            throw GradleException(
                "Graphical timetable markers not found: $markerInput"
            )
        }
        if (!wrapper.isFile) {
            throw GradleException("SPlot Gradle wrapper not found: $wrapper")
        }

        workingDir(splotDirectory)
        val splotArguments = listOf(
            wrapper.absolutePath,
            ":tools:splot:splotCsv",
            "-Pinput=${timetableInput.absolutePath}",
            "-Pmarkers=${markerInput.absolutePath}",
            "-Poutput=${svgOutput.absolutePath}",
            "-Ptitle=$studyId"
        )
        if (windows) {
            commandLine(listOf("cmd", "/c") + splotArguments)
        } else {
            commandLine(splotArguments)
        }
    }
}

tasks.register<Exec>("dcCheckGt") {
    group = "verification"
    description = "Check study input and generate the planned graphical timetable SVG"
    dependsOn("dcCheck")

    val configFile = providers.gradleProperty("confFile")
        .orElse(providers.gradleProperty("args"))
    val configuredStudyId = providers.gradleProperty("studyId")
    val splotProjectDir = providers.gradleProperty("splotProjectDir")
        .orElse(providers.environmentVariable("SPLOT_PROJECT_DIR"))

    doFirst {
        if (!configFile.isPresent) {
            throw GradleException("dcCheckGt requires -PconfFile=<study.conf>")
        }
        if (!splotProjectDir.isPresent) {
            throw GradleException(
                "dcCheckGt requires -PsplotProjectDir=<allProjects> " +
                    "or environment variable SPLOT_PROJECT_DIR"
            )
        }

        val configuration = file(configFile.get())
        val studyId = configuredStudyId.orNull
            ?: Regex(
                """(?s)\bstudy\s*\{.*?\bid\s*=\s*\"([^\"]+)\""""
            ).find(configuration.readText())?.groupValues?.get(1)
            ?: throw GradleException(
                "Cannot read study.id from $configuration; " +
                    "use -PstudyId=<id> when study is defined in an include"
            )
        val checkDirectory = file(studyWorkingDir.get())
            .resolve("dc")
            .resolve(studyId)
            .resolve("checks")
        val timetableInput = checkDirectory.resolve(
            "${studyId}_graphical_timetable.csv"
        )
        val markerInput = checkDirectory.resolve(
            "${studyId}_graphical_timetable_markers.csv"
        )
        val svgOutput = checkDirectory.resolve(
            "${studyId}_graphical_timetable.svg"
        )
        val splotDirectory = file(splotProjectDir.get())
        val windows = System.getProperty("os.name")
            .lowercase().contains("windows")
        val wrapper = splotDirectory.resolve(
            if (windows) "gradlew.bat" else "gradlew"
        )

        if (!timetableInput.isFile) {
            throw GradleException(
                "Planned graphical timetable input not found: $timetableInput"
            )
        }
        if (!markerInput.isFile) {
            throw GradleException(
                "Graphical timetable markers not found: $markerInput"
            )
        }
        if (!wrapper.isFile) {
            throw GradleException("SPlot Gradle wrapper not found: $wrapper")
        }

        workingDir(splotDirectory)
        val splotArguments = listOf(
            wrapper.absolutePath,
            ":tools:splot:splotCsv",
            "-Pinput=${timetableInput.absolutePath}",
            "-Pmarkers=${markerInput.absolutePath}",
            "-Poutput=${svgOutput.absolutePath}",
            "-Ptitle=$studyId - input check"
        )
        if (windows) {
            commandLine(listOf("cmd", "/c") + splotArguments)
        } else {
            commandLine(splotArguments)
        }
    }
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
