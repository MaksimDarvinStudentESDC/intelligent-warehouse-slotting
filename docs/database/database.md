# Database Architecture

## 1. Overview

The Intelligent Warehouse Slotting Optimization System uses PostgreSQL as its primary relational database.

The database is designed to support warehouse management, inventory tracking, ABC/XYZ product classification, storage location recommendations, and simulation-based evaluation of different slotting strategies.

The schema is designed around data integrity, warehouse isolation, reproducible experiments, and protection of historical simulation results.

### Technology

| Component | Technology |
|---|---|
| Database Management System | PostgreSQL 17 |
| Database Migration Tool | Flyway |
| ORM | Hibernate / Spring Data JPA |
| Database Driver | PostgreSQL JDBC |
| Containerization | Docker Compose |


## 2. Database Structure

The database consists of 21 application tables organized into logical functional groups.

### 2.1 Warehouse Data

These tables represent the physical warehouse structure and inventory:

| Table | Description |
|---|---|
| `warehouses` | Warehouse records, including live and simulation warehouses |
| `zones` | Warehouse zones: receiving, picking, shipping and storage |
| `locations` | Individual storage locations with capacity and occupancy information |
| `compat_groups` | Product compatibility groups |
| `compat_conflicts` | Pairs of incompatible product groups |
| `products` | Product catalog with dimensions, weight, shelf life and outbound flow |
| `batches` | Incoming product batches and their current storage status |
| `outbound_history` | Historical product picking activity |
| `stock_movements` | Audit records of warehouse movements |
| `import_jobs` | CSV import jobs, progress and validation errors |

### 2.2 User Management

| Table | Description |
|---|---|
| `users` | System users, their roles and account status |

User-related data includes password hashes rather than plain-text passwords. User anonymization is supported to preserve audit history.

### 2.3 ABC/XYZ Classification

| Table | Description |
|---|---|
| `classification_runs` | Records of classification executions and calculation parameters |
| `product_classes` | ABC and XYZ classifications assigned to products |

Classification is based on historical outbound activity and demand variability.

### 2.4 Slotting and Recommendations

| Table | Description |
|---|---|
| `strategy_configs` | Configuration of placement strategies |
| `recommendations` | Recommendation requests and their results |
| `recommendation_items` | Ranked candidate storage locations with scores and explanations |

The database supports three strategy types:

- **B1 — Nearest Feasible:** selects the nearest feasible storage location.
- **B2 — Pick Frequency:** uses pick-frequency classification and warehouse access tiers.
- **Weighted Score:** ranks feasible locations using a multi-criteria weighted scoring approach.

### 2.5 Experiments and Simulation

| Table | Description |
|---|---|
| `experiments` | Configuration and results of strategy comparison experiments |
| `scenarios` | Simulation scenarios and their parameters |
| `sim_runs` | Individual simulation executions |
| `sim_operations` | Simulated putaway, relocation and rejection operations |
| `sim_run_metrics` | KPI and supporting metric values |

The simulation model supports reproducible comparisons of placement strategies using controlled scenarios and shared arrival sequences.

## 3. Relationships and Data Integrity

The database uses primary keys, foreign keys, unique constraints, CHECK constraints, and indexes to maintain data consistency.

### 3.1 Primary and Foreign Keys

- Every main entity uses a primary key.
- Foreign keys enforce relationships between related entities.
- Composite foreign keys are used where necessary to ensure that related records belong to the same warehouse.
- Simulation records reference their corresponding scenario and simulation warehouse.

### 3.2 Warehouse Isolation

Warehouse ownership is represented through `warehouse_id`.

Composite foreign keys prevent records from referencing products, batches, locations, or other entities belonging to a different warehouse.

The design distinguishes between:

- A single live warehouse used for normal warehouse operations.
- Simulation warehouses associated with individual experiment scenarios.

### 3.3 CHECK Constraints

CHECK constraints enforce important business and data integrity rules directly in PostgreSQL.

Examples include:

- Positive product dimensions, weight and volume.
- Valid batch quantities and expiration dates.
- Storage location occupancy within capacity limits.
- Non-negative distances.
- Valid relationships between batch status and storage location.
- Valid product compatibility group pairs.
- Correct simulation operation structures.
- Valid strategy weights and thresholds.
- Non-empty recommendation explanations.

### 3.4 Unique and Partial Indexes

Unique indexes prevent duplicate business records and enforce specific architectural rules.

Partial unique indexes are used for conditions such as:

- Exactly one live warehouse.
- One receiving, picking and shipping zone per warehouse.
- At most one default strategy configuration.
- Unique batch arrival sequence within a warehouse.
- At most one initial putaway or rejection per batch, strategy and simulation run.

