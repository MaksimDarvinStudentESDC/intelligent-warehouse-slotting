# API Developer Guide — Getting Started

**Project:** Intelligent Warehouse Slotting Optimization System  
**API base URL (local):** `http://localhost:8080`  
**API style:** REST / JSON  
**Status:** Developer documentation for the currently implemented backend (November 2026).

This guide helps a developer start the backend, inspect the API contract, and execute a first request. It documents the **implemented** API, not the entire planned diploma-project feature set.

## 1. Prerequisites

- **JDK 25** (the backend Maven configuration specifies `<java.version>25</java.version>`).
- **Maven** or the repository's Maven wrapper, if present.
- **Docker with Docker Compose** for the provided PostgreSQL service, or an equivalent PostgreSQL instance.
- A terminal and optionally IntelliJ IDEA, Postman, or curl.

The repository's `docker-compose.yml` starts **PostgreSQL 17** and maps container port `5432` to host port **`5433`**. The Spring Boot backend listens on **`8080`** by default.

## 2. Configure PostgreSQL

From the repository root, configure the variables required by `docker-compose.yml` in a local `.env` file:

```dotenv
POSTGRES_DB=warehouse_slotting
POSTGRES_ADMIN_USER=postgres
POSTGRES_ADMIN_PASSWORD=replace_with_a_secure_local_password
```

These are **illustrative local values**, not credentials from the project. Existing installations may already use different database names, roles, and passwords; preserve their working configuration.

Start the database:

```bash
docker compose up -d postgres
```

The backend uses separate runtime and migration credentials. Configure them in your shell or IDE run configuration:

```dotenv
DB_URL=jdbc:postgresql://localhost:5433/warehouse_slotting
DB_APP_USERNAME=warehouse_app
DB_APP_PASSWORD=your_runtime_password
DB_MIGRATOR_USERNAME=warehouse_migrator
DB_MIGRATOR_PASSWORD=your_migration_password
```

**Important:** The Docker Compose file creates the PostgreSQL container and its initial admin role; it does **not** automatically create the `warehouse_app` and `warehouse_migrator` roles or grant their privileges. Provision these roles and the required permissions according to your database setup before starting the backend. For an existing working development database, reuse its configured values. Flyway runs on backend startup; JPA uses schema validation (`ddl-auto=validate`).

Never commit `.env`, passwords, or connection strings containing credentials.

## 3. Start the Spring Boot backend

From the repository root:

```bash
cd backend
mvn spring-boot:run
```

Alternatively, run the Spring Boot application from IntelliJ IDEA using the same environment variables and JDK 25.

A successful startup should leave the application listening on `http://localhost:8080`. If startup fails, check the PostgreSQL container, the JDBC URL, role privileges, and Flyway migration logs.

## 4. Open the API documentation

With the backend running:

| Resource | Local URL |
|---|---|
| Interactive Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| Maintained OpenAPI YAML (served by backend) | <http://localhost:8080/openapi.yaml> |
| Automatically generated OpenAPI JSON (separate) | <http://localhost:8080/v3/api-docs> |
| API base URL | <http://localhost:8080> |

The maintained API contract is [`openapi.yaml`](./openapi.yaml). **Swagger UI is configured to load `/openapi.yaml`**, served from `backend/src/main/resources/static/openapi.yaml`. This configuration was verified in Swagger UI: the page displays **Intelligent Warehouse Slotting Optimization System API**, version **0.1.0**, with the OpenAPI 3.0 specification and documented response codes. The `/v3/api-docs` endpoint is a **separate, automatically generated** description and is not the source currently displayed in Swagger UI.

The relevant Spring Boot configuration is:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.url=/openapi.yaml
springdoc.swagger-ui.disable-swagger-default-url=true
```

**Keep both YAML copies synchronized:** after updating `docs/api/openapi.yaml`, copy it to `backend/src/main/resources/static/openapi.yaml` and restart the backend. Otherwise Swagger UI may show an older contract.

In Swagger UI, expand an operation, choose **Try it out**, supply parameters or JSON, and select **Execute**. Check the actual **Server response** status code rather than relying only on the generated example.

## 5. Implemented endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/products` | Create a product |
| `POST` | `/api/batches` | Register an incoming batch |
| `GET` | `/api/warehouses/{warehouseId}/batches` | List warehouse batches |
| `GET` | `/api/warehouses/{warehouseId}/locations` | List active storage locations |
| `GET` | `/api/constraints/batches/{batchId}/locations/{locationId}` | Check placement feasibility |
| `GET` | `/api/recommendations/batches/{batchId}?topN=5` | Rank feasible storage locations |
| `POST` | `/api/classifications/run` | Run ABC/XYZ classification |

