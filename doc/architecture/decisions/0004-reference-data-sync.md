# 4. Mastering and Synchronisation of Incentive reference data

[Next >>](9999-end.md)


Date: 2023-03-05
Updated: 2023-03-21

## Status

Accepted

## Context

This document will cover the approach for the incentive service to master incentive reference data and synchronisation this information back into NOMIS

Reference data for incentives includes:-
- List of values for incentive levels for available to all prisons.  These are 
  - Basic (BAS), 
  - Standard (STD), 
  - Enhanced (ENH), 
  - Enhanced 2 (EN2), 
  - Enhanced 3 (EN3)
- List of incentive levels that apply to a prison
- Spends for each level in each prison
- Visit Allowances (VO and PVO)
- Other privileges

A logical schema of this data could be:-
```mermaid
erDiagram
    Prisoner ||--o{ Incentive_Review : is_reviewed_in
    Prison_Incentive_Level ||--o{ Incentive_Review : has_entries
    Prison_Incentive_Level ||--o{ VisitAllowance: has_visit_attributes
    Prison_Incentive_Level ||--o{ Privileges: has_priv_attributes
    Incentive_Level ||--o{ Prison_Incentive_Level: define_allowed_levels
   
```

### Tables Affected in **NOMIS**:

- REFERENCE_CODES for where `DOMAIN = 'IEP_LEVEL' OR DOMAIN = 'IEP_OTH_PRIV'`
```oracle
 CREATE TABLE "REFERENCE_CODES"
 (
   "DOMAIN"                        VARCHAR2(12)                      NOT NULL,
   "CODE"                          VARCHAR2(12)                      NOT NULL,
   "DESCRIPTION"                   VARCHAR2(40)                      NOT NULL,
   "LIST_SEQ"                      NUMBER(6, 0),
   "ACTIVE_FLAG"                   VARCHAR2(1)  DEFAULT 'Y'          NOT NULL,
   CONSTRAINT "REFERENCE_CODES_PK" PRIMARY KEY ("DOMAIN", "CODE")
 )
```
Levels are set with the DOMAIN of `IEP_LEVELS` as a central admin user on the Reference Codes screen ![](reference_codes.png) 

The `IEP_OTH_PRIV` domain allows extra privileges to be added ![](other_privs_ref.png)

**There are only 3 active privileges in production**
             
| Code | Description       | Active |
|------|-------------------|--------|
| INET | Internet Access   | Y      |
| IPOD | Apple IPOD Player | N      |
| PP   | Piano Practice    | Y      |
| SP2  | Sony Playstation  | Y      |


The **OIMOIEPS** NOMIS screen allows config of levels, visits and other privilages.

- IEP_LEVELS 
```oracle
  CREATE TABLE "IEP_LEVELS"
  (
    "IEP_LEVEL"                     VARCHAR2(12)                      NOT NULL,
    "AGY_LOC_ID"                    VARCHAR2(6)                       NOT NULL,
    "ACTIVE_FLAG"                   VARCHAR2(1)                       NOT NULL,
    "EXPIRY_DATE"                   DATE,
    "USER_ID"                       VARCHAR2(40),
    "DEFAULT_FLAG"                  VARCHAR2(1)                       NOT NULL,
    "REMAND_TRANSFER_LIMIT"         NUMBER(12, 2),
    "REMAND_SPEND_LIMIT"            NUMBER(12, 2),
    "CONVICTED_TRANSFER_LIMIT"      NUMBER(12, 2),
    "CONVICTED_SPEND_LIMIT"         NUMBER(12, 2),
    CONSTRAINT "IEP_LEVELS_PK" PRIMARY KEY ("IEP_LEVEL", "AGY_LOC_ID")
  )
```
This screen represents the IEP Levels ![](level_spending_limit.png)

- VISIT_ALLOWANCE_LEVELS (VO column is `REMAND_VISITS` and PVO column is `WEEKENDS`)
- The `HOURS` column is always blank (except for 3 records created in 2008)
- The `VISIT_TYPE` is always `SENT_VISIT` (except for 3 records created in 2008)
```oracle
 CREATE TABLE "VISIT_ALLOWANCE_LEVELS"
 (
   "IEP_LEVEL"                     VARCHAR2(12)                      NOT NULL,
   "AGY_LOC_ID"                    VARCHAR2(6)                       NOT NULL,
   "VISIT_TYPE"                    VARCHAR2(12)                      NOT NULL,
   "REMAND_VISITS"                 NUMBER(3, 0),
   "WEEKENDS"                      NUMBER(3, 0),
   "HOURS"                         NUMBER(3, 0),
   "ACTIVE_FLAG"                   VARCHAR2(1)                       NOT NULL,
   "EXPIRY_DATE"                   DATE,
   "USER_ID"                       VARCHAR2(40),
   CONSTRAINT "VISIT_ALLOWANCE_LEVELS_PK" PRIMARY KEY ("IEP_LEVEL", "AGY_LOC_ID", "VISIT_TYPE")
 )
```
This screen represents the Visit Allowances ![](visitor_allowance.png)


