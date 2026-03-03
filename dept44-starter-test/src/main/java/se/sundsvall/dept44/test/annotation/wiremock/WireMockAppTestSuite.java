package se.sundsvall.dept44.test.annotation.wiremock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;

/**
 * Autoconfigure WireMock on a dynamic/random port, loading mappings from the classpath using the provided value(s).
 *
 * <p>
 * The {@code files} attribute maps via {@code @AliasFor} to {@link ConfigureWireMock#filesUnderClasspath()}. A custom
 * {@link WireMockAppTestSuiteContextCustomizerFactory} uses Spring's annotation merging to resolve this alias and
 * create the WireMock
 * server with the correct file source before wiremock-spring-boot's built-in factory runs.
 *
 * <p>
 * {@code @EnableWireMock} is intentionally omitted — since wiremock-spring-boot 4.2.0, {@code @ConfigureWireMock}
 * itself carries {@code @ExtendWith(WireMockSpringJunitExtension.class)}, which is sufficient to register the JUnit 5
 * extension. Including
 * {@code @EnableWireMock} would cause its empty {@code value} to produce a default
 * {@code @ConfigureWireMock(name = "wiremock")} with no {@code filesUnderClasspath}, which shadows the correctly
 * configured one via {@code addIfAbsent}.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConfigureWireMock
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@AutoConfigureWebTestClient
@ActiveProfiles("it")
@TestMethodOrder(MethodOrderer.MethodName.class)
public @interface WireMockAppTestSuite {

	@AliasFor(annotation = ConfigureWireMock.class, attribute = "filesUnderClasspath")
	String files();

	@AliasFor(annotation = SpringBootTest.class, attribute = "classes")
	Class<?>[] classes();
}
