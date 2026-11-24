# Role-Based Access Control (RBAC)

## Scope and authority

This document records the implemented access policy for API v0.1.0, supporting BA US8.2. `SecurityConfig.java` is the authoritative backend policy; `frontend/src/context/permissions.ts` controls UI route access. The policy is deny by default. See [Security Architecture](security.md) and the [OpenAPI specification](../api/openapi.yaml).

## Roles

| Database / API role | Spring Security authority | Intended responsibility |
|---|---|---|
| `operator` | `ROLE_OPERATOR` | Incoming batch registration, constraint checks and placement recommendations |
| `planner` | `ROLE_PLANNER` | Product catalog creation, classification and planning |
| `manager` | `ROLE_MANAGER` | Warehouse monitoring |
| `business_manager` | `ROLE_BUSINESS_MANAGER` | Business-level evaluation (planned) |
| `admin` | `ROLE_ADMIN` | Account administration (planned) |

Each account has one role. No role hierarchy or automatic administrator override is configured. The picker is a warehouse stakeholder, not a separate implemented account role.

## Implemented backend permission matrix

Allow means the security layer permits the request; business validation may still reject it. Deny means HTTP 403 for an authenticated active account. Unauthenticated requests to protected endpoints receive HTTP 401.

| Method and path | Operator | Planner | Manager | Business Manager | Admin |
|---|---|---|---|---|---|
| `POST /api/auth/login` | Public | Public | Public | Public | Public |
| `GET /api/auth/me` | Allow | Allow | Allow | Allow | Allow |
| `POST /api/products` | Deny | Allow | Deny | Deny | Deny |
| `POST /api/batches` | Allow | Deny | Deny | Deny | Deny |
| `GET /api/warehouses/{warehouseId}/batches` | Allow | Allow | Allow | Deny | Deny |
| `GET /api/warehouses/{warehouseId}/locations` | Allow | Allow | Allow | Deny | Deny |
| `GET /api/constraints/batches/{batchId}/locations/{locationId}` | Allow | Deny | Deny | Deny | Deny |
| `GET /api/recommendations/batches/{batchId}` | Allow | Deny | Deny | Deny | Deny |
| `POST /api/classifications/run` | Deny | Allow | Deny | Deny | Deny |
| Unspecified API endpoints | Deny | Deny | Deny | Deny | Deny |

Swagger UI (`/swagger-ui.html`, `/swagger-ui/**`), `/v3/api-docs/**` and `/openapi.yaml` are public. Internal ERROR dispatches are permitted. Authentication uses the current active database account and role on each Bearer-authenticated request, regardless of the role embedded in the token.

## Implemented frontend route matrix

| Route | Operator | Planner | Manager | Business Manager | Admin | Status |
|---|---|---|---|---|---|---|
| `/` Home | Allow | Allow | Allow | Allow | Allow | Implemented |
| `/receive` Receive batch | Allow | Deny | Deny | Deny | Deny | Registration and recommendations; confirmation not implemented |
| `/locations` Locations | Allow | Allow | Allow | Deny | Deny | Read-only |
| `/import` Data import | Deny | Allow | Deny | Deny | Deny | Placeholder |
| `/compare` Compare strategies | Deny | Allow | Allow | Allow | Deny | Placeholder |

Unauthenticated users must sign in. The UI filters navigation and guards direct route access; forbidden routes display Access denied. The frontend does not grant access to backend operations. Placeholder access does not imply an implemented API. Business Manager and Admin currently have no authorized business API endpoints beyond their authenticated profile.

## Future permissions (not implemented)

The following are proposed allocations for future BA functionality, not current access grants. Confirm the policy when implementing each feature.

| Capability | Proposed roles |
|---|---|
| Confirm placement and record operator decisions | Operator |
| Manage warehouse zones, locations and capacity | Manager |
| Maintain catalog and import data | Planner |
| Run comparative experiments | Planner |
| View experiment results and analytics | Planner, Manager, Business Manager |
| Configure optimization rules | Manager |
| Manage user accounts (US8.3, Could) | Admin |

## Verification and change procedure

The manual RBAC matrix covered five roles across nine protected/unknown endpoint cases (45 checks). Automated MockMvc tests reproduce these combinations and check unauthorized requests, inactive/missing accounts and login behavior. Allowed POST cases use malformed JSON and expect 400 to demonstrate passage through authorization without modifying data; they do not demonstrate successful business writes. The JWT role claim in the matrix tests intentionally differs from some database roles to verify that the database role is authoritative. Playwright covers login/logout, denied receiving access for Manager and Bearer/401 behavior with mocked API responses.

For every new endpoint, define permitted roles explicitly, add the method/path matcher before the deny-all rule, update both OpenAPI copies and this matrix, and add positive and negative authorization tests. Update frontend permissions only when a corresponding route is introduced or changed. Do not grant broad wildcard access merely to make tests pass.
