# HMPPS Incentives API

[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-incentives-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-incentives-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-incentives-api)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://incentives-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs)



**Incentives Domain Microservice to own the incentives data for prisoners**



## Running locally

For running locally against docker instances of the following services:
- [hmpps-auth](https://github.com/ministryofjustice/hmpps-auth)
- [prison-api](https://github.com/ministryofjustice/prison-api)
- run this application independently e.g. in IntelliJ

`docker-compose up hmpps-auth prison-api`


Running all services including this service

`docker-compose up`


## Running locally against T3 test services

This is straight-forward as authentication is delegated down to the calling services.  Environment variables to be set are as follows:-
```
API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth
API_BASE_URL_PRISON=https://api-dev.prison.service.justice.gov.uk
```

### Runbook


### Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
