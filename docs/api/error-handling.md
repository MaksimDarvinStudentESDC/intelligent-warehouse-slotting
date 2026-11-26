# API Error Handling and HTTP Responses

## Scope

This guide describes the current backend implementation and the JSON error format observed during manual testing on 22 November 2026. The implementation was reviewed against `ApiExceptionHandler.java`, `UseCaseException.java`, the service classes and `ApiExceptionHandlerTest.java`.

## Centralized business exception handling

`ApiExceptionHandler` is a `@RestControllerAdvice` that handles `UseCaseException` and returns `ResponseEntity<BusinessError>` with timestamp, status, error, path and message.

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

Business exceptions now serialize `message` explicitly. Framework validation and security errors may have a different body; clients must not assume `message`, `details`, `code` or `fieldErrors` are always present. Content negotiation and deployment settings may affect the response format. The API does not currently guarantee a Problem Details (RFC 9457) response.

## Endpoint status codes

| Endpoint | Success | Documented client errors |
|---|---|---|
| POST `/api/auth/login` | 200 | 400, 401 |
| GET `/api/auth/me` | 200 | 401 |
| POST `/api/products` | 201 | 400, 404, 409 |
| POST `/api/batches` | 201 | 400, 404, 409 |
| GET `/api/warehouses/{warehouseId}/batches` | 200 | 400 (invalid parameter type) |
| GET `/api/warehouses/{warehouseId}/locations` | 200 | 400 (invalid parameter type) |
| GET `/api/constraints/batches/{batchId}/locations/{locationId}` | 200 | 400, 404 |
| GET `/api/recommendations/batches/{batchId}` | 200 | 400, 404 |
| POST `/api/classifications/run` | 201 | 400, 404 |
| POST `/api/placements/confirm` | 201 | 400, 404, 409 |
| GET `/api/warehouses/{warehouseId}/movements` | 200 | 400, 404 |

Unexpected server failures may produce HTTP 500. No custom 500 response contract is defined. Protected endpoints require a valid Bearer JWT. Missing or invalid tokens and inactive or missing accounts produce 401; authenticated accounts without the required role receive 403. Login returns 401 for invalid credentials and 400 for invalid input. Security errors do not guarantee the JSON structure described above. The public login endpoint does not require a token.

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

The handler test checks HTTP status and the serialized `message` using MockMvc. Duplicate SKU and batch-code responses are implemented, but a successful live database integration test for these scenarios is not established by this review.

## Client handling

Use the HTTP status as the primary error signal. For 401, reauthenticate; for 403, check the required role rather than retrying with the same credentials. For 400, correct input; for 404, verify resource identifiers; for 409, inspect the business reason before retrying. Display a general fallback message if no detailed message is returned. Do not treat a successful constraint rejection as an HTTP failure.

## Swagger UI and contract maintenance

The canonical specification is [openapi.yaml](./openapi.yaml). Keep the served copy at `backend/src/main/resources/static/openapi.yaml` identical. Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

See [Getting Started](./README.md), [Integration Tutorial](./integration-tutorial.md) and [API Validation Results](./validation-results.md).

A future enhancement could introduce a stable machine-readable error code and field-level validation details. Such a change requires coordinated backend, test and OpenAPI updates.

## Placement conflicts

`STALE_RECOMMENDATION`, `WAREHOUSE_MISMATCH`, `BATCH_NOT_AWAITING_PUTAWAY` and hard-constraint rejection reasons are returned as HTTP 409 business messages. Refresh recommendations for the same registered batch and explicitly select a location again for recoverable conflicts. An already placed batch must not be confirmed again. Movement history currently displays raw JSON for some errors; friendly formatting is deferred.
