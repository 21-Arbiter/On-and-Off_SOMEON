import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

application {
    mainClass = "com.drivemate.server.ApplicationKt"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.junit)
}

// The demo users drive the commute scenarios, so the server ships them.
tasks.processResources {
    from(rootProject.file("tools/scenarios")) { into("scenarios") }
}
