package se.sundsvall.dept44.test.annotation.wiremock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

/**
 * Auto-configure WireMock on a dynamic/random port, loading mappings from the classpath using the provided value(s).
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestMethodOrder(MethodOrderer.MethodName.class)
public @interface WireMockAppTestSuite {

	@AliasFor(annotation = AutoConfigureWireMock.class, attribute = "files")
	String[] files();

	@AliasFor(annotation = SpringBootTest.class, attribute = "classes")
	Class<?>[] classes();
}
