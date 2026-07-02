import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.5.3"
  kotlin("plugin.jpa") version "2.4.0"
  kotlin("plugin.spring") version "2.4.0"
  id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
  id("jacoco")
  id("org.sonarqube") version "7.3.1.8318"
}

dependencyCheck {
  suppressionFiles.add("reactive-suppressions.xml")
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

repositories {
  maven { url = uri("https://repo.spring.io/milestone") }
  mavenCentral()
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.5.0")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.security:spring-security-access")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:r2dbc-postgresql:1.1.2.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.11")

  // Shedlock dependencies
  implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-r2dbc:7.7.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3")

  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.63.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.29.0")

  implementation("org.apache.commons:commons-lang3")
  implementation("org.apache.commons:commons-text:1.15.0")
  implementation("commons-codec:commons-codec")
  implementation("com.google.code.gson:gson")

  implementation("com.pauldijou:jwt-core_2.11:5.0.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.5.0")
  testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.45") {
    exclude(group = "io.swagger.core.v3")
  }

  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.testcontainers:testcontainers-localstack:2.0.5")
  testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("io.opentelemetry:opentelemetry-sdk:1.63.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-common:1.63.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics:1.63.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.63.0")

  if (project.hasProperty("docs")) {
    implementation("com.h2database:h2")
  }
}

openApi {
  outputDir.set(layout.buildDirectory.dir("docs"))
  outputFileName.set("openapi.json")
  customBootRun.args.set(listOf("--spring.profiles.active=dev,localstack,docs"))
}

kotlin {
  jvmToolchain(25)
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

tasks {
  register<PortForwardRDSTask>("portForwardRDS") {
    namespacePrefix = "hmpps-incentives"
  }

  register<PortForwardRedisTask>("portForwardRedis") {
    namespacePrefix = "hmpps-incentives"
  }

  register<RevealSecretsTask>("revealSecrets") {
    namespacePrefix = "hmpps-incentives"
  }

  withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_25
  }

  test {
    finalizedBy(jacocoTestReport)
  }

  jacocoTestReport {
    dependsOn(test)
    reports {
      xml.required.set(true)
    }
  }
}
