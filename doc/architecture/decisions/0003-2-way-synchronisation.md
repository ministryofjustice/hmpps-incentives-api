# 3. Two-way synchronisation between NOMIS and incentives service

[Next >>](9999-end.md)


Date: 2023-02-09

## Status

Accepted

## Context

In [ADR-0002](0002-incentive-api-architecture.md) the future scope was to synchronise data back into NOMIS. These sequence diagrams
detail the two way sync that is performed between systems


## Incentive creation in DPS
The resultant flow looks like

```mermaid
sequenceDiagram

    actor Prison Staff
    participant DPS
    participant Incentives API
    participant Prison API
    participant Domain Events
    participant HMPPS Prisoner to NOMIS update
    participant HMPPS NOMIS Prisoner API
    participant NOMIS DB

    Prison Staff ->> DPS: Record Incentive
    
    DPS ->> Incentives API: Call API
    activate Incentives API
    Incentives API->>Prison API: Prisoner Information retrieved
    Incentives API->>Domain Events: incentives.iep-review.* domain event raised
    Note over Incentives API,Domain Events: incentives.iep-review.[inserted/updated/deleted]
    Incentives API-->>DPS: Incentive Created and returned
    deactivate Incentives API
    
    Domain Events-->>HMPPS Prisoner to NOMIS update: Receives incentives.iep-review.* domain event
    activate HMPPS Prisoner to NOMIS update
    HMPPS Prisoner to NOMIS update->>HMPPS NOMIS Prisoner API: Update NOMIS with new incentive
    HMPPS NOMIS Prisoner API ->> NOMIS DB: Persist data into the OFFENDER_IEP_LEVELS table
    deactivate HMPPS Prisoner to NOMIS update
```

## Incentive creation in NOMIS
The resultant flow looks like

```mermaid
sequenceDiagram

    actor Prison Staff
    participant NOMIS
    participant NOMIS DB
    participant Oracle Queue
    participant Offender Events
    participant Offender Events Topic (SNS)
    participant HMPPS Prisoner from NOMIS migration
    participant Incentives API

    Prison Staff ->> NOMIS: Record Incentive in IEP screen
    activate NOMIS    
    NOMIS ->> NOMIS DB: Store in OFFENDER_IEP_LEVELS table
    NOMIS DB -->> Oracle Queue: Trigger adds IEP change to queue
    deactivate NOMIS  
    
    Oracle Queue -->> Offender Events: IEP event received 
    Offender Events -->> Offender Events Topic (SNS): IEP_UPSERTED published
    Offender Events Topic (SNS) -->> HMPPS Prisoner from NOMIS migration: Receives IEP_UPSERTED event from subscribed Queue
    HMPPS Prisoner from NOMIS migration ->> Incentives API: Insert/Update/Delete API called
```

## Key components and their flow for Incentive management
```mermaid
    
graph TB
    X((User)) --> A
    X --> N[NOMIS Oracle forms]
    N --> G
    A[DPS] -- Add Incentive Review --> B
    B[Incentives API] -- Store Incentive --> D[[Incentive DB]]
    B -- Incentive Created Message --> C[[Domain Events]]
    C -- prisoner movements -->B
    C -- Listen to events --> E[HMPPS Prisoner to NOMIS update]
    E -- Update NOMIS via API --> F[HMPPS NOMIS Prisoner API]
    F -- persist --> G[[NOMIS DB]]
    P[Prisoner Search API] -- adds events --> C
    B -- lookup prisoner --> L[Prison API]
    L --> G
    R[HMPPS Prisoner from NOMIS Migration] -- perform migration --> B
    R -- record history --> H[[History Record DB]]
    K[HMPPS NOMIS Mapping Service] --> Q[[Mapping DB]]
    R -- check for existing mapping --> K
    R -- 1. find out how many to migrate, 2 IEP details --> F
    G -- IEP Upsert --> U[HMPPS Prisoner Events]
    U -- New Incentives from NOMIS --> R
    P -- incentive data for ES index --> B
```


[Next >>](9999-end.md)
