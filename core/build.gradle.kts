import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// The car app (Java 17) uses this module too.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // Commute scenarios are shared with the debug app (seeded trips), so tests read them in place.
    val scenarios = rootProject.file("tools/scenarios")
    inputs.dir(scenarios)
    systemProperty("scenarioDir", scenarios.absolutePath)
}
