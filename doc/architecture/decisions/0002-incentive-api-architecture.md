# 2. Service to model incentive related data for prisoners

[Next >>](9999-end.md)


Date: 2022-02-02

## Status

Accepted

## Context


New service to aggregate and capture incentive related data in a single micro service. 

Initially this service will:-
- Asynchronously call off to the Prison API to obtain information relating to positive and negative case notes, adjuication history, IEP review history and basic prisoner data (name, number, location, image)
- Aggregate this information into a restful response that can be consumed by the [Incentive UI](https://github.com/ministryofjustice/hmpps-incentives-ui)

### Future Work
 This API will form the basis of IEP data currently held in NOMIS.

- This service will act as a facade over all IEP related data
- IEP related data will be stored in a new database
- As requirements are refined, addition incentive attributes will be captured in this service
- APIs will support the maintenance of this data
- Domain events will be emitted upon changes to this data
- Synchronisation services will be written (by Syscon) to maintain IEP data in NOMIS
- Current IEP maintenance API endpoints in Prison API will be hidden and deprecated
- DPS will switch to using Incentives API to add/update/view IEP related data
- This service will combine new IEP data and historical IEP captured in NOMIS until such time as all data is migrated

## Decision

This approach follows the agreed pattern of architecture for "Getting Off NOMIS"

## Consequences

- Complexity of system will increase as service starts to master IEP data and aggregate with existing historical data from NOMIS
- Analytical Platform may have to start extracting data from this new data source (or be fed that data)
- Synchronisation process will need maintain data in NOMIS until operation reporting and downstream dependencies are resolved.

[Next >>](9999-end.md)
