import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.6"
  kotlin("plugin.jpa") version "2.0.0"
  kotlin("plugin.spring") version "2.0.0"
  id("org.springdoc.openapi-gradle-plugin") version "1.8.0"
  id("jacoco")
  id("org.sonarqube") version "5.0.0.4638"
}

dependencyCheck {
  suppressionFiles.add("reactive-suppressions.xml")
  // Please remove the below suppressions once it has been suppressed in the DependencyCheck plugin (see this issue: https://github.com/jeremylong/DependencyCheck/issues/4616)
  suppressionFiles.add("postgres-suppressions.xml")
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
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.0.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:3.1.3")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:r2dbc-postgresql")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql")

  // Shedlock dependencies
  implementation("net.javacrumbs.shedlock:shedlock-spring:5.13.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-r2dbc:5.13.0")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.5.0")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("io.opentelemetry:opentelemetry-api:1.38.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.4.0")

  implementation("org.apache.commons:commons-lang3")
  implementation("org.apache.commons:commons-text:1.12.0")
  implementation("commons-codec:commons-codec")
  implementation("com.google.code.gson:gson")

  implementation("com.pauldijou:jwt-core_2.11:5.0.0")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.awaitility:awaitility-kotlin")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.5")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.22")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.wiremock:wiremock-standalone:3.6.0")
  testImplementation("org.testcontainers:localstack:1.19.8")
  testImplementation("org.testcontainers:postgresql:1.19.8")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("io.opentelemetry:opentelemetry-sdk:1.38.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-common:1.38.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics:1.38.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.38.0")

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
  jvmToolchain(21)
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
    compilerOptions.jvmTarget = JvmTarget.JVM_21
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
