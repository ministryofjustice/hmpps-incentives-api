plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.4.1"
  // enable when gradle 7.5 is released which includes JaCoCo 0.8.8 that supports Java 18
  // id("jacoco")
  id("org.sonarqube") version "3.4.0.2513"
  kotlin("plugin.spring") version "1.7.10"
  kotlin("plugin.jpa") version "1.7.10"
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

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.7")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:r2dbc-postgresql")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql")

  implementation("org.springdoc:springdoc-openapi-webflux-ui:1.6.9")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.9")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.opentelemetry:opentelemetry-api:1.16.0")

  implementation("org.apache.commons:commons-lang3")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("commons-codec:commons-codec")
  implementation("com.google.code.gson:gson")

  implementation("com.pauldijou:jwt-core_2.11:5.0.0")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.awaitility:awaitility-kotlin")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.mockito:mockito-inline")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.1")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:localstack:1.17.3")
  testImplementation("org.testcontainers:postgresql:1.17.3")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}

// enable when gradle 7.5 is released which includes JaCoCo 0.8.8 that supports Java 18
// tasks.test {
//   finalizedBy(tasks.jacocoTestReport)
// }
//
// tasks.jacocoTestReport {
//   dependsOn(tasks.test)
//   reports {
//     xml.required.set(true)
//   }
// }
