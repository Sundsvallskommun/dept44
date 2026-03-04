# Dept44 Common Validators

Reusable JSR-303 validation annotations for common data formats, including Swedish-specific validators for personal
numbers, organization numbers, and municipality IDs.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-common-validators</artifactId>
</dependency>
```

## Available Validators

|         Annotation         |           Validates            |          Pattern/Rule          |
|----------------------------|--------------------------------|--------------------------------|
| `@ValidPersonalNumber`     | Swedish personal numbers       | `^(19\|20)[0-9]{10}$`          |
| `@ValidOrganizationNumber` | Swedish organization numbers   | `^([1235789][\d][2-9]\d{7})$`  |
| `@ValidMunicipalityId`     | Municipality IDs (e.g. "2281") | `^\d{4}$`                      |
| `@ValidMobileNumber`       | Swedish mobile numbers         | `^07[02369]\d{7}$`             |
| `@ValidMSISDN`             | International MSISDN format    | `^\+[1-9][\d]{3,14}$`          |
| `@ValidUuid`               | UUID (RFC 4122)                | Standard UUID format           |
| `@ValidBase64`             | Base64 encoding (RFC 4648)     | Standard Base64 format         |
| `@ValidNamespace`          | Namespace identifiers          | 2-32 chars, `A-Za-z0-9-_`      |
| `@OneOf`                   | String enum validation         | Must be one of provided values |
| `@MemberOf`                | Enum member validation         | Must match an enum constant    |

## Usage

```java
public class MyRequest {
	@ValidPersonalNumber
	private String ssn;

	@ValidMunicipalityId
	private String municipalityId;

	@ValidMobileNumber(nullable = true)
	private String phoneNumber;
}
```

All annotations support a `nullable` attribute (default: `false`) to allow null values.

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
