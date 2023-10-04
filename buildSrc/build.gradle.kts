import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "uk.gov.justice.digital.hmpps.gradle"
version = "1.0-SNAPSHOT"
description = "Custom gradle tasks"

plugins {
  kotlin("jvm") version "1.9.10"
}

repositories {
  mavenCentral()
}

java {
  toolchain.languageVersion = JavaLanguageVersion.of(20)
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_20.toString()
  }
}