- OTHER_PRIVILEGES_LEVELS (column = `IEP_LEVEL`)

```oracle
CREATE TABLE "OTHER_PRIVILEGES_LEVELS"
(
  "PRIVILEGE_CODE" VARCHAR2(12) NOT NULL,
  "AGY_LOC_ID"     VARCHAR2(6)  NOT NULL,
  "IEP_LEVEL"      VARCHAR2(12) NOT NULL,
  "ACTIVE_FLAG"    VARCHAR2(1)  NOT NULL,
  "EXPIRY_DATE"    DATE,
  "USER_ID"        VARCHAR2(40),
  CONSTRAINT "OTH_LEV_IEP_LEV_PK" PRIMARY KEY ("PRIVILEGE_CODE", "AGY_LOC_ID", "IEP_LEVEL")
)
```

This screen represents the Other Privileges ![](other_privs.png)

In production there are only 12 records across all prisons for 1 privilege (Play Station PS2) for privilages and most records were added over 8 years ago.

| Code | Prison Id | Min Incentive Level | Active |
|------|-----------|---------------------|--------|
| SP2  | PKI       | ENH                 | Y      |
| SP2  | HDI       | ENH                 | Y      |
| SP2  | WDI       | ENH                 | Y      |
| SP2  | LEI       | ENH                 | N      |
| SP2  | LWI       | ENH                 | Y      |
| SP2  | FMI       | ENH                 | Y      |
| SP2  | CLI       | ENH                 | Y      |
| SP2  | IWI       | ENH                 | Y      |
| SP2  | SKI       | ENH                 | Y      |
| SP2  | WWI       | ENH                 | Y      |
| SP2  | HCI       | ENH                 | Y      |
| SP2  | SHI       | ENH                 | Y      |


## Domain Events

### Reference Data Changes
When a change is made to reference data, one of four events can be fired. The sequence of events for syncing back to NOMIS is show below:

```mermaid
sequenceDiagram

    actor Prison Staff
    participant DPS
    participant Incentives API
    participant Incentives Database
    participant Domain Events
    participant HMPPS Prisoner to NOMIS update
    participant HMPPS NOMIS Prisoner API
    participant NOMIS DB

    Prison Staff ->> DPS: Add Incentive Reference data 
    
    DPS ->> Incentives API: Call API with changes
    activate Incentives API
    Incentives API->>Incentives Database: update DB
    Incentives API->>Domain Events: domain event raised
    Note over Incentives API,Domain Events: INCENTIVE_LEVEL_REFERENCE_DATA_[INSERTED/UPDATED]
    Incentives API-->>DPS: Reference Data updated returned
    deactivate Incentives API
    
    Domain Events-->>HMPPS Prisoner to NOMIS update: Receives INCENTIVE_LEVEL_REFERENCE_DATA_* domain event
    activate HMPPS Prisoner to NOMIS update
    HMPPS Prisoner to NOMIS update->>HMPPS NOMIS Prisoner API: Update NOMIS with reference data
    HMPPS NOMIS Prisoner API ->> NOMIS DB: Persist data into the IEP_LEVELS, VISIT_ALLOWANCE_LEVELS tables
    deactivate HMPPS Prisoner to NOMIS update

```

#### Event Types:
In both instances the domain event will contain the code of the reference data.
- INCENTIVE_LEVEL_REFERENCE_DATA_INSERTED 
- INCENTIVE_LEVEL_REFERENCE_DATA_UPDATED

Note these should be the standard way of notifying about reference data changes for all NOMIS related reference data.
**Example:**
```json
{
  "eventType": "INCENTIVE_LEVEL_REFERENCE_DATA_INSERTED",
  "occurredAt": "2023-03-07T14:45:00",
  "version": "1.0",
  "description": "Reference data Incentive Level added : EN4",
  "additionalInformation": {
    "code": "EN4"
  }
}
```

### Prison Incentive Level Changes
These events are raised when changes are made to add or update incentive levels and associated data for a prison

#### Event Types:
- INCENTIVE_PRISON_LEVEL_INSERTED
- INCENTIVE_PRISON_LEVEL_UPDATED

**Example:**
```json
{
  "eventType": "INCENTIVE_PRISON_LEVEL_INSERTED",
  "occurredAt": "2023-03-07T15:45:00",
  "version": "1.0",
  "description": "Added EN4 to prison MDI",
  "additionalInformation": {
    "prisonId": "MDI",
    "incentiveLevel": "EN4"
  }
}
```


