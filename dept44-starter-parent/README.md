# Dept44 Starter Parent

Base parent POM for all dept44 framework starters. Provides shared plugin configuration for library JAR distribution
including source and Javadoc generation.

## Usage

Set as parent in starter `pom.xml`:

```xml

<parent>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-parent</artifactId>
	<version>8.0.4-SNAPSHOT</version>
	<relativePath>../dept44-starter-parent</relativePath>
</parent>
```

## What It Provides

- Source JAR generation (`maven-source-plugin`)
- Javadoc JAR generation (`maven-javadoc-plugin`)
- Code formatting enforcement (`dept44-formatting-plugin`)
- Spring banner disabled during tests

All dept44 starters inherit from this parent.

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).
© 2024 Sundsvalls kommun
