package se.sundsvall.dept44.support.testutil;

import static org.springframework.boot.SpringApplication.run;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApplication {
	public static void main(final String... args) {
		run(TestApplication.class, args);
	}
}
