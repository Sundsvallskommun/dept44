# Dept44

The `Dept44` framework provides a comprehensive structure and shared configurations for various Spring Boot
microservices used by Sundsvalls kommun.

## Overview

This project includes modules for common functionality, shared dependencies, and standards that simplify the creation
and management of consistent, high-quality services. Key areas covered include:

- **Dependency Management**: Shared dependencies and versioning for consistency across services.
- **Code Formatting**: Enforced through the `dept44-formatting-plugin`, which provides a standardized code style.
- **Build Tools**: Utilities and plugins configured for streamlined builds and deployments.

## Modules

The Dept44 parent project organizes a suite of purpose-built modules, each crafted to streamline and standardize Spring
Boot microservices. These modules encapsulate reusable components, configurations, and tools to simplify development
across projects. Here’s an overview:

- **`dept44-models`**: Contains shared data models used across services, promoting consistency and reuse of core domain
  objects.
- **`dept44-build-tools`**: Provides essential build utilities and checks, including tools for verifying OpenAPI
  properties and truststore configurations.
- **`dept44-service-parent`**: A common parent for service modules, encapsulating shared configurations and dependencies
  for microservices.
- **`dept44-starter-parent`**: Base parent module for starter configurations, allowing rapid service bootstrapping.
- **`dept44-starter`**: Core Spring Boot starter module that includes essential configurations and dependencies.
- **`dept44-starter-authorization`**: Adds authorization handling utilities and pre-configured security settings.
- **`dept44-starter-feign`**: Pre-configured Feign client starter, simplifying service-to-service communication.
- **`dept44-starter-logback-logserver`**: Logback configuration for structured logging, optimized for centralized log
  management.
- **`dept44-starter-test`**: Testing utilities and configurations to streamline unit and integration testing.
- **`dept44-starter-webclient`**: Simplifies HTTP client setup using Spring’s WebClient, enhancing service
  communication.
- **`dept44-starter-webservicetemplate`**: Streamlined configuration for SOAP-based services using WebServiceTemplate.
- **`dept44-common-validators`**: Provides a set of common validators for data integrity and validation within services.
- **`dept44-example`**: An example microservice using the Dept44 parent configurations as a reference implementation.
- **`dept44-formatting-plugin`**: Maven plugin for consistent code formatting
- **`dept44-starter-jpa`**: Aggregates JPA, Flyway, MariaDB driver, and Hibernate metamodel generation for
  database-backed services.
- **`dept44-starter-jpa-test`**: Aggregates DataJpaTest support, Testcontainers MariaDB, and Spring Boot Testcontainers
  for testing database-backed services.
- **`dept44-starter-scheduler`**: Streamlines scheduled task management with robust exception handling, automated health
  checks, and ShedLock integration.
- **`dept44-starter-rabbitmq`**: Publishing and consuming over RabbitMQ, with safe defaults and publishing the broker has
  confirmed.

For more details, see each module’s README or documentation in the repository.

## Code Formatting Guidelines

To keep our codebase clean and consistent, we use a custom Maven plugin, `dept44-formatting-plugin`, instead of directly
configuring Spotless. This plugin ensures uniform formatting across Java, SQL, JSON, Markdown, and `pom.xml` files.

Our plugin provides two primary commands:

- **Check formatting**:  
  Use `mvn dept44-formatting:check` to validate that code follows the project’s formatting rules.

- **Apply formatting**:  
  Use `mvn dept44-formatting:apply` to automatically apply the required formatting.

