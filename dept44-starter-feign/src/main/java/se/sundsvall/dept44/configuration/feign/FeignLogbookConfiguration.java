package se.sundsvall.dept44.configuration.feign;

import feign.Logger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import se.sundsvall.dept44.configuration.LogbookConfiguration;
import se.sundsvall.dept44.configuration.feign.logbook.BodyCaptureFeignLogger;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

/**
 * Registers the Feign logger used by every Feign client, in place of Logbook's own, which backs off when a
 * {@link Logger} bean exists. Without a {@link BodyCapturePolicy}, such as when a service sets up Logbook itself
 * instead of using {@link LogbookConfiguration}, Logbook's own Feign logger is used.
 */
@AutoConfiguration(after = LogbookConfiguration.class, before = LogbookAutoConfiguration.class)
@ConditionalOnClass({
	Logger.class, Logbook.class
})
public class FeignLogbookConfiguration {

	@Bean
	@ConditionalOnBean(BodyCapturePolicy.class)
	@ConditionalOnMissingBean(Logger.class)
	Logger bodyCaptureFeignLogger(final Logbook logbook, final BodyCapturePolicy bodyCapturePolicy) {
		return new BodyCaptureFeignLogger(logbook, bodyCapturePolicy);
	}
}
