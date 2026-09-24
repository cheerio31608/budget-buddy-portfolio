# Budget Buddy Development Guide

## Project

Budget Buddy is a Spring Boot backend portfolio project for reliable household finance data processing.

The project should demonstrate backend fundamentals for financial-style domains:

- transaction and balance consistency
- validation and exception handling
- persistence design with JPA
- database constraints and indexes
- testable service logic
- external API integration through Gemini

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- Bean Validation
- PostgreSQL
- H2 for tests
- Flyway
- Gradle
- JUnit 5
- Mockito
- Gemini API

## Architecture Rules

Use a layered architecture:

```text
Controller -> Service -> Repository -> Database
```

- Controller handles HTTP requests and responses only.
- Service owns business rules and transaction boundaries.
- Repository handles persistence queries only.
- Entity represents persisted domain state.
- DTO represents API input/output contracts.
- Exception package owns common business error handling.

Do not put calculation or business validation in controllers.
Do not put business logic in repositories.

## Domain Rules

When creating a transaction, the application must:

- lock the target user balance row when balance can change
- reject future transaction dates
- reject expense transactions when balance is insufficient
- update `User.balance`
- store `Transaction.balanceBefore`
- store `Transaction.balanceAfter`
- keep amount values as `BigDecimal`
- keep transaction type as `TransactionType`
- verify that the category belongs to the requesting user

Balance changes and transaction creation must happen in one `@Transactional` boundary.

## Coding Rules

- Prefer Java records for DTOs.
- Keep Entity classes as JPA-compatible classes.
- Use `Optional` only as repository return types or short-lived control flow.
- Keep DTO and Entity separated.
- Use Lombok minimally. Entity builders are acceptable for tests and focused creation, but DTOs should prefer records.
- Avoid magic numbers and magic strings. Use enums, constants, or configuration properties.
- Keep comments short and useful.

## Security & Secrets Policy

Never commit real secrets.

- Do not hardcode real passwords, API keys, or tokens in source, properties, README, or scripts.
- Inject sensitive values through environment variables or external configuration.
- Local defaults may use safe placeholders such as `postgres`, `1234`, `admin`, or `your-api-key`.
- README examples must use generic demo values only.
- New secret-bearing files must be added to `.gitignore` before use.

## Error Handling

Use the common exception flow:

- `BusinessException`
- `ErrorCode`
- `ErrorResponse`
- `GlobalExceptionHandler`

Validation failures should return a structured `ErrorResponse`.

## Testing Rules

When adding a new Service:

- add service unit tests for business success and failure cases

When adding a new Repository query:

- add repository tests for the query behavior

When adding financial consistency behavior:

- test balance changes
- test insufficient balance
- test snapshot values
- test relevant concurrency behavior when applicable

Tests should run with the `test` profile and should not depend on a developer's local PostgreSQL database.
