# user-service

Microservice for managing users and payment cards.

## Tech Stack

- Java 21, Spring Boot 4
- PostgreSQL, Liquibase
- Redis (caching)
- MapStruct, Lombok
- JUnit 5, Mockito, Testcontainers
- Docker, GitHub Actions, SonarCloud

## Features

- CRUD for User and PaymentCard (max 5 cards per user)
- Pagination and filtering by name/surname
- JPA Auditing, cascading delete
- Redis caching with invalidation
- Global exception handling
- Unit + integration tests (80%+ coverage)
- CI with build, test, SonarQube and Docker image

## Run with Docker

docker compose up --build
