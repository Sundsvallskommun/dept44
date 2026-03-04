# Dept44 Feign Starter

Pre-configured Feign client starter that simplifies service-to-service communication with OAuth2 authentication, RFC
9457 error handling, and structured logging.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-feign</artifactId>
</dependency>
```

## Features

- **Auto-configured Feign clients** with Jackson serialization, OkHttp transport, and full request/response logging
- **OAuth2 client credentials** flow via `OAuth2RequestInterceptor`
- **RFC 9457 Problem error decoding**: Converts Problem JSON responses into typed exceptions with proper HTTP status
  propagation
- **Binary-aware decoder**: Handles non-JSON responses (images, files, streams)
- **Pageable support**: Encodes Spring Data `Pageable` parameters as query strings
- **Retry with backoff**: `ActionRetryer` for transient failure handling
- **Circuit breaker**: Integration with Resilience4j

## Usage

Define a Feign client interface:

```java
@FeignClient(
	name = "my-service",
	url = "${integration.my-service.url}",
	configuration = MyServiceConfiguration.class
)
@CircuitBreaker(name = "my-service")
public interface MyServiceClient {
	@GetMapping("/resource/{id}")
	Resource getResource(@PathVariable String id);
}
```

Configure in `application.yml`:

```yaml
spring.security.oauth2.client:
  provider.my-service:
    token-uri: https://auth.example.com/token
  registration.my-service:
    client-id: my-client
    client-secret: my-secret
    authorization-grant-type: client_credentials

integration.my-service:
  url: https://api.example.com
```

## Key Dependencies

- Spring Cloud OpenFeign
- Feign OkHttp, Jackson, SOAP-Jakarta
- Logbook OpenFeign
- Spring Data Commons

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
