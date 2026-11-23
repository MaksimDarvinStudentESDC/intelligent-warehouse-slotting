# Intelligent Warehouse Slotting

## Project Overview

An educational diploma project focused on developing a web-based system for intelligent warehouse storage location optimization.

The system aims to recommend suitable storage locations for incoming products or batches based on several criteria:

- Product movement frequency and demand dynamics.
- ABC/XYZ product classification.
- Product dimensions and weight.
- Available storage capacity.
- Distance to picking and shipping areas.
- Product compatibility constraints.
- Remaining shelf life and expiry dates.

The system will use a combination of hard constraints and multi-criteria ranking to evaluate suitable storage locations and generate explainable placement recommendations.

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 25, Spring Boot 3.5.7 |
| Frontend | React |
| Database | PostgreSQL 17 |
| Database migrations | Flyway |
| ORM | Spring Data JPA, Hibernate |
| Build tool | Maven |
| Version control | Git, GitHub |
| Containerization | Docker, Docker Compose |

## Main Features

Planned functionality:

- Warehouse structure and storage location management.
- Product and batch management.
- ABC/XYZ classification.
- Intelligent storage location recommendations.
- Storage compatibility validation.
- Shelf-life-aware placement.
- Recommendation explanations.
- Warehouse simulation and performance evaluation.
- KPI calculation and comparison of placement strategies.

## Project Structure

```text
intelligent-warehouse-slotting/
├── backend/
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   ├── db/migration/
│   │   └── application.properties
│   └── pom.xml
├── database/
├── docs/
│   ├── architecture/
│   └── database/
├── frontend/
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

Database migrations are maintained in the backend resources directory and executed by Flyway.

## Project Status

The initial database schema, constraints, database logic, and reference data migrations have been implemented.

The migration process has been successfully tested against a clean database.

The project is currently moving into backend architecture and application development. Demo data population and full business logic implementation are planned for subsequent development stages.

## Security

Application credentials and sensitive configuration must be provided through environment variables.

The `.env` file must not be committed to the repository. The `.env.example` file provides a template for the required configuration variables.

No passwords, API keys or other secrets should be committed to this repository.

## Disclaimer

This is an educational project developed as part of a bachelor's degree diploma.
