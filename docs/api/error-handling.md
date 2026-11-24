# API Error Handling and HTTP Responses

## Scope

This guide describes the current backend implementation and the JSON error format observed during manual testing on 22 November 2026. The implementation was reviewed against `ApiExceptionHandler.java`, `UseCaseException.java`, the service classes and `ApiExceptionHandlerTest.java`.

## Centralized business exception handling

`ApiExceptionHandler` is a `@RestControllerAdvice` that handles `UseCaseException` and calls `HttpServletResponse.sendError(status, message)`.

| Exception kind | HTTP status |
|---|---|
| `INVALID_INPUT` | 400 Bad Request |
| `NOT_FOUND` | 404 Not Found |
| `CONFLICT` | 409 Conflict |

Spring MVC also rejects malformed JSON, invalid parameter types and applicable Bean Validation failures with HTTP 400. These are not necessarily `UseCaseException` instances.

## JSON error response

For requests accepting JSON, the default Spring Boot error response observed in the development environment is:

```json
{
  "timestamp": "2026-11-22T15:44:14.268+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/recommendations/batches/5"
}
```

| Field | Type | Meaning |
|---|---|---|
| `timestamp` | date-time string | Time of error |
| `status` | integer | HTTP status code |
| `error` | string | General HTTP error label |
| `path` | string | Request path |

The handler sets the servlet error reason but does not serialize a custom JSON body. The current application configuration does not explicitly enable exception messages in error bodies. Clients must not assume that `message`, `details`, `code` or `fieldErrors` are present. Content negotiation and deployment settings may affect the response format. The API does not currently guarantee a Problem Details (RFC 9457) response.

## Endpoint status codes

| Endpoint | Success | Documented client errors |
|---|---|---|
| POST `/api/products` | 201 | 400, 404, 409 |
| POST `/api/batches` | 201 | 400, 404, 409 |
| GET `/api/warehouses/{warehouseId}/batches` | 200 | 400 (invalid parameter type) |
| GET `/api/warehouses/{warehouseId}/locations` | 200 | 400 (invalid parameter type) |
| GET `/api/constraints/batches/{batchId}/locations/{locationId}` | 200 | 400, 404 |
| GET `/api/recommendations/batches/{batchId}` | 200 | 400, 404 |
| POST `/api/classifications/run` | 201 | 400, 404 |

Unexpected server failures may produce HTTP 500. No custom 500 response contract is defined. The current backend does not implement HTTP authentication or authorization; no application-specific 401/403 contract is claimed.

## Business exception messages

The following messages are taken from the service implementation. They describe server-side exception reasons, not guaranteed JSON fields.

| Operation | Status | Exception message |
|---|---|---|
| Create product | 400 | Compatibility group not found in warehouse |
| Create product | 404 | Warehouse not found |
| Create product | 409 | SKU already exists in warehouse |
| Register batch | 400 | Archived product cannot receive batches |
| Register batch | 400 | Expiry date must be after receiving date |
| Register batch | 404 | Product not found |
| Register batch | 409 | Batch code already exists in warehouse |
| Check constraints | 404 | Batch not found / Location not found |
| Recommendations | 400 | topN must be between 1 and 100 |
| Recommendations | 400 | Unsupported strategy: followed by the requested strategy |
| Recommendations (`weighted_score`) | 400 | Expired batch cannot be ranked |
| Recommendations | 404 | Batch not found |
| Classification | 400 | Invalid warehouse or date range |
| Classification | 400 | Window must not exceed 3651 days |
| Classification | 400 | Persisted classification runs are supported only for LIVE warehouses |
| Classification | 404 | Warehouse not found |

## Business rejection is not an HTTP error

A constraint check may return HTTP 200 with `feasible: false` and rejection reasons such as `INSUFFICIENT_VOLUME` or `INSUFFICIENT_WEIGHT_CAPACITY`. This is a successful evaluation, not a failed request. A feasible result has `feasible: true` and an empty `rejectionReasons` array.

## Verification evidence

| Scenario | Expected status | Evidence |
|---|---|---|
| Negative product length | 400 | Manual test N1 passed |
| Zero batch quantity | 400 | Manual test N2 passed |
| Missing batch in constraint check | 404 | Manual test N3 passed |
| Recommendation topN=0 | 400 | Manual test N4 passed |
| INVALID_INPUT / NOT_FOUND / CONFLICT mapping | 400 / 404 / 409 | Parameterized handler test in repository |

The handler test checks the status and servlet error reason using standalone MockMvc. It does not verify the serialized JSON after a real servlet error dispatch. Duplicate SKU and batch-code responses are implemented, but a successful live database integration test for these scenarios is not established by this review.

## Client handling

Use the HTTP status as the primary error signal. For 400, correct input; for 404, verify resource identifiers; for 409, resolve the duplicate before retrying. Display a general fallback message if no detailed message is returned. Do not treat a successful constraint rejection as an HTTP failure.

## Swagger UI and contract maintenance

The canonical specification is [openapi.yaml](./openapi.yaml). Keep the served copy at `backend/src/main/resources/static/openapi.yaml` identical. Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

See [Getting Started](./README.md), [Integration Tutorial](./integration-tutorial.md) and [API Validation Results](./validation-results.md).

A future enhancement could introduce a stable machine-readable error code and field-level validation details. Such a change requires coordinated backend, test and OpenAPI updates.
