package se.sundsvall.petinventory.integration.petstore.configuration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import se.sundsvall.petinventory.Application;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class PetStorePropertiesTest {

	@Autowired
	private PetStoreProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.connectTimeout()).isEqualTo(10);
		assertThat(properties.readTimeout()).isEqualTo(20);
	}
}
