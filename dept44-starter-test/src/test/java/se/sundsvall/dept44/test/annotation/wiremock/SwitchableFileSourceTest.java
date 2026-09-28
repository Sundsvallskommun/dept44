package se.sundsvall.dept44.test.annotation.wiremock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchableFileSourceTest {

	@TempDir
	private Path tempDir;

	private Path first;
	private Path second;

	@BeforeEach
	void setUp() throws IOException {
		first = Files.createDirectories(tempDir.resolve("first/__files"));
		second = Files.createDirectories(tempDir.resolve("second/__files"));
		Files.writeString(first.resolve("body.json"), "first");
		Files.writeString(second.resolve("body.json"), "second");
	}

	@AfterEach
	void tearDown() {
		SwitchableFileSource.useNoFiles();
	}

	@Test
	void readsNothingUntilSwitched() {
		final var source = new SwitchableFileSource();

		assertThat(source.exists()).isFalse();
		assertThat(source.child("mappings").exists()).isFalse();
	}

	@Test
	void useDirectorySwitchesSource() {
		final var source = new SwitchableFileSource();

		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());

		assertThat(source.getPath()).isEqualTo(tempDir.resolve("second").toString());
		assertThat(source.exists()).isTrue();
	}

	@Test
	void useNoFilesSwitchesBack() {
		final var source = new SwitchableFileSource();
		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());

		SwitchableFileSource.useNoFiles();

		assertThat(source.exists()).isFalse();
	}

	@Test
	void childTakenBeforeSwitchFollowsSwitch() {
		final var files = new SwitchableFileSource().child("__files");

		assertThat(files.exists()).isFalse();

		SwitchableFileSource.useDirectory(tempDir.resolve("first").toString());
		assertThat(files.exists()).isTrue();
		assertThat(files.getTextFileNamed("body.json").readContentsAsString()).isEqualTo("first");
		assertThat(Path.of(files.getPath())).isEqualTo(first);

		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());
		assertThat(files.getTextFileNamed("body.json").readContentsAsString()).isEqualTo("second");
		assertThat(files.listFilesRecursively()).hasSize(1);
	}

	@Test
	void grandchildFollowsSwitch() throws IOException {
		Files.createDirectories(second.resolve("nested"));
		Files.writeString(second.resolve("nested/body.json"), "nested");
		final var nested = new SwitchableFileSource().child("__files").child("nested");

		SwitchableFileSource.useDirectory(tempDir.resolve("first").toString());
		assertThat(nested.exists()).isFalse();

		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());
		assertThat(nested.getTextFileNamed("body.json").readContentsAsString()).isEqualTo("nested");
	}

	@Test
	void fileNameMayUseDotDotWithinDirectory() throws IOException {
		Files.createDirectories(second.resolve("test"));

		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());

		assertThat(new SwitchableFileSource().child("__files").getTextFileNamed("test/../body.json").readContentsAsString()).isEqualTo("second");
	}

	@Test
	void writesGoToCurrentDirectory() {
		final var files = new SwitchableFileSource().child("__files");

		SwitchableFileSource.useDirectory(tempDir.resolve("second").toString());
		files.writeTextFile("written.txt", "text");
		files.writeBinaryFile("written.bin", new byte[] {
			1, 2
		});

		assertThat(second.resolve("written.txt")).hasContent("text");
		assertThat(files.getBinaryFileNamed("written.bin").readContents()).containsExactly(1, 2);

		files.deleteFile("written.txt");
		assertThat(second.resolve("written.txt")).doesNotExist();
		assertThat(first.resolve("written.txt")).doesNotExist();
	}

	@Test
	void createIfNecessaryCreatesCurrentDirectory() {
		final var source = new SwitchableFileSource();
		SwitchableFileSource.useDirectory(tempDir.resolve("created").toString());

		source.child("__files").createIfNecessary();

		assertThat(tempDir.resolve("created/__files")).isDirectory();
		assertThat(source.getUri()).isEqualTo(tempDir.resolve("created").toUri());
	}

	@Test
	void useClasspathDirectoryOnFileSystemReadsItAsDirectory() {
		final var source = new SwitchableFileSource();

		SwitchableFileSource.useClasspathDirectory("/__files/", getClass().getClassLoader());

		assertThat(Path.of(source.getPath())).endsWith(Path.of("test-classes", "__files"));
		assertThat(source.getTextFileNamed("testBinaryCall/../common/response.json").readContentsAsString()).isNotEmpty();
	}

	@Test
	void useClasspathDirectoryInJarReadsThroughClasspath() {
		final var source = new SwitchableFileSource();

		SwitchableFileSource.useClasspathDirectory("org/junit/jupiter/api/", getClass().getClassLoader());

		assertThat(source.getUri().getScheme()).isEqualTo("jar");
		assertThat(source.getBinaryFileNamed("Test.class").readContents()).isNotEmpty();
	}

	@Test
	void useClasspathDirectoryThatDoesNotExistReadsThroughClasspath() {
		final var source = new SwitchableFileSource();

		SwitchableFileSource.useClasspathDirectory("DoesNotExistIT/", getClass().getClassLoader());

		assertThat(source.getPath()).isEqualTo("DoesNotExistIT");
		assertThat(source.child("mappings").exists()).isFalse();
	}
}
