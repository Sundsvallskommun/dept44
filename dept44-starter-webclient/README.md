# Dept44 WebClient Starter

Simplifies HTTP client setup using Spring's WebClient with built-in OAuth2, Logbook logging, and configurable timeouts.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-webclient</artifactId>
</dependency>
```

## Usage

Build a WebClient using the fluent builder:

```java
final var webClient = new WebClientBuilder()
	.withBaseUrl("https://api.example.com")
	.withOAuth2ClientRegistration("my-service")
	.withConnectTimeout(Duration.ofSeconds(5))
	.withReadTimeout(Duration.ofSeconds(30))
	.withLogbook(logbook)
	.build();
```

Or create a Spring declarative HTTP service proxy:

```java
final var myService = new WebClientBuilder()
	.withBaseUrl("https://api.example.com")
	.build(MyServiceClient.class);
```

## Features

- **OAuth2 client credentials** flow with scope support
- **Basic authentication** support
- **Configurable timeouts**: connect (default 10s), read (default 60s), write (default 60s)
- **Logbook integration** for structured request/response logging
- **Request ID propagation** via `RequestIdExchangeFilterFunction`
- **Custom filters and status handlers** via builder methods
- **Declarative HTTP client** proxy generation

## Key Dependencies

- Spring Boot WebFlux starter
- Reactor Netty (non-blocking I/O)

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
