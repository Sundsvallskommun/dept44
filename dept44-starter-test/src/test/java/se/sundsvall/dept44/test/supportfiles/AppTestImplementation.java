package se.sundsvall.dept44.test.supportfiles;

import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

@WireMockAppTestSuite(files = "classpath:/", classes = Class.class)
@ActiveProfiles("junit")
public class AppTestImplementation extends AbstractAppTest {
}
