# Dept44 Test Starter

Testing utilities and configurations to streamline unit and integration testing for dept44 services. Provides a fluent
integration test DSL, WireMock support, and resource loading helpers.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-test</artifactId>
	<scope>test</scope>
</dependency>
```

## Features

### AbstractAppTest

Base class for integration tests with a fluent DSL:

```java
@WireMockAppTestSuite(files = "classpath:/MyEndpoint/", classes = Application.class)
class MyEndpointIT extends AbstractAppTest {

	@Test
	void test01_getResource() {
		setupCall()
			.withServicePath("/2281/resources/123")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("expected.json")
			.sendRequestAndVerifyResponse();
	}
}
```

### @WireMockAppTestSuite

Annotation that configures WireMock on a random port, loads stub mappings from classpath, activates the `it` Spring
profile, and auto-configures `TestRestTemplate`.

### ResourceLoaderExtension

JUnit 5 extension for injecting test resources via `@Load`:

```java
@Test
void test(@Load(value = "request.json", as = JSON) MyRequest request) {
	// request is deserialized from src/test/resources/request.json
}
```

Supports JSON, XML, and plain string resource types.

### Test File Convention

```
src/test/resources/__files/{testMethodName}/
├── mappings/          # WireMock stub mappings
├── common/mappings/   # Shared mappings
└── __files/           # Response/request bodies
```

## Key Dependencies

- Spring Boot test starters (starter-test, resttestclient, webtestclient)
- Spring Cloud Contract WireMock
- WireMock Spring Boot
- Bean Matchers (Hamcrest-based POJO testing)
- JSON Unit + AssertJ (JSON comparison)

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
