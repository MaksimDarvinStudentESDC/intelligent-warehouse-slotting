# Intelligent Warehouse Slotting Optimization System — Getting Started

## 1. Overview

The Intelligent Warehouse Slotting Optimization System is a web-based application developed as part of a bachelor's degree project. The system provides storage location recommendations for incoming product batches based on warehouse capacity, product characteristics, compatibility constraints, demand classification, shelf life, and distances to operational zones.

This guide explains how to configure the development environment, initialize the database, start the backend, access the API documentation, and execute the first API request.

## 2. Prerequisites

| Software | Version |
|---|---|
| Java Development Kit | 25 |
| Maven | 3.9+ recommended |
| PostgreSQL | 17 |
| Docker and Docker Compose | Docker Compose V2 |
| Git | 2.x |
| Node.js and npm | Required for frontend development |

Verify the installed tools:

```bash
java -version
mvn -version
docker --version
docker compose version
git --version
```

Ensure Maven uses Java 25.

## 3. Project Structure

```text
intelligent-warehouse-slotting/
├── backend/
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   ├── db/migration/
│   │   ├── static/openapi.yaml
│   │   ├── demo_warehouse.sql
│   │   └── application.properties
│   └── pom.xml
├── database/
├── docs/
│   ├── api/
│   ├── architecture/
│   └── database/
├── frontend/
├── .env.example
└── docker-compose.yml
```

The backend uses Java and Spring Boot, PostgreSQL for persistent storage, and Flyway for database migrations.

## 4. Environment Configuration

From the project root, create a local `.env` file.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux or macOS:

```bash
cp .env.example .env
```

Configure the following variables:

```dotenv
POSTGRES_DB=warehouse_slotting
POSTGRES_ADMIN_USER=postgres
POSTGRES_ADMIN_PASSWORD=change_me_admin
DB_URL=jdbc:postgresql://localhost:5433/warehouse_slotting
DB_APP_USERNAME=warehouse_app
DB_APP_PASSWORD=change_me_app
DB_MIGRATOR_USERNAME=warehouse_migrator
DB_MIGRATOR_PASSWORD=change_me_migrator
```

Replace example passwords with local development credentials. Do not commit `.env` to version control.

## 5. Start PostgreSQL

```bash
docker compose up -d
docker compose ps
```

| Parameter | Value |
|---|---|
| Host | localhost |
| Port | 5433 |
| Database | warehouse_slotting |
| Container | warehouse-slotting-db |

Wait until PostgreSQL is ready.

## 6. Initialize Database Roles

| Role | Responsibility |
|---|---|
| warehouse_owner | Database object ownership |
| warehouse_migrator | Flyway schema migrations |
| warehouse_app | Application runtime access |

For a new local database, connect as the PostgreSQL administrator:

```bash
docker exec -it warehouse-slotting-db psql -U postgres -d warehouse_slotting
```

Create the required roles if they do not already exist:

```sql
CREATE ROLE warehouse_owner NOLOGIN;
CREATE ROLE warehouse_migrator LOGIN PASSWORD 'change_me_migrator';
CREATE ROLE warehouse_app LOGIN PASSWORD 'change_me_app';
GRANT warehouse_owner TO warehouse_migrator;
GRANT CONNECT ON DATABASE warehouse_slotting TO warehouse_migrator, warehouse_app;
GRANT USAGE, CREATE ON SCHEMA public TO warehouse_owner, warehouse_migrator;
GRANT USAGE ON SCHEMA public TO warehouse_app;
ALTER DEFAULT PRIVILEGES FOR ROLE warehouse_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO warehouse_app;
ALTER DEFAULT PRIVILEGES FOR ROLE warehouse_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO warehouse_app;
```

Use passwords matching the local environment. Do not recreate existing roles. Exit with `\q`.

## 7. Start the Backend

Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/warehouse_slotting"
$env:DB_APP_USERNAME = "warehouse_app"
$env:DB_APP_PASSWORD = "change_me_app"
$env:DB_MIGRATOR_USERNAME = "warehouse_migrator"
$env:DB_MIGRATOR_PASSWORD = "change_me_migrator"
cd backend
mvn spring-boot:run
```

Linux or macOS:

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
```

