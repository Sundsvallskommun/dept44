package se.sundsvall.petinventory.integration.petstore.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.petstore")
public record PetStoreProperties(int connectTimeout, int readTimeout) {
}