Additional indexes support primary key, foreign key and query access patterns.

## 4. Database Logic

Some integrity and lifecycle rules are implemented directly in PostgreSQL through functions and triggers.

### 4.1 Triggers and Functions

The database includes five triggers and five functions.

Important responsibilities include:

- Preventing modifications to frozen strategy configurations.
- Protecting completed validation runs from further modification.
- Preventing deletion of scenarios with final validation runs.
- Anonymizing user information while preserving audit records.
- Purging simulation warehouse data under controlled conditions.

### 4.2 Views

Three database views provide useful derived information and consistency checks:

| View | Purpose |
|---|---|
| `v_location_occupancy_drift` | Detects differences between stored location occupancy and the sum of placed batches |
| `v_product_destination_zone` | Determines the destination zone based on product outbound flow |
| `v_warehouses_missing_special_zones` | Identifies warehouses missing required special zones |

### 4.3 Service-Layer Responsibilities

Not all business rules are enforced by the database.

Rules involving multiple records or application workflows are handled by the backend service layer.

Examples include:

- Rechecking product compatibility before placement.
- Calculating batch volume and weight.
- Ensuring simulation warehouses remain stateless.
- Validating experiment configuration before locking.
- Controlling whether tuning or validation runs can be created.
- Ensuring simulation operations reference strategies belonging to the corresponding experiment.

This separation allows the database to enforce structural integrity while the application handles more complex business workflows.

## 5. Database Migrations

Flyway is used to manage database schema changes.

Migration scripts are stored in:

`backend/src/main/resources/db/migration/`

Current migrations:

| Migration | Description |
|---|---|
| `V1__create_schema.sql` | Creates the initial database schema, tables, types and relationships |
| `V2__add_constraints_and_logic.sql` | Adds additional constraints, indexes, functions, triggers and views |
| `V3__insert_reference_data.sql` | Inserts initial reference data, including placement strategy configurations |

Flyway tracks executed migrations in the `flyway_schema_history` table.

Migrations are applied automatically when the Spring Boot application starts.

## 6. Database Roles and Security

The database uses separate PostgreSQL roles to follow the principle of least privilege.

| Role | Responsibility |
|---|---|
| `warehouse_admin` | Database administration and role management |
| `warehouse_owner` | Ownership of database objects |
| `warehouse_migrator` | Execution of Flyway migrations |
| `warehouse_app` | Runtime access for the application |

### 6.1 Permission Model

- `warehouse_admin` has administrative privileges.
- `warehouse_owner` owns application database objects.
- `warehouse_migrator` is a member of `warehouse_owner` and has the required schema creation permissions.
- `warehouse_app` has schema usage and database connection permissions but cannot create schema objects.

The runtime application role is intentionally separated from the migration role.

Database credentials are supplied through environment variables and are not stored in source code.

## 7. Deployment and Verification

The database runs in a Docker container using Docker Compose.

The PostgreSQL service uses a persistent Docker volume to retain database data between container restarts.

### 7.1 Clean Database Deployment

The migration process was tested against a separate, initially empty database named `warehouse_slotting_test`.

The verification included:

1. Creating a clean database.
2. Configuring the required database roles and permissions.
3. Starting the Spring Boot application with the test database connection.
4. Running Flyway migrations from the beginning.
5. Verifying that all migrations completed successfully.
6. Checking the resulting application tables and reference data.
7. Verifying the database role permissions.

### 7.2 Verification Results

The clean database deployment completed successfully.

The following results were confirmed:

- All three Flyway migrations executed successfully.
- The expected 21 application tables were created.
- Initial strategy reference records for B1 and B2 were present.
- The application successfully initialized against the test database.
- The runtime role had schema usage permissions without schema creation permissions.
- The migration role had the required schema creation permissions.

The test database is maintained separately from the main development database.

## 8. Database Documentation

The detailed database structure is documented separately in the Data Dictionary and ER model.

These resources provide:

- Table and column definitions.
- Data types and default values.
- Primary and foreign key relationships.
- Enumeration types and values.
- CHECK constraints and indexes.
- Trigger and function descriptions.
- Service-layer business rules.

They complement this document and provide a more detailed technical reference.

## 9. Current Implementation Status

The database schema, initial migration scripts, additional integrity rules, database functions, triggers, views and reference strategy configurations have been implemented.

Clean database deployment and role permission checks have been successfully verified.

Population of the database with demonstration warehouse data, products, batches and outbound history is postponed until the current database version is reviewed and approved by the project supervisor.