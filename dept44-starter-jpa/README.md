# Dept44 JPA Starter

A dependency aggregator for database-backed services, providing JPA, Flyway migrations, MariaDB driver, and Hibernate
metamodel generation in a single dependency.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-jpa</artifactId>
</dependency>
```

## Included Dependencies

- **`spring-boot-starter-data-jpa`**: Spring Data JPA and Hibernate ORM
- **`spring-boot-starter-flyway`**: Database migration framework
- **`flyway-mysql`**: Flyway dialect for MySQL/MariaDB
- **`mariadb-java-client`**: MariaDB JDBC driver
- **`hibernate-processor`** (provided): JPA metamodel generation for type-safe criteria queries

## Usage

This starter replaces the following individual declarations that database services typically need:

```xml

<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
	<groupId>org.mariadb.jdbc</groupId>
	<artifactId>mariadb-java-client</artifactId>
</dependency>
<dependency>
	<groupId>org.flywaydb</groupId>
	<artifactId>flyway-mysql</artifactId>
</dependency>
<dependency>
	<groupId>org.hibernate.orm</groupId>
	<artifactId>hibernate-processor</artifactId>
	<scope>provided</scope>
</dependency>
```

Service-specific JPA additions such as JaVers, spring-filter, or hypersistence-utils should still be declared directly
in the services that need them.

For test dependencies, see
[dept44-starter-jpa-test](../dept44-starter-jpa-test).

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
