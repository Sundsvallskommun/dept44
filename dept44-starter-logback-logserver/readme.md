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
- **Rich metadata**: application name, Spring profile, container hostname, instance id, service version, MDC data,
  caller info, root cause details
- **Per-pod tracing fields** (`instance_id`, `service_version`) on every log line, in both console and GELF output
- **Always logs to standard out**, whether GELF is enabled or not

### Override Configuration

If needed, provide your own `logback-spring.xml` in your service to override this configuration completely.

## Configuration

### Environment Variables

|         Variable         | Required |                                Description                                |
|--------------------------|----------|---------------------------------------------------------------------------|
| `LOGSERVER_HOST`         | Yes      | Graylog/ELK host (enables GELF appender)                                  |
| `LOGSERVER_PORT`         | Yes      | Graylog/ELK port                                                          |
| `SPRING_PROFILES_ACTIVE` | No       | Included as static field in log entries                                   |
| `INSTANCE_ID`            | No       | Pod/instance id; emitted as `instance_id` (default `unknown`)             |
| `SERVICE_VERSION`        | No       | Running service version; emitted as `service_version` (default `unknown`) |

### Properties

|                Property                 | Default |              Description              |
|-----------------------------------------|---------|---------------------------------------|
| `dept44.logback.logserver.disabled`     | `false` | Set to `true` to disable GELF logging |
| `dept44.logback.logserver.maxchunksize` | `508`   | Max GELF chunk size in bytes          |

### Chunk Size

The max chunk size is set to 508 bytes per chunk. Since GELF accepts a maximum of 128 chunks, this means it accepts a
maximum of 508 * 128 = 65024 bytes compressed. If you notice that some events in ELK are missing, set
`dept44.logback.logserver.maxchunksize` to a bigger value, e.g., 8192.

### Per-pod tracing fields (`instance_id` / `service_version`)

To trace *which pod produced a log entry* and *which version was running*, every log line carries two per-pod fields,
sourced from environment variables set by the Helm chart:

|       Field       |      Env var      |  Default  |
|-------------------|-------------------|-----------|
| `instance_id`     | `INSTANCE_ID`     | `unknown` |
| `service_version` | `SERVICE_VERSION` | `unknown` |

These are **per-pod constants** — the same category as `application_name`, `spring_profile` and `container_hash` — so
they are injected as logback context properties and emitted as GELF `<staticField>` entries, **not** as per-request MDC
values. As a result, they appear in **every** log line regardless of the producing thread (request, scheduler, async),
in
both the console output and the GELF/Graylog payload.

When the environment variables are absent (e.g., local development), both fields fall back to `unknown`, so log lines
remain valid. The values are typically wired up in the Helm chart, for example:

```yaml
env:
  - name: INSTANCE_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.name      # the pod name
  - name: SERVICE_VERSION
    value: "1.2.3"                     # e.g., the image tag / release version
```

## Key Dependencies

- logback-gelf (Graylog Extended Log Format)
- Janino (conditional logback configuration)

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