Docker Compose reads `.env` automatically, but a separately launched Spring Boot process requires its own environment configuration. During startup Flyway executes V1 (schema creation), V2 (constraints and supporting logic), and V3 (reference data initialization). Hibernate validates the schema.

Backend URL: `http://localhost:8080`.

## 8. Initialize Demo Warehouse Data

The development-only script `backend/src/main/resources/demo_warehouse.sql` creates a demonstration warehouse, four operational zones and twelve storage locations (ST-01 through ST-12). Run it after successful Flyway migration, from the project root:

```bash
docker cp backend/src/main/resources/demo_warehouse.sql warehouse-slotting-db:/tmp/demo_warehouse.sql
docker exec -it warehouse-slotting-db psql -U postgres -d warehouse_slotting -v ON_ERROR_STOP=1 -f /tmp/demo_warehouse.sql
docker exec -it warehouse-slotting-db psql -U postgres -d warehouse_slotting -c "SELECT id, name FROM warehouses;"
```

Use the actual warehouse ID returned by the database in subsequent requests.

## 9. Access API Documentation

The REST API is documented using OpenAPI 3.0.3.

| Resource | Address |
|---|---|
| API base URL | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI specification | http://localhost:8080/openapi.yaml |

Swagger UI allows developers to inspect endpoints, request parameters and response models, and execute requests. The repository specification is [openapi.yaml](./openapi.yaml).

## 10. Execute Your First API Request

Retrieve active storage locations using `GET /api/warehouses/1/locations`. Replace `1` with the actual warehouse ID if necessary.

```bash
curl -i http://localhost:8080/api/warehouses/1/locations
```

Windows PowerShell:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/warehouses/1/locations"
```

Expected status: `200 OK`.

Example response:

```json
[
  {
    "id": 1,
    "code": "ST-01",
    "zoneId": 4,
    "capacityDm3": 100,
    "occupiedDm3": 0,
    "maxWeightKg": 60,
    "distanceToReceivingM": 10,
    "distanceToPickingM": 5,
    "distanceToShippingM": 9,
    "active": true
  }
]
```

Actual identifiers and values depend on database contents. A successful response confirms that the backend can retrieve location data from PostgreSQL.

## 11. Available API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/products` | Create a product |
| POST | `/api/batches` | Register an incoming batch |
| GET | `/api/warehouses/{warehouseId}/batches` | Retrieve warehouse batches |
| GET | `/api/warehouses/{warehouseId}/locations` | Retrieve active storage locations |
| GET | `/api/constraints/batches/{batchId}/locations/{locationId}` | Evaluate storage constraints |
| GET | `/api/recommendations/batches/{batchId}` | Generate ranked recommendations |
| POST | `/api/classifications/run` | Execute ABC/XYZ classification |

For complete request and response models, consult the OpenAPI specification.

## 12. Start the Frontend (Optional)

Open a separate terminal from the project root:

```bash
cd frontend
npm ci
npm run dev
```

Open the URL displayed by Vite, normally `http://localhost:5173`. Keep the backend running to use real API data.

## 13. Troubleshooting

| Problem | Possible solution |
|---|---|
| PostgreSQL connection refused | Verify the container is running and port 5433 is available. |
| Password authentication failed | Verify credentials and environment variables. |
| Role warehouse_owner does not exist | Complete role initialization before starting Flyway. |
| Permission denied for table | Verify runtime database permissions. |
| Unsupported Java version | Verify JAVA_HOME and the Java version used by Maven. |
| Swagger UI unavailable | Verify the backend is running on port 8080. |
| Warehouse not found | Initialize demo data and verify the warehouse ID. |
| Empty location list | Verify the warehouse contains active locations. |
| Frontend API connection error | Verify the backend is running on port 8080. |

## 14. Further Documentation

- [API Integration Tutorial](./integration-tutorial.md)
- [OpenAPI Specification](./openapi.yaml)
- [Error Handling](./error-handling.md)
- [API Validation Results](./validation-results.md)

## 15. Security and Project Scope

Database credentials must be provided through environment variables and must not be committed to the repository. This setup is intended for local development and educational evaluation. Database roles provide separation of database privileges; they do not replace HTTP authentication or application-level authorization.
