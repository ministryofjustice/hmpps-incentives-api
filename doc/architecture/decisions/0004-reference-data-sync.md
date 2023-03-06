# 4. Mastering and Synchronisation of Incentive reference data

[Next >>](9999-end.md)


Date: 2023-03-05

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

Tables Affected in **NOMIS**:

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

### Events to be raised on changes (Insert, update, delete)
- REFERENCE_DATA_CHANGE (REFERENCE_CODE supplied)
- INCENTIVE_PRISON_LEVEL_CHANGE (Prison and Level supplied)
- VISIT_ALLOWANCE_LEVEL_CHANGE (Prison and Level supplied)
- OTHER_PRIVILEGES_LEVEL_CHANGE (Code, prison and level supplied)

## Migration steps

1. Build API endpoints to read and write reference data
2. Syscon to build one way sync service to react to incentive reference data changes
3. Build API endpoints to one time migrate data
4. Build screens to support reference data
5. Setup roles for access to screens
6. Provide links to reference screens based on roles
7. Turn off **OIMOIEPS** screen (with config tool)
8. Disable editing of `IEP_LEVELS` and `IEP_OTH_PRIV` domain types in reference code screen **OUMIRCODE**
9. Migrate data



## Decision

This approach follows the agreed pattern of architecture for "Getting Off NOMIS"

## Consequences


[Next >>](9999-end.md)
