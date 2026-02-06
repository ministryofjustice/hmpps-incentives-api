import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "uk.gov.justice.digital.hmpps.gradle"
version = "1.0-SNAPSHOT"
description = "Custom gradle tasks"

plugins {
  kotlin("jvm") version "2.3.10"
}

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(25)
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

tasks {
  withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_24
  }
}
