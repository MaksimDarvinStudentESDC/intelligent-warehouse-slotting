# intelligent-warehouse-slotting

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

| Component           | Technology        |
|---------------------|-------------------|
| Backend             | Java, Spring Boot |
| Frontend            | React             |
| Database            | PostgreSQL        |
| Database migrations | Flyway            |
| Version control     | Git, GitHub       |

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

- `backend/` — Spring Boot application.
- `frontend/` — React application.
- `database/` — SQL migrations, seed data, views and database scripts.
- `docs/` — technical documentation and architecture diagrams.

## Project Status

Currently in the initial implementation and database design stage.

## Security

Application credentials and sensitive configuration must be provided through environment variables.

No passwords, API keys or other secrets should be committed to this repository.

## Disclaimer

This is an educational project developed as part of a bachelor's degree diploma.