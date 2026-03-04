# Dept44 Service Parent

Common parent POM for all dept44-based microservices. Configures the complete build pipeline including compilation,
testing, coverage enforcement, Docker image generation, and code formatting.

## Usage

Set as parent in your service `pom.xml`:

```xml

<parent>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-service-parent</artifactId>
	<version>8.0.4-SNAPSHOT</version>
</parent>
```

## What It Provides

### Dependencies

- `dept44-starter` (always included)
- `dept44-starter-logback-logserver` (centralized logging)

### Build Pipeline

|          Plugin          |                                Purpose                                |
|--------------------------|-----------------------------------------------------------------------|
| Spring Boot Maven Plugin | Executable JAR, build info, Docker image with layering                |
| Maven Compiler Plugin    | `-parameters` flag for reflection                                     |
| Maven Failsafe Plugin    | Integration tests (`*IT.java`, `IT*.java`)                            |
| Maven Surefire Plugin    | Unit tests with `-Xmx256m`                                            |
| JaCoCo                   | Coverage enforcement: 85% line, 50% branch (merged UT + IT)           |
| Build Helper             | Adds `src/integration-test/java` and `src/integration-test/resources` |
| dept44-build-tools       | OpenAPI property and truststore certificate checks                    |
| dept44-formatting-plugin | Code formatting enforcement                                           |
| Git Commit ID Plugin     | Generates `git.properties` with commit info                           |

### Test Structure

- Unit tests: `src/test/java/`
- Integration tests: `src/integration-test/java/`
- JaCoCo merges coverage from both phases

### Resource Filtering

Enables Maven property filtering while protecting keystores and certificates (`.jks`, `.p12`, `.pem`, `.pfx`, `.cer`,
`.cert`, `.crt`) from corruption.

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
