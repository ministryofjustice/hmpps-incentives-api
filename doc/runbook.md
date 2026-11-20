# Support runbook

Known issues and how to handle them when they are reported. Each entry says what the user sees,
why it happens, how to check it, and what to do.

## Incentive history shows a newer level than the prisoner's current level

**Status:** known issue, by design. No fix planned ([IR-1818](https://dsdmoj.atlassian.net/browse/IR-1818)).

### What the user sees

On the incentive level history page, the top row (the most recent) shows a different level from the
*Current incentive level* banner. It is usually a Standard row recorded by "System" with the reason
"Default level assigned on arrival". For example:

| Date and time  | Level    | Reason for recording              | Recorded by       |
|----------------|----------|-----------------------------------|-------------------|
| 1 March, 10:00 | Standard | Default level assigned on arrival | System            |
| 1 January      | Enhanced | (staff comment)                   | A member of staff |

The banner says Enhanced, but the newest row says Standard. Users report this as the service
showing the wrong level.

### Why it happens

The banner is correct. The newer row belongs to a booking the prisoner is no longer on.

1. A recalled prisoner is admitted in NOMIS onto a **new** booking by mistake. The service records
   a default level against that new booking, as it does for every admission.
2. NOMIS staff correct the mistake by re-admitting the prisoner onto their **earlier** booking.
   Prisoner search publishes this as a `READMISSION_SWITCH_BOOKING` event.
3. The service handles that event (since [IR-1808](https://dsdmoj.atlassian.net/browse/IR-1808)):
   the default level on the mistaken booking stops being current, and the level on the earlier
   booking becomes current again. This is in
   `PrisonerIncentiveReviewService.reinstateReviewsAfterBookingSwitch`.
4. The default-level row is **not deleted**. It is a genuine record, NOMIS holds it too, and it must
   stay auditable. Because the history page lists every review in date order, it appears at the
   top.

The history page does not show which booking each row belongs to, so nothing on screen explains
this.

### How to check it

Get the prisoner's history (needs `ROLE_INCENTIVE_REVIEWS` with read scope):

```shell
curl -s "$API/incentive-reviews/prisoner/$PRISONER_NUMBER" -H "Authorization: Bearer $TOKEN" |
  jq '{bookingId, iepLevel, history: [.iepDetails[] | {bookingId, iepDate, iepLevel, userId}]}'
```

The top-level `bookingId` is the booking the prisoner is on now. Any row in `iepDetails` with a
**higher** `bookingId` is on a booking the prisoner was moved off. If the confusing row is one of
those, and the top-level `iepLevel` matches the banner, this is the known issue.

### What to do

**If the banner shows the right level:** nothing needs changing. Reply to the user:

> The current incentive level shown at the top of the page is correct. The more recent entry in
> the history was recorded automatically when the prisoner was admitted onto a new record in NOMIS
> by mistake. That record has since been corrected, so the entry no longer applies, but it stays
> in the history because it was recorded at the time.

**If the banner shows the wrong level** (the mistaken booking's default level is still current),
the `READMISSION_SWITCH_BOOKING` event was missed or arrived before IR-1808 was live. Run the
repair, which does what the event handler would have done. It needs `ROLE_INCENTIVE_REVIEWS` with
write scope, and is safe to re-run. Try a dry run first:

```shell
curl -s -X POST "$API/incentive-reviews/prisoner/$PRISONER_NUMBER/repair-booking-switch?dry-run=true" \
  -H "Authorization: Bearer $TOKEN"
```

If `levelCodeAfter` is the level the prisoner should be on, run it again without `?dry-run=true`.
The repair publishes `incentives.iep-review.updated`, so other services pick up the change, and
records a `PRISONER_BOOKING_SWITCHED` audit event.

**If the newer row was recorded by a member of staff, not "System":** the repair will not change it.
A staff review is a real decision and the service never discards it, even when it was recorded
against the mistaken booking. Ask the prison to record a new review on the
prisoner's current record at the level they should be on.

### Why this is not being fixed

Labelling these rows on the history page was considered in IR-1818 and parked on 20/11/2026. Only a
small number of prisoners are affected, the current level is already correct, and the booking
switch that causes it only happens in NOMIS. Bookings are being replaced as part of the move off
NOMIS, so screen wording about "previous bookings" would have to be undone later. If this starts
being reported regularly, IR-1818 describes a small screen-only change that could be made instead.
