plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.3.0"
}

group = "moe.tachyon"
version = "1.0.0"


application {
    mainClass.set("moe.tachyon.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-io:1.9.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}


kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xmulti-dollar-interpolation")
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        freeCompilerArgs.add("-Xnested-type-aliases")
        freeCompilerArgs.add("-Xdata-flow-based-exhaustiveness")
        freeCompilerArgs.add("-Xallow-reified-type-in-catch")
        freeCompilerArgs.add("-Xallow-holdsin-contract")
    }
}

ktor {
    fatJar {
        allowZip64 = true
        archiveFileName = "SSH_HELPER.jar"
    }
}