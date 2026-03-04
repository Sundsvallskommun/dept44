# Dept44 Authorization Starter

Adds JWT-based authorization to dept44 services. Extracts user identity and roles from a configurable JWT header and
integrates with Spring Security's `@PreAuthorize` / `@PostAuthorize` annotations.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-authorization</artifactId>
</dependency>
```

## Usage

Enable JWT authorization by annotating your application class:

```java
@ServiceApplication
@EnableJwtAuthorization
public class Application {
	public static void main(final String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
```

Then use Spring Security annotations on your endpoints:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/resource")
public ResponseEntity<String> getAdminResource() {
	// ...
}
```

## Features

- **JWT Extraction Filter**: Reads JWT from a configurable header (default: `x-authorization-info`) and populates the
  Spring `SecurityContext`
- **Role Mapping**: Maps JWT `roles` claim to Spring Security `GrantedAuthority` instances
- **Method Security**: Enables `@PreAuthorize` and `@PostAuthorize` annotations
- **Error Handling**: Returns proper 401 responses for invalid, expired, or malformed tokens

## Configuration

```yaml
jwt:
  authorization:
    headername: x-authorization-info  # Header containing the JWT (default)
```

## Key Dependencies

- JJWT (JSON Web Token API, implementation, GSON)

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
