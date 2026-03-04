# Dept44 Example

Reference implementation demonstrating dept44 framework patterns and conventions. Implements a "Pet Inventory"
microservice that integrates with an external Petstore API, stores data in MariaDB, manages image uploads, and runs
scheduled jobs.

## Purpose

This module serves as a living example of how to build a dept44-based service. It demonstrates:

- **REST API** with `PetInventoryResource` following the `{Entity}Resource` naming convention
- **Feign integration** with `PetStoreClient` using OAuth2 client credentials and `@CircuitBreaker`
- **JPA entities and repositories** with lifecycle listeners
- **Service layer** with mapper pattern for object transformation
- **Scheduled jobs** using `@Dept44Scheduled` with ShedLock
- **OpenAPI model generation** from external API specs
- **Integration tests** using `AbstractAppTest` and WireMock
- **Multipart file upload/download** handling

## Dependencies

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-feign</artifactId>
</dependency>
<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-jpa</artifactId>
</dependency>
<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-scheduler</artifactId>
</dependency>
<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-test</artifactId>
	<scope>test</scope>
</dependency>
<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-jpa-test</artifactId>
	<scope>test</scope>
</dependency>
```

## Note

This module is not published to Maven Central (`maven.deploy.skip=true`). It exists solely as a reference for
developers building new services.

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