## API endpoints

### Read endpoints for incentive levels and prisons’ associated information

Authorised requests do not require any roles.

#### Get a list of active incentive levels globally of all prisons
`GET /incentive/levels` - 
```json5
[
  {
    "code": "BAS",
    "description": "Basic",
    "active": true
  },
  {
    "code": "STD",
    "description": "Standard",
    "active": true
  },
  // more entries…
]
```

#### Get a list of all incentive levels globally of all prisons
`GET /incentive/levels?with-inactive=true` -
```json5
[
  {
    "code": "BAS",
    "description": "Basic",
    "active": true
  },
  {
    "code": "STD",
    "description": "Standard",
    "active": true
  },
  // more entries…
  {
    "code": "ENT",
    "description": "Entry",
    "active": false
  }
]
```


#### Get the details of all incentive levels for a specified prison
This contains spend limits and visit allowances

`GET /incentive/prison-levels/{prisondId}` -

e.g. **/incentive/prison-levels/MDI** returns
```json5
[
  {
    "levelCode": "STD",
    "levelDescription": "Standard",
    "prisonId": "MDI",
    "active": true,
    "defaultOnAdmission": true,
    "remandTransferLimitInPence": 5500,
    "remandSpendLimitInPence": 55000,
    "convictedTransferLimitInPence": 1800,
    "convictedSpendLimitInPence": 18000,
    "visitOrders": 2,
    "privilegedVisitOrders": 1
  },
  // more entries…
]
```

#### Get the details of an incentive level for a specified prison
This contains spend limits and visit allowances

`GET /incentive/prison-levels/{prisondId}/level/{level}` -

e.g. **/incentive/prison-levels/MDI/level/STD** returns
```json
{
  "levelCode": "STD",
  "levelDescription": "Standard",
  "prisonId": "MDI",
  "active": true,
  "defaultOnAdmission": true,
  "remandTransferLimitInPence": 5500,
  "remandSpendLimitInPence": 55000,
  "convictedTransferLimitInPence": 1800,
  "convictedSpendLimitInPence": 18000,
  "visitOrders": 2,
  "privilegedVisitOrders": 1
}
```


### Write endpoints for reference data

Authorised requests will require roles with write scope.

#### Add a new incentive level
`POST /incentive/levels/{code}` -
```json
{
  "code": "EN4",
  "description": "Enhanced 4",
  "active": true
}
```

#### Update an existing incentive level
`PUT /incentive/levels/{code}` -
```json
{
  "code": "STD",
  "description": "Silver",
  "active": true
}
```

`PATCH /incentive/levels/{code}` -
```json
{
  "description": "Silver"
}
```

#### Deactivate an existing incentive level

`DELETE /incentive/levels/{code}`

#### Change order of incentive levels globally

`PATCH /incentive/level-order` -
```json
["BAS", "ENT", "STD", "ENH", "EN2", "EN3"]
```

#### Update an incentive level’s associated information in a prison

`PUT /incentive/prison-levels/{prisonId}/level/{levelCode}` -
```json
{
  "levelCode": "STD",
  "levelDescription": "Standard",
  "prisonId": "MDI",
  "active": true,
  "defaultOnAdmission": true,
  "remandTransferLimitInPence": 5500,
  "remandSpendLimitInPence": 55000,
  "convictedTransferLimitInPence": 1800,
  "convictedSpendLimitInPence": 18000,
  "visitOrders": 2,
  "privilegedVisitOrders": 1
}
```

`PATCH /incentive/prison-levels/{prisonId}/level/{levelCode}` -
```json
{
  "remandTransferLimitInPence": 5500,
  "remandSpendLimitInPence": 55000
}
```

#### Deactivate a level in a prison

`DELETE /incentive/prison-levels/{prisonId}/level/{levelCode}`

## Migration steps

1. Build API endpoints to read and write reference data
2. SYSCON to build one way sync service to react to incentive reference data changes
3. Build screens to support reference data
4. Setup roles for access to screens
5. Provide links to reference screens based on roles
6. Migrate data manually
7. Turn off **OIMOIEPS** screen (with config tool)
8. Disable editing of `IEP_LEVELS` domain types in reference code screen **OUMIRCODE** (optional)




## Decision
- Other privileges data will NOT be moved off NOMIS as it is not used
- SYSCON will not migrate data - a one off SQL script will set-up the data in the incentive DB
- One way sync only will be performed
- NOMIS screens can be turned off / made read only - but timescale is not urgent as this data changes very infrequently



## Consequences


[Next >>](9999-end.md)
