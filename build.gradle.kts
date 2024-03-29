val ktorVersion: String by project
val kordVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    id("com.github.johnrengelman.shadow")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("dev.kord:kord-gateway:$kordVersion")
    implementation("ch.qos.logback:logback-classic:1.4.7")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-resources:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:2.3.0")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
        filteringCharset = "UTF-8"
    }
    shadowJar {
        manifest {
            attributes["Main-Class"] = "com.koisv.hufscord.MainKt"
        }
        project.setProperty("mainClassName", "com.koisv.hufscord.MainKt")
        archiveClassifier.set("release")
        archiveVersion.set("HufsCord-R1.0")
    }
    create<Copy>("dist") {
        from (shadowJar)
        into(".\\out\\")
    }
}