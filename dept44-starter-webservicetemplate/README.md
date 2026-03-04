# Dept44 WebServiceTemplate Starter

Streamlined configuration for SOAP-based services using Spring's WebServiceTemplate with SSL/TLS, JAXB marshaling, and
structured logging.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-webservicetemplate</artifactId>
</dependency>
```

## Usage

Build a WebServiceTemplate using the fluent builder:

```java
final var template = new WebServiceTemplateBuilder()
	.withBaseUrl("https://soap.example.com/service")
	.withPackageToScan("com.example.generated")
	.withConnectTimeout(Duration.ofSeconds(10))
	.withReadTimeout(Duration.ofSeconds(60))
	.withLogbook(logbook)
	.build();
```

With client certificate authentication:

```java
final var template = new WebServiceTemplateBuilder()
	.withBaseUrl("https://soap.example.com/service")
	.withPackageToScan("com.example.generated")
	.withKeyStoreFileLocation("classpath:certs/client.p12")
	.withKeyStorePassword("secret")
	.build();
```

## Features

- **JAXB marshaling** with automatic package scanning
- **SSL/TLS** with keystore support (file, classpath, or byte array)
- **Basic authentication** support
- **Configurable timeouts**: connect (default 10s), read (default 60s)
- **Logbook integration** for SOAP message logging
- **Built-in interceptors**:
  - `DefaultFaultInterceptor` — handles SOAP fault responses
  - `RequestIdInterceptor` — propagates `x-request-id` header
  - `RemoveContentLengthHeaderInterceptor` — fixes SOAP server compatibility

## Key Dependencies

- Spring Boot WebServices starter
- Apache HttpComponents 5
- JAXB marshaler

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
