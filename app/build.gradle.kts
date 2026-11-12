import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Machine-specific settings live in local.properties (not committed).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val withoutDevCa = providers.gradleProperty("withoutDevCa").isPresent
// The release key stays on this PC too (local.properties drivemate.release.storeFile/storePassword/keyAlias/keyPassword)
val releaseKey = localProps.getProperty("drivemate.release.storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.drivemate.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.drivemate.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // Matching server: the one on Render, unless local.properties drivemate.serverUrl or -PserverUrl=... says
        // otherwise (the emulator reaches a dev server on this PC as http://10.0.2.2:8080, in debug builds only)
        val serverUrl = providers.gradleProperty("serverUrl").orNull ?: localProps.getProperty("drivemate.serverUrl", "https://drive-mate-0ub6.onrender.com")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey
                storePassword = localProps.getProperty("drivemate.release.storePassword")
                keyAlias = localProps.getProperty("drivemate.release.keyAlias")
                keyPassword = localProps.getProperty("drivemate.release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Without the release key (another PC), signed with the debug key: it still installs and runs
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
                // not kotlin.**: the Pleos Vehicle SDK reads its classes with kotlin-reflect, which R8's changes broke
                packageScope = setOf("androidx.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

/**
 * Writes the debug build's network security config: plain http:// to the dev server on
 * this PC (10.0.2.2), and, when [caCert] is set, trust in a TLS-inspecting proxy's root
 * CA (some office networks re-sign every HTTPS site, which breaks https:// to the cloud).
 * The certificate comes from local.properties (drivemate.devCaCert), never from git,
 * and release builds don't get any of this.
 */
abstract class DebugNetworkConfigTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    abstract val caCert: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val res = outputDir.get().asFile.apply { deleteRecursively() }
        val cert = caCert.orNull?.asFile
        val trust = if (cert == null) "" else {
            res.resolve("raw").mkdirs()
            cert.copyTo(res.resolve("raw/dev_proxy_ca.pem"), overwrite = true)
            """
            |    <base-config>
            |        <trust-anchors>
            |            <certificates src="system" />
            |            <certificates src="@raw/dev_proxy_ca" />
            |        </trust-anchors>
            |    </base-config>
            |""".trimMargin()
        }
        res.resolve("xml").mkdirs()
        res.resolve("xml/network_security_config.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<network-security-config>
            |    <domain-config cleartextTrafficPermitted="true">
            |        <domain includeSubdomains="false">10.0.2.2</domain>
            |    </domain-config>
            |$trust</network-security-config>
            |""".trimMargin()
        )
    }
}

/** The demo's recordings of the driver (the .pcm files in tools/voice) as assets, for DebugReceiver's hear and talk. */
abstract class DemoVoicesTask : DefaultTask() {
    @get:InputFiles
    abstract val recordings: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        recordings.files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        // Every build can seed a week of trips from the commute scenarios and hear the driver's
        // recorded lines (DebugReceiver, adb only): an emulator has neither a week of driving nor a microphone.
        variant.sources.assets?.addStaticSourceDirectory(rootProject.file("tools/scenarios").path)
        val demoVoices = tasks.register<DemoVoicesTask>("${variant.name}DemoVoices") {
            recordings.from(rootProject.layout.projectDirectory.dir("tools/voice").asFileTree.matching { include("*.pcm") })
            outputDir.set(layout.buildDirectory.dir("generated/demoVoices/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(demoVoices, DemoVoicesTask::outputDir)
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        val task = tasks.register<DebugNetworkConfigTask>("${variant.name}NetworkConfig") {
            // -PwithoutDevCa: an APK to hand out, so the office proxy's CA stays on this PC
            localProps.getProperty("drivemate.devCaCert")?.takeUnless { withoutDevCa }?.let { caCert.set(file(it)) }
            outputDir.set(layout.buildDirectory.dir("generated/networkConfig/${variant.name}"))
        }
        variant.sources.res?.addGeneratedSourceDirectory(task, DebugNetworkConfigTask::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.navihelper)

    implementation(project(":core"))
    implementation(libs.zxing.core) // QR code for verifying on the phone
    implementation(libs.okhttp) // 보이스톡 audio over a WebSocket

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
