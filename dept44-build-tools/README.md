# Dept44 Build Tools

Maven plugin providing build-time validation checks for OpenAPI properties and truststore certificate expiration.
Automatically executed during the `initialize` phase via `dept44-service-parent`.

## Prerequisites

- Java 25
- Maven 3.9.9+

## Goals

### `dept44:check-openapi-properties`

Validates that required OpenAPI properties (`openapi.name`, `openapi.title`, `openapi.version`) are set in
`application.yml`, `application.yaml`, or `application.properties`.

Skip with: `dept44.check.openapi-properties.skip=true`

### `dept44:check-truststore-validity`

Validates that X.509 certificates in the truststore directory have not expired and are not expiring within a
configurable number of months.

Skip with: `dept44.check.truststore.skip=true`

## Configuration

```xml

<plugin>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-build-tools</artifactId>
	<executions>
		<execution>
			<goals>
				<goal>check-truststore-validity</goal>
				<goal>check-openapi-properties</goal>
			</goals>
			<phase>initialize</phase>
		</execution>
	</executions>
</plugin>
```

### Properties

|                     Property                      |    Default    |                     Description                     |
|---------------------------------------------------|---------------|-----------------------------------------------------|
| `dept44.check.openapi-properties.skip`            | `false`       | Skip OpenAPI property validation                    |
| `dept44.check.truststore.skip`                    | `false`       | Skip truststore certificate validation              |
| `dept44.check.truststore.path`                    | `truststore/` | Path to truststore directory                        |
| `dept44.check.truststore.months-until-expiration` | `1`           | Warn if certificates expire within this many months |

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
