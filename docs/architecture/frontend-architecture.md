# Frontend architecture

React 19 / TypeScript / Vite SPA with React Router, TanStack Query, React Hook Form and Zod. `App.tsx` provides navigation and route guards; `context/permissions.ts` defines route access. Authentication uses an in-memory JWT; the API client attaches Bearer authorization. Backend authorization remains authoritative.

| Layer | Components |
|---|---|
| Pages | ReceiveBatchPage, LocationsPage, MovementHistoryPage |
| Presentation | MovementHistoryTable, shared UI controls |
| Services | batchService, placementService, movementService, apiClient |
| Contracts | types/api.ts |

ReceiveBatchPage registers a batch, fetches ranked candidates, submits the selected version and renders the placement receipt. A successful mutation invalidates location queries. Movement history fetches on mount, keys queries by applied filters and page, and disables retries. Draft filters are separate from applied filters; calendar dates convert from local midnight to ISO instants, with the upper bound at next-day midnight. Reset restores warehouse 1, size 20 and page 0. Navigation is shared between desktop and mobile.

See [RBAC](../rbac.md) and [user flows](user-flows.md). Product selection currently uses a numeric ID. Import and comparison pages are placeholders. The receiving page currently uses the default nearest_feasible strategy, not weighted_score. Expiry equal to receiving date may pass frontend validation but is rejected by backend. Raw history error formatting remains a known UI limitation.