The current recommendation strategy observed during validation is **`nearest_feasible`**: hard constraints are checked first, then feasible locations are ordered by distance to picking. Do not interpret this as the final planned multi-criteria optimizer.

## 6. First API request

The following example creates a product in an **existing** warehouse with ID `1`. Replace that ID if your database has a different warehouse. Choose a **new SKU** for each repeated creation attempt.

```bash
curl -i -X POST 'http://localhost:8080/api/products' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "warehouseId": 1,
    "sku": "GETTING-STARTED-001",
    "name": "Getting Started Product",
    "lengthMm": 300,
    "widthMm": 200,
    "heightMm": 150,
    "unitWeightKg": 2.5,
    "compatibilityGroupId": null,
    "shelfLifeDays": 365,
    "outboundFlow": "PICKING"
  }'
```

**Expected status:** `201 Created`. The response contains the generated product `id`, `sku`, `name`, `unitVolumeDm3`, and `unitWeightKg`. For these dimensions, the unit volume is **9 dm³**. Save the returned ID for the next operation; do not assume a fixed ID.

A sample response shape (illustrative ID):

```json
{
  "id": 5,
  "sku": "GETTING-STARTED-001",
  "name": "Getting Started Product",
  "unitVolumeDm3": 9,
  "unitWeightKg": 2.5
}
```

For the full workflow (create product → create batch → list locations → check constraints → request recommendations → run classification), see the completed [`integration-tutorial.md`](./integration-tutorial.md).

## 7. HTTP status codes and error responses

Common statuses observed in manual testing:

| Code | Meaning | Example |
|---|---|---|
| `200 OK` | Successful retrieval/check/recommendation | Feasibility check |
| `201 Created` | Successful creation or classification run | Create product/batch, run classification |
| `400 Bad Request` | Invalid request data or query parameter | Negative dimensions, zero quantity, `topN=0` |
| `404 Not Found` | Referenced resource does not exist | Unknown batch ID in constraints check |

A typical observed Spring Boot error body:

```json
{
  "timestamp": "2026-11-22T15:44:14.268+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/recommendations/batches/5"
}
```

The observed Spring Boot error payload **does not include field-level validation details**. The `timestamp` varies per request and `path` depends on the endpoint. The maintained OpenAPI contract now documents the relevant `201`, `400`, and `404` responses, and these descriptions have been verified in Swagger UI. A documented response code describes the API contract; it does not by itself prove that every error scenario has been exercised. See [`error-handling.md`](./error-handling.md) for details. The `409 Conflict` response is documented as a potential contract response but has **not** been confirmed by manual testing.

See [`validation-results.md`](./validation-results.md) for manually observed test results. For the complete request/response schemas and documented status codes, see [`openapi.yaml`](./openapi.yaml).

## 8. Troubleshooting

| Symptom | What to check |
|---|---|
| Backend fails during startup | PostgreSQL running on host port `5433`; `DB_URL`; credentials; migration privileges; Flyway logs |
| `400` on product creation | Required fields, positive dimensions/weight, allowed `outboundFlow` (`PICKING` or `SHIPPING`) |
| `404` for a batch or location | Use IDs that actually exist in the current database |
| Swagger UI shows `OpenAPI definition` or `/v3/api-docs` | Confirm `springdoc.swagger-ui.url=/openapi.yaml`, check that the static YAML exists, restart backend, then hard-refresh Swagger UI |
| Swagger UI shows outdated examples or `Undocumented` | Verify that `docs/api/openapi.yaml` matches `backend/src/main/resources/static/openapi.yaml`; confirm the UI is loading `/openapi.yaml` |
| No feasible recommendations | Check volume, weight, active status, and other hard constraints; `feasibleCount` may be zero |
| Classification has `xyzClass: null` | A product may lack sufficient movement history; see the returned `note` |

## 9. Scope and next steps

The current API does **not** expose a putaway-confirmation endpoint, a product-list endpoint, or the full planned simulation/KPI workflow. Do not call these routes in integration code until implemented.

Continue with the [step-by-step integration tutorial](./integration-tutorial.md), then review [manual validation results](./validation-results.md) and the [OpenAPI specification](./openapi.yaml).
