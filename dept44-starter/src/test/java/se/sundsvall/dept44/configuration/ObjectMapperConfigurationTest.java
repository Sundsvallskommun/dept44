package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ObjectMapperConfiguration.class)
class ObjectMapperConfigurationTest {

    @Autowired
    private YAMLMapper yamlMapper;
    @Autowired
    private JaxbAnnotationModule jaxbAnnotationModule;
    @Autowired
    private JavaTimeModule javaTimeModule;

    @Test
    void test_yamlMapper_isAutowired() {
        assertThat(yamlMapper).isNotNull();
    }

    @Test
    void test_jaxbAnnotationModule_isAutowired() {
        assertThat(jaxbAnnotationModule).isNotNull();
    }

    @Test
    void test_javaTimeModule_isAutowired() {
        assertThat(javaTimeModule).isNotNull();
    }
}
