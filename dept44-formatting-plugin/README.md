# Dept44 Formatting Maven Plugin

A Maven plugin to enforce consistent code formatting across Java, JSON, SQL, Markdown, and POM files. This plugin
provides two primary commands:

- **`formatting-apply`**: Automatically applies the required formatting.
- **`formatting-check`**: Validates that the project's files adhere to the specified format.

## Getting Started

### Prerequisites

- **Java 21**
- **Maven 3.9.9 or higher**
- **Spring Boot 3.x**

### Installation

Add this plugin to your project's `pom.xml` in the `<plugins>` section:

```xml

<plugin>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-formatting-plugin</artifactId>
	<version>6.0.6-SNAPSHOT</version>
</plugin>
```

## Usage

1. **To check formatting** (useful for CI pipelines):

   ```bash
   mvn dept44-formatting:check
   ```

   This command will validate the format of Java, JSON, SQL, Markdown, and POM files against the defined rules. If any
   discrepancies are found, the build will fail.

2. **To apply formatting**:

   ```bash
   mvn dept44-formatting:apply
   ```

   This command will automatically format your files according to the specified configuration.

### Configuration Details

The plugin uses [Spotless](https://github.com/diffplug/spotless) under the hood to handle file formatting. Key
configurations include:

- **Java Formatting**: Applies Eclipse formatting based on `src/main/resources/sundsvall_formatting.xml`
- **Indentation**: Tabs with 4 spaces per tab
- **File Exclusions**: Excludes files in `target` and other specified directories
- **File Type Handling**: Configures formatting for `.java`, `.json`, `.sql`, `.md`, and `pom.xml` files

#### File Inclusions and Exclusions

It is possible to include or exclude specific files or directories from the formatting process. This can be done by
adding the following configuration to the plugin in the `pom.xml` file:

```xml

<plugin>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-formatting-plugin</artifactId>
	<version>6.0.3-SNAPSHOT</version>
	<configuration>
		<javaIncludes>
			<include>**/api/**</include>
		</javaIncludes>
		<javaExcludes>
			<exclude>**/api/**</exclude>
		</javaExcludes>
		<jsonIncludes>
			<include>**/*.json</include>
		</jsonIncludes>
		<jsonExcludes>
			<exclude>**/*.json</exclude>
		</jsonExcludes>
		<sqlIncludes>
			<include>**/*.sql</include>
		</sqlIncludes>
		<sqlExcludes>
			<exclude>**/*.sql</exclude>
		</sqlExcludes>
		<markdownIncludes>
			<include>**/*.md</include>
		</markdownIncludes>
		<markdownExcludes>
			<exclude>**/*.md</exclude>
		</markdownExcludes>
		<pomIncludes>
			<include>**/*.xml</include>
		</pomIncludes>
		<pomExcludes>
			<exclude>**/*.xml</exclude>
		</pomExcludes>
	</configuration>
</plugin>
```

> Note: The spotless configuration in pom.xml is exclusively for the plugin’s internal use and not applied to projects
> using this plugin. For your project’s formatting, refer to src/main/resources/configuration.xml.

## Contributing

Contributions are welcome! See
the [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This plugin is distributed under
the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).

© 2024 Sundsvalls kommun
