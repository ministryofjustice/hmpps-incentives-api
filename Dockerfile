FROM eclipse-temurin:19-jre-jammy AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ gradle/
RUN ./gradlew build || return 0

ARG BUILD_NUMBER
ENV BUILD_NUMBER ${BUILD_NUMBER:-1_0_0}

COPY . .
RUN ./gradlew clean assemble -Dorg.gradle.daemon=false

FROM eclipse-temurin:19-jre-alpine
LABEL maintainer="HMPPS Digital Studio <info@digital.justice.gov.uk>"

RUN apk --no-cache upgrade

ENV TZ=Europe/London
RUN ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime && echo "$TZ" > /etc/timezone

RUN addgroup --gid 2000 --system appgroup && \
    adduser --u 2000 --system appuser 2000

# Install AWS RDS Root cert into Java truststore
RUN mkdir /home/appuser/.postgresql
ADD --chown=appuser:appgroup https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem /home/appuser/.postgresql/root.crt

WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /app/build/libs/hmpps-incentives-api*.jar /app/app.jar
COPY --from=builder --chown=appuser:appgroup /app/build/libs/applicationinsights-agent*.jar /app/agent.jar
COPY --from=builder --chown=appuser:appgroup /app/applicationinsights.json /app
COPY --from=builder --chown=appuser:appgroup /app/applicationinsights.dev.json /app

USER 2000

ENTRYPOINT ["java", "-XX:+AlwaysActAsServerClassMachine", "-javaagent:/app/agent.jar", "-jar", "/app/app.jar"]
