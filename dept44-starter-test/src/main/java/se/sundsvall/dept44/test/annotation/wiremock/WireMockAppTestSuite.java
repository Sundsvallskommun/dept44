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
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * Autoconfigure WireMock on a dynamic/random port, loading mappings from the classpath using the provided value(s).
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConfigureWireMock
@EnableWireMock
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("it")
@TestMethodOrder(MethodOrderer.MethodName.class)
public @interface WireMockAppTestSuite {

	@AliasFor(annotation = ConfigureWireMock.class, attribute = "filesUnderClasspath")
	String files();

	@AliasFor(annotation = SpringBootTest.class, attribute = "classes")
	Class<?>[] classes();
}
