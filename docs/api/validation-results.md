# API Validation Results

**Project:** Intelligent Warehouse Slotting Optimization System  
**Test date:** 2026-11-22  
**Environment:** Local Spring Boot API (`http://localhost:8080`), PostgreSQL, Swagger UI  
**Method:** Manual HTTP requests executed through Swagger UI  
**Scope:** Seven API operations and four negative test cases

## 1. Summary

Seven positive functional scenarios produced responses consistent with the expected application behavior. Four negative scenarios returned the expected HTTP status codes. The classification endpoint's `201 Created` status was confirmed by the tester. For some successful GET requests, the response bodies were provided without the HTTP status displayed; those statuses are therefore **not independently recorded** in this report.

**Result:** Functional API validation completed for the tested scenarios. Documentation improvements remain outstanding; this is not a claim of exhaustive API or OpenAPI specification validation.

## 2. Positive functional tests

| ID | Method | Endpoint | Expected status | Observed status | Result / evidence |
|---|---|---|---|---|---|
| P1 | POST | `/api/products` | `201 Created` | `201` | PASS — created product ID `5`, SKU `API-TEST-003`, unit volume `9 dm³`, unit weight `2.5 kg`. |
| P2 | POST | `/api/batches` | `201 Created` | `201` | PASS — created batch ID `5`, quantity `50`, volume `450 dm³`, weight `125 kg`, status `AWAITING_PUTAWAY`. |
| P3 | GET | `/api/warehouses/1/batches` | `200 OK` | Not supplied | Response content verified — three batches returned, including batch ID `5`. HTTP status should be recorded separately. |
| P4 | GET | `/api/warehouses/1/locations` | `200 OK` | Not supplied | Response content verified — 12 active locations (`ST-01` to `ST-12`) returned. HTTP status should be recorded separately. |
| P5 | GET | `/api/constraints/batches/5/locations/1` and `/9` | `200 OK` | `200` for both | PASS — location `ST-01` rejected; `ST-09` accepted. |
| P6 | GET | `/api/recommendations/batches/5?topN=5` | `200 OK` | Not supplied | Response content verified — `nearest_feasible`, `feasibleCount: 4`, four ranked recommendations. HTTP status should be recorded separately. |
| P7 | POST | `/api/classifications/run` | `201 Created` | `201` (tester-confirmed) | PASS — classification run ID `2`, three products classified/processed. |

### P1 — Product creation

**Request:** `POST /api/products`

```json
{
  "warehouseId": 1,
  "sku": "API-TEST-003",
  "name": "Warehouse API Test Product",
  "lengthMm": 300,
  "widthMm": 200,
  "heightMm": 150,
  "unitWeightKg": 2.5,
  "compatibilityGroupId": null,
  "shelfLifeDays": 365,
  "outboundFlow": "PICKING"
}
```

**Actual response:** `201 Created`

```json
{
  "id": 5,
  "sku": "API-TEST-003",
  "name": "Warehouse API Test Product",
  "unitVolumeDm3": 9,
  "unitWeightKg": 2.5
}
```

Two earlier product-creation attempts returned `400 Bad Request`. Their root cause was not established; the later successful request does not prove why the earlier attempts failed.

### P2 — Batch creation

**Request:** `POST /api/batches`

```json
{
  "productId": 5,
  "batchCode": "API-BATCH-001",
  "quantity": 50,
  "receivingDate": "2026-11-22",
  "expiryDate": "2027-11-22"
}
```

**Actual response:** `201 Created`

```json
{
  "id": 5,
  "batchCode": "API-BATCH-001",
  "quantity": 50,
  "volumeDm3": 450,
  "weightKg": 125,
  "receivingDate": "2026-11-22",
  "expiryDate": "2027-11-22",
  "status": "AWAITING_PUTAWAY"
}
```

### P3 and P4 — Warehouse data retrieval

- `GET /api/warehouses/1/batches` returned batch IDs `1`, `2`, and `5`; the new batch's values matched the creation response.
- `GET /api/warehouses/1/locations` returned 12 active locations in zone `4`, all with `occupiedDm3: 0`. Location capacities range from `100` to `700 dm³`.

### P5 — Hard constraints

**Request:** `GET /api/constraints/batches/5/locations/1`  
**Actual response:** `200 OK`

```json
{
  "batchId": 5,
  "locationId": 1,
  "locationCode": "ST-01",
  "feasible": false,
  "rejectionReasons": [
    "INSUFFICIENT_VOLUME",
    "INSUFFICIENT_WEIGHT_CAPACITY"
  ]
}
```

The batch requires `450 dm³` and `125 kg`; `ST-01` allows `100 dm³` and `60 kg`.

**Request:** `GET /api/constraints/batches/5/locations/9`  
**Actual response:** `200 OK`

```json
{
  "batchId": 5,
  "locationId": 9,
  "locationCode": "ST-09",
  "feasible": true,
  "rejectionReasons": []
}
```

`ST-09` has `450 dm³` capacity and `320 kg` maximum weight.

### P6 — Location recommendations

**Request:** `GET /api/recommendations/batches/5?topN=5`

```json
{
  "batchId": 5,
  "strategy": "nearest_feasible",
  "feasibleCount": 4,
  "recommendations": [
    {
      "rank": 1,
      "locationId": 9,
      "locationCode": "ST-09",
      "distanceMeters": 35,
      "explanation": "Passes hard constraints; nearest feasible location by distance to picking (m)"
    },
    {
      "rank": 2,
      "locationId": 10,
      "locationCode": "ST-10",
      "distanceMeters": 40,
      "explanation": "Passes hard constraints; nearest feasible location by distance to picking (m)"
    },
    {
      "rank": 3,
      "locationId": 11,
      "locationCode": "ST-11",
      "distanceMeters": 45,
      "explanation": "Passes hard constraints; nearest feasible location by distance to picking (m)"
    },
    {
      "rank": 4,
      "locationId": 12,
      "locationCode": "ST-12",
      "distanceMeters": 55,
      "explanation": "Passes hard constraints; nearest feasible location by distance to picking (m)"
    }
  ]
}
```

