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

Certificates found on the `truststore/*` classpath path (configurable via `dept44.truststore.path`) are loaded
into an in-memory truststore together with the CA certificates bundled in this starter, and that truststore is
installed as the JVM default via `SSLContext.setDefault(...)`.

**This replaces the JVM default trust anchors rather than adding to them.** The JDK `cacerts` store is not used
by the application once the starter has initialised. The resulting trust set is the bundled CA certificates plus
whatever you add yourself - nothing else. This is intentional: services talk to a known set of endpoints, and a
curated CA list is a smaller attack surface than the ~110 public roots in `cacerts`.

The practical consequences:

- A server certificate that any browser or `curl` accepts may still fail with
  `PKIX path building failed / unable to find valid certification path` if its CA is not in the bundled set.
  This is the expected outcome, not a misconfiguration.
- Importing the CA into the JDK's `cacerts` will **not** help, for the same reason. Add the CA (or the server's
  own certificate) in X.509 (PEM) format to `src/main/resources/truststore/` instead, or point
  `dept44.truststore.path` at a directory that contains it.
- Certificates that have expired are skipped with a warning and are silently not trusted, so an expired PEM in
  `truststore/` behaves the same as a missing one.
- The trust set applies process-wide, but only to clients that honour the JVM default `SSLContext` - which
  includes the default Feign client. OkHttp resolves its own platform trust manager and therefore needs the
  truststore wired in explicitly (`dept44-starter-feign` does this for its OkHttp client).

The set of trust anchors actually installed is logged at `INFO` on startup.

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
