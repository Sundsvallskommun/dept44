package se.sundsvall.petinventory.apptest;

import static se.sundsvall.petinventory.apptest.SharedContextAssertions.assertClassStubAnswers;
import static se.sundsvall.petinventory.apptest.SharedContextAssertions.assertSameContext;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.petinventory.Application;

/**
 * SharedContextSecondIT tests, sharing their application context with {@link SharedContextFirstIT}.
 */
@WireMockAppTestSuite(files = "classpath:/SharedContextSecondIT/", classes = Application.class)
class SharedContextSecondIT extends AbstractAppTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void test01_classStubAnswersInFirstTest() throws IOException, InterruptedException {
		assertClassStubAnswers(wiremock, "second");
		assertSameContext(applicationContext);
	}

	@Test
	void test02_classStubAnswersInLaterTest() throws IOException, InterruptedException {
		assertClassStubAnswers(wiremock, "second");
		assertSameContext(applicationContext);
	}
}
