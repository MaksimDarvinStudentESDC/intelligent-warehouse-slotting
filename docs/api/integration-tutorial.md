# API Integration Tutorial — From Incoming Product to Slotting Recommendation

**Project:** Intelligent Warehouse Slotting Optimization System  
**Audience:** Backend integrators, QA engineers, and developers  
**API base URL:** `http://localhost:8080`  
**Validated against:** Manual API tests performed on 22 November 2026

## Goal

Follow a complete, reproducible API workflow: create a product, register an incoming batch, list the warehouse's batches and active locations, verify hard constraints, request ranked placement recommendations, and optionally run ABC/XYZ classification.

This tutorial uses the currently implemented `nearest_feasible` recommendation strategy. **It does not place stock into a location**: no putaway-confirmation endpoint is currently exposed.

## Before you start

1. Follow [Getting Started](./README.md) to start PostgreSQL and the Spring Boot backend (Java 25).
2. Verify that [Swagger UI](http://localhost:8080/swagger-ui/index.html) loads, or use the `curl` commands below.
3. Ensure warehouse ID `1` exists and has active storage locations. The demonstrated test database had warehouse `1` (`DEMO - Main Warehouse`) and 12 active locations, `ST-01` through `ST-12`. Other databases may have different IDs or data.
4. Use a **new SKU and batch code** for repeated runs. Do not reuse an existing identifier and assume that creation will succeed.

All examples below use Bash-style `curl` commands. Windows PowerShell users can execute them in Git Bash or adapt the commands to `Invoke-RestMethod`.

## Step 1 — Create a product

**Request**

```bash
curl -i -X POST 'http://localhost:8080/api/products' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "warehouseId": 1,
    "sku": "API-TUTORIAL-001",
    "name": "Warehouse Tutorial Product",
    "lengthMm": 300,
    "widthMm": 200,
    "heightMm": 150,
    "unitWeightKg": 2.5,
    "compatibilityGroupId": null,
    "shelfLifeDays": 365,
    "outboundFlow": "PICKING"
  }'
```

**Expected HTTP status:** `201 Created`.

**Example response** (the generated `id` will vary):

```json
{
  "id": 5,
  "sku": "API-TUTORIAL-001",
  "name": "Warehouse Tutorial Product",
  "unitVolumeDm3": 9,
  "unitWeightKg": 2.5
}
```

The unit volume is `(300 × 200 × 150) / 1,000,000 = 9 dm³`. **Record the actual returned product `id`**. The demonstrated run returned product ID `5`, but do not hard-code it for a fresh run.

## Step 2 — Register an incoming batch

Replace `5` in `productId` with the **actual product ID** from Step 1.

```bash
curl -i -X POST 'http://localhost:8080/api/batches' \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": 5,
    "batchCode": "API-TUTORIAL-BATCH-001",
    "quantity": 50,
    "receivingDate": "2026-11-22",
    "expiryDate": "2027-11-22"
  }'
```

**Expected HTTP status:** `201 Created`.

**Example response:**

```json
{
  "id": 5,
  "batchCode": "API-TUTORIAL-BATCH-001",
  "quantity": 50,
  "volumeDm3": 450,
  "weightKg": 125,
  "receivingDate": "2026-11-22",
  "expiryDate": "2027-11-22",
  "status": "AWAITING_PUTAWAY"
}
```

The batch occupies `50 × 9 = 450 dm³` and weighs `50 × 2.5 = 125 kg`. **Record the actual returned batch `id`**. The original validation run returned batch ID `5`.

## Step 3 — Verify the batch belongs to the warehouse

```bash
curl -i 'http://localhost:8080/api/warehouses/1/batches'
```

**Expected HTTP status:** `200 OK`. The response is an array. Locate the entry with the batch ID and batch code from Step 2. During validation, the array included:

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

This is **one object within the returned array**, not the entire response. Its batch code is from the recorded validation run; your new tutorial batch has a different code.

## Step 4 — Discover active storage locations

```bash
curl -i 'http://localhost:8080/api/warehouses/1/locations'
```

**Expected HTTP status:** `200 OK`, with an array of active locations. Each entry includes `id`, `code`, `zoneId`, `capacityDm3`, `occupiedDm3`, `maxWeightKg`, distances to receiving/picking/shipping, and `active`.

In the validated test dataset, two relevant locations were:

| Location | ID | Free volume* | Max weight | Picking distance |
|---|---:|---:|---:|---:|
| `ST-01` | 1 | 100 dm³ | 60 kg | 5 m |
| `ST-09` | 9 | 450 dm³ | 320 kg | 35 m |

*In the recorded test, both locations had `occupiedDm3: 0`. For another dataset, compute free volume as `capacityDm3 - occupiedDm3`.

If your dataset differs, choose one location too small for the batch and one with sufficient capacity and weight allowance. Other compatibility rules can still make a location infeasible.

## Step 5 — Check hard constraints

Replace `5` with your actual batch ID and use location IDs from Step 4.

**Example A: insufficient capacity**

```bash
curl -i 'http://localhost:8080/api/constraints/batches/5/locations/1'
```

**Expected HTTP status:** `200 OK`. The validated dataset returned:

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

**Example B: sufficient volume and weight**

```bash
curl -i 'http://localhost:8080/api/constraints/batches/5/locations/9'
```

The validated dataset returned:

```json
{
  "batchId": 5,
  "locationId": 9,
  "locationCode": "ST-09",
  "feasible": true,
  "rejectionReasons": []
}
```

A `200 OK` response with `feasible: false` is **not** an HTTP error. It means the API successfully evaluated an unsuitable location.

## Step 6 — Request ranked slotting recommendations

```bash
curl -i 'http://localhost:8080/api/recommendations/batches/5?topN=5'
```

**Expected HTTP status:** `200 OK`.

**Actual response from the validated dataset:**

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

**Interpretation:** Eight locations failed hard constraints, leaving four feasible candidates. The implemented `nearest_feasible` strategy ranks them by ascending distance to picking. Since only four are feasible, `topN=5` returns four recommendations. This is **not** confirmation that stock was moved or that the planned multi-criteria ABC/XYZ optimization has been implemented.

## Step 7 — Optional: run ABC/XYZ classification

Classification is a separate API operation; its results are **not demonstrated as inputs** to the current `nearest_feasible` ranking.

```bash
curl -i -X POST 'http://localhost:8080/api/classifications/run' \
  -H 'Content-Type: application/json' \
  -d '{
    "warehouseId": 1,
    "windowStart": "2026-10-01",
    "windowEnd": "2026-11-22"
  }'
```

**Expected HTTP status:** `201 Created` (confirmed during manual validation). The observed response included three products:

| SKU | ABC | XYZ | Movement count | CV | Note |
|---|---|---|---:|---:|---|
| `DEMO-001` | A | X | 969 | 0.2004 | — |
| `POSTMAN-001` | A | Y | 462 | 0.5489 | — |
| `API-TEST-003` | C | `null` | 0 | `null` | `no data` |

A product without movement history can return `xyzClass: null` and `cv: null`; do not treat those as the literal class `Z`.

## Negative cases verified during manual testing

| Operation | Invalid input | Actual status |
|---|---|---|
| `POST /api/products` | `lengthMm: -100` | `400 Bad Request` |
| `POST /api/batches` | `quantity: 0` | `400 Bad Request` |
| `GET /api/constraints/batches/999999/locations/1` | Unknown batch ID | `404 Not Found` |
| `GET /api/recommendations/batches/5?topN=0` | `topN=0` | `400 Bad Request` |

The observed error body uses Spring Boot's standard fields (`timestamp`, `status`, `error`, `path`) and generally does not identify the invalid field. For example:

```json
{
  "timestamp": "2026-11-22T15:44:14.268+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/recommendations/batches/5"
}
```


## Completion checklist

- [ ] Product created (`201`) and its actual ID recorded.
- [ ] Batch created (`201`) and its actual ID recorded.
- [ ] Batch visible in warehouse batch list (`200`).
- [ ] Active locations retrieved (`200`).
- [ ] One infeasible and one feasible location checked (`200`).
- [ ] Ranked recommendations returned (`200`).
- [ ] Optional classification run completed (`201`).

## Related documentation

- [Getting Started and endpoint overview](./README.md)
- [OpenAPI contract](./openapi.yaml)
- [Manual validation report](./validation-results.md)

**Note:** Example IDs and historical test responses are evidence from the validation dataset, not guaranteed outputs in a new database. Use the IDs and locations returned by your own API calls.
