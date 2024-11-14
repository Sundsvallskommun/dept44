package se.sundsvall.petinventory;

import static org.springframework.boot.SpringApplication.run;

import org.springframework.cloud.openfeign.EnableFeignClients;
import se.sundsvall.dept44.ServiceApplication;
import se.sundsvall.dept44.util.jacoco.ExcludeFromJacocoGeneratedCoverageReport;

@EnableFeignClients
@ServiceApplication
@ExcludeFromJacocoGeneratedCoverageReport
public class Application {
	public static void main(final String... args) {
		run(Application.class, args);
	}
}
