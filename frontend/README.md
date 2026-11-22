# Warehouse Slotting Frontend — foundation

## Start
Requires Node.js 20.19+ or 22.12+. From `frontend/`:

```bash
npm install
npm run dev
```

Vite runs at http://localhost:5173 and proxies `/api` to `http://localhost:8080`. Set `VITE_API_PROXY_TARGET` if backend uses a different URL.

## Implemented
- Responsive shell, UX colour tokens, navigation and placeholder routes.
- Receive-batch form with client-side validation, real POST `/api/batches`, and GET `/api/recommendations/batches/{id}`.
- Ranked feasible locations with explanations; GET `/api/warehouses/{id}/locations`.
- Loading, empty and error states; no fabricated backend metrics.

## Backend gaps identified
- No GET `/api/products` to populate searchable product selection. Temporary product ID field is explicit.
- No endpoint to confirm putaway transactionally; confirmation intentionally disabled.
- No CSV import or simulation endpoints in this build. These routes are clearly marked as planned.
- Recommendation response currently lacks score contributions and free capacity; extend DTO to meet UX acceptance criteria.

## Next integration slice
Implement product listing and putaway confirmation backend endpoints; then add product search, location free-capacity data, override reasons and conflict refresh.
