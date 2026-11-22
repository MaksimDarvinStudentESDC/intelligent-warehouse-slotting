# API Error Handling and HTTP Responses

## Scope

This guide documents **observed responses** from manual testing of the Intelligent Warehouse Slotting Optimization System backend on 22 November 2026. It does not imply that the application currently has a custom global exception handler or a standardized field-level error format.

## HTTP status codes

| Status | Meaning | Example operation |
|---|---|---|
| `200 OK` | Successful retrieval, feasibility check, or recommendations | `GET /api/recommendations/batches/5?topN=5` |
| `201 Created` | Product, batch, or classification run created | `POST /api/products`, `POST /api/batches`, `POST /api/classifications/run` |
| `400 Bad Request` | Request rejected, e.g. invalid validation parameter | `POST /api/products` with `lengthMm=-100`; `POST /api/batches` with `quantity=0`; `GET /api/recommendations/batches/5?topN=0` |
| `404 Not Found` | Requested resource does not exist | `GET /api/constraints/batches/999999/locations/1` |
| `409 Conflict` | Potential conflict documented in the API contract (such as a duplicate SKU/batch code); **not manually verified** | `POST /api/products`, `POST /api/batches` |

## Observed error body

The development backend returned the default Spring Boot JSON error structure:

```json
{
  "timestamp": "2026-11-22T15:44:14.268+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/recommendations/batches/5"
}
```

Fields:

| Field | Type | Description |
|---|---|---|
| `timestamp` | date-time string | Time when the error was generated; value changes for every request |
| `status` | integer | HTTP response status |
| `error` | string | General HTTP error label |
| `path` | string | Request path without query string in observed responses |

**Important:** This response does **not** identify the invalid field or provide a detailed business-error message. Do not rely on a `message`, `details`, `code`, or `fieldErrors` property unless the backend is explicitly enhanced to provide one. The response structure may vary with Spring Boot settings and deployment profiles.

## Tested negative scenarios

| Test | Request | Expected | Observed | Result |
|---|---|---|---|---|
| N1 | `POST /api/products` with `lengthMm: -100` | 400 | 400 | PASS |
| N2 | `POST /api/batches` with `quantity: 0` | 400 | 400 | PASS |
| N3 | `GET /api/constraints/batches/999999/locations/1` | 404 | 404 | PASS |
| N4 | `GET /api/recommendations/batches/5?topN=0` | 400 | 400 | PASS |

## Business rejection is not an HTTP error

A feasible-location check returns **HTTP 200** even when a location cannot accommodate a batch. For example, batch `5` against location `1` returns `feasible: false` with rejection codes including `INSUFFICIENT_VOLUME` and `INSUFFICIENT_WEIGHT_CAPACITY`. This is an expected business result, not a failed HTTP request.

For batch `5` against location `9`, the API returns `feasible: true` and `rejectionReasons: []`.

## Swagger UI: fixing “Undocumented”

The checked-in `docs/api/openapi.yaml` defines the documented status codes. To display that file rather than an auto-generated definition, copy it to `backend/src/main/resources/static/openapi.yaml` and configure:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.url=/openapi.yaml
springdoc.swagger-ui.disable-swagger-default-url=true
```

Restart the backend, then refresh Swagger UI. Check that the page title is **Intelligent Warehouse Slotting Optimization System API** and that the specification version is **OpenAPI 3.0.3**. The displayed `201`, `400`, and `404` response descriptions should now match the YAML. If the UI still shows **OpenAPI definition** and `/v3/api-docs`, it is likely using the auto-generated document. The generated `/v3/api-docs` endpoint may remain enabled; it is a different source of documentation.

The `409` descriptions are included as potential contract responses but have not been verified by manual tests. Test these before treating them as confirmed behavior.

## Suggested future enhancement (not implemented)

A centralized `@RestControllerAdvice` could return machine-readable error codes and field-level validation details. This would require changes to backend code, tests, OpenAPI schemas, and example responses. The current documentation deliberately describes the existing behavior instead.
