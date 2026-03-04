# Dept44 Starter

Core Spring Boot starter that provides the foundational configuration for all dept44-based microservices. This module is
automatically included via `dept44-service-parent` and should not need to be declared explicitly.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Features

### Problem Handling (RFC 9457)

Standardized error responses using the `Problem` interface:

```java
throw Problem.valueOf(BAD_REQUEST, "Invalid input");
```

Built-in types include `ThrowableProblem`, `ConstraintViolationProblem`, and a global
`ProblemExceptionHandler` that maps exceptions to RFC 9457 JSON responses.

### Request ID Tracking

Automatic `x-request-id` propagation via `RequestId` utility and MDC integration. Each incoming request gets a unique
identifier for tracing across service calls.

### Security

Default `SecurityConfiguration` that disables CSRF and permits all requests. Services requiring authentication should
add `dept44-starter-authorization`.

### OpenAPI / Swagger UI

Auto-configured SpringDoc integration with Swagger UI at `/api-docs`. Configure via `openapi.*` properties in
`application.yml`.

### Object Mapping

Pre-configured Jackson with ISO-8601 date formatting, timezone preservation, and YAML support.

### Logbook Integration

Structured HTTP request/response logging with sensitive data filtering for passwords and tokens.

### Circuit Breaker

Default Resilience4j circuit breaker configuration for fault tolerance.

### Truststore

Automatic SSL/TLS truststore loading from `truststore/*` path. Configure via `dept44.truststore.path`.

## Key Dependencies

- Spring Boot (actuator, security, webmvc, validation, oauth2-client)
- Jackson (JAXB, JSR310, XML, YAML)
- Logbook (Spring Boot & WebFlux)
- SpringDoc OpenAPI
- Spring Cloud (Kubernetes config, circuit breaker via Resilience4j)
- Spring Boot Admin client

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
