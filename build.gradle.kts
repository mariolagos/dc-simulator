plugins {
    id("java")
    id("application")
    id("java-test-fixtures")
    id("com.github.johnrengelman.shadow") version "8.1.1"

}

repositories { mavenCentral() }

java {
    toolchain {
        // Anpassa om du behöver en annan version
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


dependencies {
    // ===== Core dependencies =====
    // Akka Typed (Java API) + SLF4J integration (2.6.20 = sista Apache 2.0 LTS)
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.6.20")
    implementation("com.typesafe.akka:akka-slf4j_2.13:2.6.20")

    // HOCON config
    implementation("com.typesafe:config:1.4.3")

    // Apache Commons Math (för RealMatrix/RealVector m.m.)
    implementation("org.apache.commons:commons-math3:3.6.1")

    implementation("org.apache.commons:commons-csv:1.11.0")

    // ===== Logging (Log4j2 via SLF4J) =====
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1")

    // ===== Test (JUnit 5) =====
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    // ===== Test (JUnit 4) =====
    testImplementation("junit:junit:4.13.2")
    // (valfritt) Hamcrest om dina tester använder det
    testImplementation("org.hamcrest:hamcrest:2.2")

    testFixturesImplementation("junit:junit:4.13.2")
    testFixturesImplementation("org.hamcrest:hamcrest:2.2")


    // Aktivera bara om du använder Akka testkit:
    // testImplementation("com.typesafe.akka:akka-actor-testkit-typed_2.13:2.6.20")

    // Excel (Apache POI)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    implementation("com.google.guava:guava:33.2.1-jre")

    implementation("com.opencsv:opencsv:5.9")
    implementation("com.typesafe:config:1.4.3")

    implementation("info.picocli:picocli:4.7.6")

    testImplementation(sourceSets["testFixtures"].output)

// (valfritt) om du även skriver/parsar CSV någonstans
// implementation("org.apache.commons:commons-csv:1.10.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17) // eller din JDK-version
    }
}


application {
    // 🟢 Detta fungerar endast om 'application'-pluginet är aktivt (plugins-blocket ovan)
    mainClass.set("org.dcsim.DcSimApp")
}


// Konfigurera den befintliga run-tasken (skapa inte en ny!)
tasks.named<JavaExec>("run") {
    standardInput = System.`in`

    // Tillåt gradlew run -PappArgs="..."
    val appArgs = project.findProperty("appArgs") as String?
    if (!appArgs.isNullOrBlank()) {
        args = appArgs.split("\\s+".toRegex())
    }
}

tasks.register("printTestCp") {
    doLast {
        println("TEST compileClasspath:")
        println(sourceSets["test"].compileClasspath.asPath)
        println()
        println("TEST FIXTURES output:")
        println(sourceSets["testFixtures"].output.asPath)
    }
}

tasks.register<JavaExec>("runPivot") {
    group = "application"
    description = "Run the A2 pivot tool (project/<proj>/<scen>)"
    mainClass.set("org.dcsim.tools.LongtablePivotTool")
    classpath = sourceSets.main.get().runtimeClasspath

    // -Pargs="--verbose --excel"
    providers.gradleProperty("args").orNull?.takeIf { it.isNotBlank() }?.let { raw ->
        args(raw.split(Regex("\\s+")))
    }
    // -PconfigFile=project/3subs1train/scenario1/application.conf
    providers.gradleProperty("configFile").orNull?.takeIf { it.isNotBlank() }?.let { cf ->
        jvmArgs("-Dconfig.file=$cf")
    }
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runDcSim") {
    group = "application"
    description = "Run the DC simulator (DcSimApp)"
    mainClass.set("org.dcsim.DcSimApp")
    classpath = sourceSets.main.get().runtimeClasspath

    // -Pargs="--verbose --excel"
    providers.gradleProperty("args").orNull?.takeIf { it.isNotBlank() }?.let { raw ->
        args(raw.split(Regex("\\s+")))
    }
    // -PconfigFile=project/3subs1train/scenario1/application.conf
    providers.gradleProperty("configFile").orNull?.takeIf { it.isNotBlank() }?.let { cf ->
        jvmArgs("-Dconfig.file=$cf")
    }
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("runTrainsPivotWide") {
    group = "tools"
    description = "Pivot trains.csv long -> wide"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.dcsim.tools.TrainsWidePivot")
    args(
        "output/pivots/trains.csv",
        "output/pivots/trains_wide.csv"
    )
}

tasks.register<JavaExec>("runWideExcel") {
    group = "tools"
    description = "Export wide Excel (one sheet per pivot CSV) from a pivot directory"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.dcsim.tools.LongtableWideExcel")

    // Använd -Pargs="pivotDir outFile" om du vill, annars default
    val argsProp = project.findProperty("args") as String?
    if (argsProp != null) {
        args(argsProp.split("\\s+".toRegex()))
    } else {
        // default för 3subs2train2
        args(
            "output/pivots",
            "output/pivots/wide.xlsx"
        )
    }
}

tasks.register<JavaExec>("runLongtableTrainSubWide") {
    group = "tools"
    description = "Export trains + subs wide Excel directly from longtable.csv"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.dcsim.tools.LongtableTrainSubWideExcel")

    val argsProp = project.findProperty("args") as String?
    if (argsProp != null) {
        args(argsProp.split("\\s+".toRegex()))
    } else {
        args(
            "output/pivots/trains.csv",
            "output/pivots/trains_wide.csv"
        )
    }
}


tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}


