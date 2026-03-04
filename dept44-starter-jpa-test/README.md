# Dept44 JPA Test Starter

A dependency aggregator for testing database-backed services, providing DataJpaTest support, Testcontainers MariaDB, and
Spring Boot Testcontainers integration.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml` with test scope:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-jpa-test</artifactId>
	<scope>test</scope>
</dependency>
```

## Included Dependencies

- **`spring-boot-starter-data-jpa-test`**: `@DataJpaTest` slice testing support
- **`testcontainers-mariadb`**: MariaDB container for integration tests
- **`spring-boot-testcontainers`**: Spring Boot Testcontainers integration (`@ServiceConnection`)

## Usage

This starter replaces the following individual test declarations:

```xml

<dependency>
	<groupId>org.testcontainers</groupId>
	<artifactId>testcontainers-mariadb</artifactId>
	<scope>test</scope>
</dependency>
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-testcontainers</artifactId>
	<scope>test</scope>
</dependency>
```

This starter does not depend on `dept44-starter-jpa` or `dept44-starter-test` — services declare those separately. This
keeps the starters orthogonal, matching Spring Boot's own pattern.

For runtime dependencies, see
[dept44-starter-jpa](../dept44-starter-jpa).

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
