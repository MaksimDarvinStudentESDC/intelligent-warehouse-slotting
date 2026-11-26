# User flows — placement and history

## Operator: receive and place

```mermaid
flowchart TD
  A[Sign in as operator] --> B[Enter product ID, batch code, quantity and optional expiry]
  B --> C[Register batch]
  C --> D[Request recommendations]
  D --> E{Feasible candidate?}
  E -->|No| F[Show empty result]
  E -->|Yes| G[Select location]
  G --> H[Confirm with location version]
  H --> I{Response}
  I -->|201| J[Show receipt and update location data]
  I -->|Recoverable 409| K[Show reason and refresh same batch]
  K --> D
  I -->|Already placed| L[Block repeat confirmation]
  J --> M[Receive another batch]
  M --> B
```

If recommendation retrieval fails after registration, retry for the existing batch. A refreshed list requires explicit selection. The success receipt shows movement ID, batch ID, destination, status, total occupancy and time. Receive another batch resets the form and selection. No automatic placement retry is performed.

## Operator, planner, manager: movement history

Open Movement history from navigation; warehouse 1 is the initial filter. Enter optional product/location IDs and dates, select page size and apply. Review results, move between pages or reset filters. Empty results and load failures are shown separately. Business manager and admin are denied; unauthenticated users must sign in.

The current UI supports initial putaway, not a general relocation or override-reason workflow.