All four recommendations satisfy the tested constraints and are ordered by ascending distance to picking. This validates the observed `nearest_feasible` behavior, not a full multi-criteria optimization algorithm.

### P7 — ABC/XYZ classification

**Request:** `POST /api/classifications/run`

```json
{
  "warehouseId": 1,
  "windowStart": "2026-10-01",
  "windowEnd": "2026-11-22"
}
```

**Actual response:** `201 Created` (confirmed by tester)

```json
{
  "runId": 2,
  "warehouseId": 1,
  "windowStart": "2026-10-01",
  "windowEnd": "2026-11-22",
  "products": [
    {
      "productId": 1,
      "sku": "DEMO-001",
      "abcClass": "A",
      "xyzClass": "X",
      "movementCount": 969,
      "cumulativeShare": 0.67715,
      "cv": 0.2004,
      "note": null
    },
    {
      "productId": 2,
      "sku": "POSTMAN-001",
      "abcClass": "A",
      "xyzClass": "Y",
      "movementCount": 462,
      "cumulativeShare": 1,
      "cv": 0.5489,
      "note": null
    },
    {
      "productId": 5,
      "sku": "API-TEST-003",
      "abcClass": "C",
      "xyzClass": null,
      "movementCount": 0,
      "cumulativeShare": 1,
      "cv": null,
      "note": "no data"
    }
  ]
}
```

The response includes an explicit no-demand-data case with `xyzClass: null`, `cv: null`, and `note: "no data"`.

## 3. Negative tests

| ID | Request / condition | Expected | Observed | Result |
|---|---|---|---|---|
| N1 | `POST /api/products`, `lengthMm: -100` | `400 Bad Request` | `400` | PASS |
| N2 | `POST /api/batches`, `quantity: 0` | `400 Bad Request` | `400` | PASS |
| N3 | `GET /api/constraints/batches/999999/locations/1` | `404 Not Found` | `404` | PASS |
| N4 | `GET /api/recommendations/batches/5?topN=0` | `400 Bad Request` | `400` | PASS |

### Actual error response format

The application returned the default Spring Boot error body, including a timestamp, HTTP status, error title, and request path. Examples:

**N1 — Invalid product length (`400`):**

```json
{
  "timestamp": "2026-11-22T15:37:52.221+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/products"
}
```

**N2 — Invalid batch quantity (`400`):**

```json
{
  "timestamp": "2026-11-22T15:40:08.333+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/batches"
}
```

**N3 — Missing batch (`404`):**

```json
{
  "timestamp": "2026-11-22T15:41:32.787+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/constraints/batches/999999/locations/1"
}
```

**N4 — Invalid `topN` (`400`):**

```json
{
  "timestamp": "2026-11-22T15:44:14.268+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/recommendations/batches/5"
}
```

These responses do not identify the invalid field or provide a detailed human-readable explanation. The status codes match the tested expectations; the response body design can be improved.

## 4. Findings and follow-up actions

| ID | Finding | Priority | Recommended action |
|---|---|---|---|
| DOC-01 | Swagger UI displayed `201 Undocumented`, `400 Undocumented`, and `404 Undocumented` for tested operations. | High | Document the observed response codes and JSON schemas in the OpenAPI specification actually served by Swagger UI; verify its source (`/v3/api-docs` versus a static YAML file). |
| DOC-02 | Error responses contain no field-specific validation details. | Medium | Document the current default error shape; optionally add centralized exception handling with consistent, actionable error messages. |
| TEST-01 | Initial product-creation requests returned `400`; their cause remains unknown. | Medium | Investigate logs or reproduce the original request before assigning a root cause. |
| TEST-02 | HTTP statuses for P3, P4, and P6 were not included in the supplied evidence. | Low | Record the Swagger UI HTTP status or save request/response traces to complete the evidence trail. |
| DOC-03 | The tested recommendation strategy is `nearest_feasible`. | Medium | Describe this as the currently observed strategy; avoid presenting it as full ABC/XYZ-based multi-criteria optimization. |

## 5. Conclusion

The tested API workflows successfully created products and batches, retrieved warehouse data, enforced placement constraints, generated feasible location recommendations, and executed ABC/XYZ classification. All four negative scenarios returned the expected status codes.

**Manual functional validation is complete for the selected scenarios.** Before declaring the API documentation criterion fully satisfied, update the published OpenAPI specification with accurate status codes, response examples, error schemas, and verify its consistency with the running application. The Getting Started guide and integration tutorial should also be completed and linked from the repository.

## Placement workflow follow-up — 26 November 2026

Historical results above are retained. Subsequent manual evidence: batch #10, movement #5, ST-01 occupied 60 dm³ / 30 kg; batch #11, movement #6, ST-01 occupied 90 dm³ / 45 kg. Location list showed 10 dm³ remaining out of 100. Stale recommendation for batch #9 was rejected; refresh reduced feasible locations from eight to seven and a subsequent confirmation succeeded. History returned six movements for product #1 and two for location #1; unknown warehouse returned 404. Date filters, pagination, reset and role navigation were reported verified. Existing frontend automated suites were reported passing; exact latest counts are not independently established. Both OpenAPI copies are identical in the reviewed archive. No new runtime or CI execution was performed in this documentation review. PostgreSQL rollback and concurrency verification remain deferred.
