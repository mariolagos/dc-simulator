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
}

application {
    mainClass.set("org.dcsim.Main")
}
