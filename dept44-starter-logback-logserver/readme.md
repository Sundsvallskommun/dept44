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

|                   Property                   | Default |                       Description                       |
|----------------------------------------------|---------|---------------------------------------------------------|
| `dept44.logback.logserver.disabled`          | `false` | Set to `true` to disable GELF logging                   |
| `dept44.logback.logserver.maxchunksize`      | `508`   | Max GELF chunk size in bytes                            |
| `dept44.logback.pii-masking.enabled`         | `false` | Set to `true` to mask PII in all log output (see below) |
| `dept44.logback.pii-masking.uuid`            | `true`  | Set to `false` to leave UUIDs whole when masking is on  |
| `dept44.logback.pii-masking.personal-number` | `true`  | Set to `false` to leave personal identity numbers whole |
| `dept44.logback.pii-masking.phone-number`    | `true`  | Set to `false` to leave phone numbers whole             |
| `dept44.logback.pii-masking.email`           | `true`  | Set to `false` to leave e-mail addresses whole          |

### PII masking

Set `dept44.logback.pii-masking.enabled=true` to mask personally identifiable information in **every** log line -
console and GELF - regardless of whether the producing code remembered to mask the value itself. The following are
masked:

> **Before 8.1.0, GELF was not masked.** The GELF layouts were configured as `shortPatternLayout` and
> `fullPatternLayout`, names that logback-gelf 6 does not know. Logback ignores such elements, so the encoder used its
> own `%m` layouts, and only the console was masked. A service with its own `logback-spring.xml` that copied the GELF
> encoder must rename them to `shortMessageLayout` and `fullMessageLayout`. A warning such as
> `Ignoring unknown property [shortPatternLayout]` at startup shows the problem.

|           Category            |             Example input              |   Masked output    |
|-------------------------------|----------------------------------------|--------------------|
| Swedish personal identity no. | `900101-1234` / `199001011234`         | `******-****`      |
| UUID (`partyId`)              | `f47ac10b-58cc-4372-a567-0e02b2c3d479` | `f47a...`          |
| E-mail address                | `john.doe@example.com`                 | `j***@example.com` |
| Swedish phone number          | `070-123 45 67`                        | `***-*** ** **`    |

The masking is performed by the `%maskPii` pattern conversion word (class
`se.sundsvall.dept44.util.PiiMasker` / `se.sundsvall.dept44.logback.PiiMaskingConverter`). When disabled it renders
identically to `%m`, so there is no behavioural change in the default configuration.

#### Turning a single category off

Each category is masked unless its own property says `false`, so enabling masking masks everything, as before.

The category worth a decision is **`uuid`**. A UUID is masked to its first four characters, and four hexadecimal
characters are not enough to tie a log line to an errand, an attachment or a message. A service that traces an incident
by `errandId` or `attachmentId` - or that masks its HTTP payloads field by field and deliberately keeps the identifiers
- loses that ability when every UUID in every log line is truncated. Such a service can keep its identifiers and still
have the other categories masked:

```yaml
dept44:
  logback:
    pii-masking:
      enabled: true
      uuid: false
```

With `uuid: false`, every `partyId` is logged in clear text unless the code masked it itself. The UUID rule cannot tell
a `partyId` from an `errandId`, so the safety net no longer covers it in any log line, the payload log included. A
`partyId` is a direct identifier against the Party service and can be looked up to a personal identity number, which is
why UUID masking exists at all. Turning `uuid` off is an exception to the core rule of the
[PII logging policy](../docs/pii-logging-policy.md) and needs a decision, not just a configuration change. Mask `partyId`
at the source (`PiiMasker.maskUuid`, or a payload mask that does not keep it) before turning it off.

A UUID that is **not** masked is stepped over by the phone-number and personal-identity-number rules, because the
personal-identity-number shape matches the digits of one: the first two groups of `12345678-1234-1234-1234-123456789012`
are a ten-digit number as far as that pattern is concerned. E-mail addresses are still read from the whole line, so an
address carrying a UUID, such as `john.doe+f47ac10b-58cc-4372-a567-0e02b2c3d479@example.com`, is masked as one address.

> **Note:** this is distinct from `LogUtils.sanitizeForLogging`, which guards against *log injection*, not PII.

**Limitations:**

- Only the rendered **message** is masked. MDC values, caller data and exception/stack-trace content emitted by the GELF
  encoder as separate fields are **not** masked.
- **Street addresses are not masked.** Free-form addresses have no stable shape a regex can match without masking large
  amounts of ordinary log text; mask them field-by-field at the source instead (or via Logbook JSONPath/XPath body
  masking for HTTP payloads).
- The personal-identity-number rule keys on the digit shape `\b\d{6}(?:\d{2})?[-+]?\d{4}\b` (ten-digit `NNNNNN-NNNN`
  and twelve-digit `NNNNNNNN-NNNN` forms) and cannot distinguish a real personnummer from any other free-standing ten-
  or twelve-digit number, so some non-PII numbers may be masked.
- Phone-number masking only catches structured Swedish numbers (a `+46`/`0046` prefix or an internal separator); a bare
  digit run like `0701234567` is treated as a personal identity number.
- A service that provides its own `logback-spring.xml` (see [Override Configuration](#override-configuration)) must
  replicate the `<conversionRule>` and `%maskPii` patterns to keep masking.

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