The `dept44:formatting-check` goal is also configured to run during the Maven validation phase, ensuring all code meets
formatting standards before builds proceed.
For more details, refer to
the [plugin documentation here](https://github.com/Sundsvallskommun/dept44/dept44-formatting-plugin/blob/main/README.md).

**Note for `dept44`:** Because `dept44-formatting-plugin` is a module of the parent in this project, you need to run the
commands manually for the plugin to take effect on the parent pom.

## Logging & PII

Personal data (`partyId`, personal/organisation numbers, names, e-mail, phone numbers) must never be
logged in cleartext. Mask it with `se.sundsvall.dept44.util.PiiMasker` before logging. Note that
`LogUtils.sanitizeForLogging` guards against *log injection* only — it does **not** hide PII.

See the **[PII logging policy](docs/pii-logging-policy.md)** for the full guidance, the available
masking methods, and the opt-in framework-level masker in `dept44-starter-logback-logserver`.

## Verifying JWTs on incoming requests

`dept44-starter` can reject incoming requests that don't carry a valid, signed JWT in a given header. Each JWT to
check is a *verifier*: a header and the JWKS URLs of its issuer. A servlet filter verifies the signature, expiry and
not-before of every verifier's JWT, and its issuer and audience when configured; all verifiers must pass. The JWT must
have an expiry. A missing, invalid or expired JWT gives `401` with a problem response; if the keys can't be fetched, or
can't be fetched again yet, the response is `503`. Nothing from the JWT is passed on to the application, and nothing is
put into the security context. The filter is off by default and is not available in WebFlux services.

A typical use is an API gateway that adds a signed JWT to every request it forwards; the service then rejects requests
that bypass the gateway:

```yaml
dept44:
  jwt-verification:
    enabled: true
    excluded-paths:
      - /some-callback
    verifiers:
      gateway:
        header-name: x-gateway-jwt
        jwk-set-uris:
          - https://<gateway>/jwks
```

|                   Property                   | Default |                                 Description                                  |
|----------------------------------------------|---------|------------------------------------------------------------------------------|
| `dept44.jwt-verification.enabled`            | `false` | Turns verification on                                                        |
| `dept44.jwt-verification.excluded-paths`     |         | Path patterns no verifier checks, in addition to the built-in ones           |
| `dept44.jwt-verification.verifiers.<name>.*` |         | At least one verifier is required when enabled; the name appears in the logs |

Per verifier, under `dept44.jwt-verification.verifiers.<name>`:

|     Property      | Default |                                                            Description                                                            |
|-------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `header-name`     |         | Required. The header that holds the JWT. Each verifier needs a header of its own                                                  |
| `jwk-set-uris`    |         | Required. The issuer's JWKS URLs, `https` only (`http` to `localhost`, `127.0.0.1` and `[::1]` is allowed); their keys are merged |
| `issuer`          |         | If set (not blank), the JWT's `iss` must equal it. Needed when the keys are shared, as with Entra ID                              |
| `audiences`       |         | If set, the JWT's `aud` must contain one of them                                                                                  |
| `excluded-paths`  |         | Path patterns this verifier doesn't check, on top of the global ones                                                              |
| `algorithms`      | `RS256` | Accepted signature algorithms (`RS*`, `PS*`, `ES*`)                                                                               |
| `connect-timeout` | `5s`    | Connect timeout when fetching the keys, for the worst case (100 ms to 5 min)                                                      |
| `read-timeout`    | `10s`   | Read timeout when fetching the keys, for the worst case (100 ms to 5 min)                                                         |

A signature only proves whose keys signed the JWT. Keys that belong to one issuer only, such as a gateway's own
certificate, need nothing more. Entra ID signs the tokens of every tenant with the same keys, even at a tenant's own key URL, so a verifier for
Entra ID needs `issuer` to accept only your organisation's tokens:

```yaml
ad:
  header-name: x-jwt-ad
  jwk-set-uris:
    - https://login.microsoftonline.com/<tenant-id>/discovery/v2.0/keys
  issuer: https://login.microsoftonline.com/<tenant-id>/v2.0
```

Mind placeholders whose variable may be unset. `issuer: ${ISSUER:}` stops startup, since a set issuer must not be
blank. `audiences: ${AUDIENCES:}` can't be told apart from no audiences, so it turns the audience check off: don't give
audiences an empty default. A placeholder without a default, such as `${ISSUER}` or `audiences[0]: ${AUDIENCE}`, is kept
as the literal text `${ISSUER}` when the variable is unset; startup succeeds, but every JWT is rejected.

The index page (`/`), the OpenAPI documents, Swagger UI (with `/webjars/**`), `/favicon.ico` and actuator are always
excluded, so Spring Boot Admin and the Kubernetes probes keep working. A request is only let through on those paths if the endpoint they belong to really
serves it: `/actuator/1/things` handled by a `/{municipalityId}/{id}/things` controller is verified like any
other request. Anything else that calls the service without the JWT, such as a direct call that bypasses the gateway, has to
be listed in `excluded-paths`. Those patterns are matched on the path alone, so list exact paths: `/some-callback/**`
would also let `/some-callback/1/things` through to a `/{municipalityId}/{id}/things` controller. All paths
are relative to `spring.mvc.servlet.path`, like controller mappings. Only enable it in a service that every caller
reaches with the JWT, apart from the excluded paths.

The verified headers are masked in the payload log (as `XXX`, like `Authorization`): a logged JWT could otherwise be
copied and sent to the service directly until it expires.

The keys are fetched on first use and cached for five minutes, or three times the longest fetch (twice the connect and
read timeouts, plus a second) if that is longer. A request that comes in during the last fetch time before they expire
has them refreshed in the background; if none does, they expire and the next request waits for the fetch. A JWT signed
with a new key makes the keys be fetched at once, at most twice per six seconds; a JWT that needs them fetched beyond
that gets `503`, as does any request while no keys could be fetched yet. If a key endpoint is down, the keys last
fetched from it stay in use for ten times the cache time, 50 minutes by default; after that, requests get `503` until
it is back.

## Status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)

## Contributing

Contributions are welcome! See
the [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This plugin is distributed under the [MIT License](LICENSE).

© 2024 Sundsvalls kommun
