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

Tables Affected:

- REFERENCE_CODES for DOMAIN = IEP_LEVEL
- IEP_LEVELS
- VO_ALLOWANCES
- PRIVS


## Decision

This approach follows the agreed pattern of architecture for "Getting Off NOMIS"

## Consequences


[Next >>](9999-end.md)
