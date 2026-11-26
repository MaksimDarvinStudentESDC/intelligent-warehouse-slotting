# Placement requirements traceability

Source: BA_Maksim_Darvin.pdf, Appendix A (US1.5, US5.3) and non-functional requirements (NFR7). Review date: 26 November 2026.

| Requirement / acceptance criterion | Implementation | Evidence / status |
|---|---|---|
| US1.5: record operation and update occupancy atomically | PlacementService transaction, StockMovement, Batch, Location | Implemented; sequential success manually observed; rollback integration test pending |
| US1.5: occupancy never exceeds capacity | HardConstraintsService, Location.occupy, database constraints, locks | Sequential examples observed; universal invariant not established by those examples |
| US1.5: history by product, location, period | StockMovementService, repository specification, MovementHistoryPage | Manual product/location results shown; date filtering reported passed |
| US5.3: save confirmation and occupancy atomically | POST /api/placements/confirm | Receipts #5 and #6 and location occupancy verified manually; rollback pending |
| US5.3: unsuitable location rejected, list refreshed, reason shown | Version comparison, constraint recheck, conflict UI and refresh button | Batch #9 stale conflict and refreshed list observed; refresh is operator-triggered |
| US5.3: concurrent confirmations do not overrun capacity | Batch/location pessimistic locks and version check | Implemented safeguards; real concurrent test deferred |
| NFR7: zero capacity overruns under competing requests exceeding free capacity | Same transaction and lock design | Not verified; must remain open |

US1.5 and US5.3 are partially verified, not fully accepted. Current live workflow records putaway; other movement producers such as simulated relocation are not established by this review. Existing unit and controller tests include PlacementServiceTest, StockMovementServiceTest, PlacementControllerWebIntegrationTest, StockMovementControllerWebIntegrationTest and StockMovementTest. These are not substitutes for concurrent real-database tests.

## Deferred acceptance gate

Use isolated PostgreSQL, independent concurrent transactions and two batches whose combined volume exceeds one location's free capacity. Assert no overrun, at least one rejection, consistent batch states and movement count. Also force a failure after occupancy update and verify rollback of all writes. Testcontainers startup timezone issue remains unresolved. Record actual test results before marking NFR7 or both stories fully accepted.

Stage 9.5 (final build/CI) and 9.6 (summary) remain separate. No new tests executed during this documentation update.
