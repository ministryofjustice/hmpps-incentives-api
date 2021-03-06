# HMPPS Incentives API

[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-incentives-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-incentives-api)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://incentives-api-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html?configUrl=/v3/api-docs)
[![Repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.data%5B%3F%28%40.name%20%3D%3D%20%22hmpps-incentives-api%22%29%5D.status&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fgithub_repositories)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/github_repositories#hmpps-incentives-api "Link to report")

**Incentives Domain Microservice to own the incentives data for prisoners**

## Running locally

For running locally against docker instances of the following services:
- [hmpps-auth](https://github.com/ministryofjustice/hmpps-auth)
- [prison-api](https://github.com/ministryofjustice/prison-api)
- run this application independently e.g. in IntelliJ

`docker-compose up --scale hmpps-incentives-api=0`

## Running all services including this service

`docker-compose up`

## Running locally against T3 test services

This is straight-forward as authentication is delegated down to the calling services.  Environment variables to be set are as follows:-
```
API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth
API_BASE_URL_PRISON=https://api-dev.prison.service.justice.gov.uk
```

## Running integration tests

Before running integration tests you need to start a localstack instance

`docker-compose up localstack`

## Publishing a received message to your local instance

This assumes you have the [AWS CLI](https://aws.amazon.com/cli/) installed

1. Follow [Running Locally](#running-locally) to bring up the service and docker containers
2. Find the ARN of the Domain Events topic created in your localstack instance and update the `topic-arn` parameter in the command below
```
aws --endpoint-url=http://localhost:4566 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:[find this in the Incentives API log for HmppsTopicFactory] \
    --message-attributes '{
      "eventType": { "DataType": "String", "StringValue": "prison-offender-events.prisoner.received" }
    }' \
    --message '{
      "version":"1.0",
      "occurredAt":"2020-02-12T15:14:24.125533Z",
      "publishedAt":"2020-02-12T15:15:09.902048716Z",
      "description":"A prisoner has been received into prison",
      "additionalInformation": {
        "nomsNumber":"A0289IR",
        "prisonId":"MDI",
        "reason":"ADMISSION",
        "details":"ecall referral date 2021-05-12"
      }
    }'
```
3. Paste the command into your terminal

### Runbook


### Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
