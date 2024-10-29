# Dept44

The `Dept44` framework provides a comprehensive structure and shared configurations for various Spring Boot microservices used by Sundsvalls kommun.

## Overview

This project includes modules for common functionality, shared dependencies, and standards that simplify the creation and management of consistent, high-quality services. Key areas covered include:

- **Dependency Management**: Shared dependencies and versioning for consistency across services.
- **Code Formatting**: Enforced through the `dept44-formatting-plugin`, which provides a standardized code style.
- **Build Tools**: Utilities and plugins configured for streamlined builds and deployments.

## Modules

The Dept44 parent project organizes a suite of purpose-built modules, each crafted to streamline and standardize Spring Boot microservices. These modules encapsulate reusable components, configurations, and tools to simplify development across projects. Here’s an overview:

- **`dept44-models`**: Contains shared data models used across services, promoting consistency and reuse of core domain objects.
- **`dept44-build-tools`**: Provides essential build utilities and checks, including tools for verifying OpenAPI properties and truststore configurations.
- **`dept44-service-parent`**: A common parent for service modules, encapsulating shared configurations and dependencies for microservices.
- **`dept44-starter-parent`**: Base parent module for starter configurations, allowing rapid service bootstrapping.
- **`dept44-starter`**: Core Spring Boot starter module that includes essential configurations and dependencies.
- **`dept44-starter-authorization`**: Adds authorization handling utilities and pre-configured security settings.
- **`dept44-starter-feign`**: Pre-configured Feign client starter, simplifying service-to-service communication.
- **`dept44-starter-logback-logserver`**: Logback configuration for structured logging, optimized for centralized log management.
- **`dept44-starter-test`**: Testing utilities and configurations to streamline unit and integration testing.
- **`dept44-starter-webclient`**: Simplifies HTTP client setup using Spring’s WebClient, enhancing service communication.
- **`dept44-starter-webservicetemplate`**: Streamlined configuration for SOAP-based services using WebServiceTemplate.
- **`dept44-common-validators`**: Provides a set of common validators for data integrity and validation within services.
- **`dept44-example`**: An example microservice using the Dept44 parent configurations as a reference implementation.
- **`dept44-formatting-plugin`**: Maven plugin for consistent code formatting

For more details, see each module’s README or documentation in the repository.

## Code Formatting Guidelines

To keep our codebase clean and consistent, we use a custom Maven plugin, `dept44-formatting-plugin`, instead of directly configuring Spotless. This plugin ensures uniform formatting across Java, SQL, JSON, Markdown, and `pom.xml` files.

Our plugin provides two primary commands:

- **Check formatting**:  
  Use `mvn dept44:formatting-check` to validate that code follows the project’s formatting rules.

- **Apply formatting**:  
  Use `mvn dept44:formatting-apply` to automatically apply the required formatting.

**Note for `dept44`:** Because `dept44-formatting-plugin` is a module of the parent in this project, you may need to run the full command:

```bash
mvn se.sundsvall.dept44:dept44-formatting-plugin:6.0.3-SNAPSHOT:formatting-apply
```

The `dept44:formatting-check` goal is also configured to run during the Maven validation phase, ensuring all code meets formatting standards before builds proceed.
For more details, refer to the [plugin documentation here](https://github.com/Sundsvallskommun/dept44/dept44-formatting-plugin/blob/main/README.md).

## Status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_dept44&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_dept44)

## Contributing

Contributions are welcome! See the [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This plugin is distributed under the [MIT License](LICENSE).

© 2024 Sundsvalls kommun
