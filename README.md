# HMPPS Incentives API

[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-incentives-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-incentives-api)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://incentives-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-incentives-api/main/async-api.yml&readOnly)

This application is the REST api and database that owns incentive level information for prisons
and prisoner incentive reviews.

## Running locally

For running locally against docker instances of the following services:
- [hmpps-auth](https://github.com/ministryofjustice/hmpps-auth)
- [prison-api](https://github.com/ministryofjustice/prison-api)
- run this application independently e.g. in IntelliJ

```shell
docker compose up --scale hmpps-incentives-api=0
```

### Running all services including this service

```shell
docker compose up
```

### Running locally against dev/T3 services

This is straight-forward as authentication is delegated down to the calling services in `dev` environment.

Use all environment variables starting with `API_BASE_URL_` from [helm chart values](./helm_deploy/values-dev.yaml).
Choose a suitable hmpps-auth oauth client, for instance from kubernetes `hmpps-incentives-api` secret and add
`INCENTIVES_API_CLIENT_ID` and `INCENTIVES_API_CLIENT_SECRET`.

Start the database and other required services in docker with:

```shell
docker compose -f docker-compose-local.yml up
```

## Testing and linting

Run unit and integration tests with:

```shell
./gradlew test
```

Run automatic lint fixes:

```shell
./gradlew ktlintformat
```

## Publishing a received message to your local instance

This assumes you have the [AWS CLI](https://aws.amazon.com/cli/) installed

1. Follow [Running Locally](#running-locally) to bring up the service and docker containers
2. Find the ARN of the Domain Events topic created in your localstack instance and update the `topic-arn` parameter in the command below
    ```shell
    aws --endpoint-url=http://localhost:4566 sns publish \
        --topic-arn arn:aws:sns:eu-west-2:000000000000:11111111-2222-3333-4444-555555555555 \
        --message-attributes '{
          "eventType": { "DataType": "String", "StringValue": "prisoner-offender-search.prisoner.received" }
        }' \
        --message '{
          "version": "1.0",
          "occurredAt": "2020-02-12T15:14:24.125533+00:00",
          "publishedAt": "2020-02-12T15:15:09.902048716+00:00",
          "description": "A prisoner has been received into a prison with reason: admission on new charges",
          "additionalInformation": {
            "nomsNumber": "A0289IR",
            "prisonId": "MDI",
            "reason": "NEW_ADMISSION"
          }
        }'
    ```
3. Paste the command into your terminal

**NOTE**: If you get a `Topic does not exist` error, it may mean your default AWS profile points to a different region,
be sure it points to `eu-west-2` either by changing your default profile or by passing `--region eu-west-1` to the
command above.

## Connecting to AWS resources from a local port

There are custom gradle tasks that make it easier to connect to AWS resources (RDS and ElastiCache Redis)
in Cloud Platform from a local port:

```shell
./gradlew portForwardRDS
# and
./gradlew portForwardRedis
```

These could be useful to, for instance, clear out a development database or edit data live.

They require `kubectl` to already be set up to access the kubernetes cluster;
essentially these tasks are just convenience wrappers.

Both accept the `--environment` argument to select between `dev`, `preprod` and `prod` namespaces
or prompt for user input when run.

Both also accept the `--port` argument to choose a different local port, other than the resource’s default.

## Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
