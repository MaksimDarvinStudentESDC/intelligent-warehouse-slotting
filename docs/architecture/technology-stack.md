# Technology stack — current implementation

| Area | Technology |
|---|---|
| Backend | Java 25, Spring Boot 3.5.7, Maven, Spring MVC, Spring Security, JPA/Hibernate |
| Persistence | PostgreSQL 17, Flyway, Docker Compose for database |
| Frontend | React 19, TypeScript 5.9, Vite 6, React Router 7, TanStack Query 5, Zod 4 |
| API | REST JSON, OpenAPI 3.0.3, Swagger UI, Bearer JWT |
| Testing | JUnit, Mockito, MockMvc, JaCoCo, Vitest, Playwright, axe |
| CI | GitHub Actions |

Testcontainers infrastructure and real PostgreSQL concurrency verification are deferred. No production deployment or new CI result is asserted by this document.
