package se.sundsvall.dept44;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootConfiguration
@ConfigurationPropertiesScan
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
	@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
	@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public @interface ServiceApplication {

	@AliasFor(annotation = EnableAutoConfiguration.class, attribute = "exclude")
	Class<?>[] exclude() default {};
}
