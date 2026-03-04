# Dept44 Logback Logserver Starter

Logback configuration for structured logging via GELF (Graylog Extended Log Format), optimized for centralized log
management with Graylog or ELK.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-logback-logserver</artifactId>
</dependency>
```

This starter is automatically included via `dept44-service-parent`.

## Features

- **GELF UDP appender** for sending structured logs to Graylog/ELK
- **Conditional activation**: Only enabled when `LOGSERVER_HOST` environment variable is set
- **Compressed messages** with configurable chunk size
- **Rich metadata**: application name, Spring profile, container hostname, MDC data, caller info, root cause details
- **Always logs to standard out**, whether GELF is enabled or not

### Override Configuration

If needed, provide your own `logback-spring.xml` in your service to override this configuration completely.

## Configuration

### Environment Variables

|         Variable         | Required |               Description                |
|--------------------------|----------|------------------------------------------|
| `LOGSERVER_HOST`         | Yes      | Graylog/ELK host (enables GELF appender) |
| `LOGSERVER_PORT`         | Yes      | Graylog/ELK port                         |
| `SPRING_PROFILES_ACTIVE` | No       | Included as static field in log entries  |

### Properties

|                Property                 | Default |              Description              |
|-----------------------------------------|---------|---------------------------------------|
| `dept44.logback.logserver.disabled`     | `false` | Set to `true` to disable GELF logging |
| `dept44.logback.logserver.maxchunksize` | `508`   | Max GELF chunk size in bytes          |

### Chunk Size

The max chunk size is set to 508 bytes per chunk. Since GELF accepts a maximum of 128 chunks, this means it accepts a
maximum of 508 * 128 = 65024 bytes compressed. If you notice that some events in ELK are missing, set
`dept44.logback.logserver.maxchunksize` to a bigger value, e.g. 8192.

## Key Dependencies

- logback-gelf (Graylog Extended Log Format)
- Janino (conditional logback configuration)

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
