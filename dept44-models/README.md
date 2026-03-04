# Dept44 Models

Shared data models used across services, providing DIGG-compliant pagination and sorting support.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-models</artifactId>
</dependency>
```

## Features

### Pagination Models

- **`PagingMetaData`**: Response metadata with page, limit, count, totalRecords, and totalPages
- **`PagingAndSortingMetaData`**: Extends `PagingMetaData` with sortBy and sortDirection fields. Includes
  `withPageData(Page<?>)` to populate directly from Spring Data results

### Parameter Base Classes

- **`AbstractParameterPagingBase`**: Base class for query parameter paging (extend for custom parameters)
- **`AbstractParameterPagingAndSortingBase`**: Adds sorting with a `sort()` method returning Spring Data `Sort`

### Validation Annotations

- **`@MaxPagingLimit`**: Enforces a maximum page size
- **`@ValidSortByProperty`**: Validates that sort property names are allowed

## Configuration

```yaml
dept44:
  models:
    api:
      paging:
        max:
          limit: 1000  # Default max page size
```

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
