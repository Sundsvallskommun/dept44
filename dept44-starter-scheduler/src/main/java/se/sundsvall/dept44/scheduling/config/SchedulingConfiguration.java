package se.sundsvall.dept44.scheduling.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for scheduling and locking tasks.
 * <p>
 * Uses {@link JdbcTemplateLockProvider} for locking tasks.
 * <p>
 * Locks tasks for at most 2 minutes.
 * <p>
 * Uses {@link EnableSchedulerLock} for locking tasks.
 *
 * @see JdbcTemplateLockProvider
 * @see EnableSchedulerLock
 */
@AutoConfiguration
@ComponentScan(basePackages = "se.sundsvall.dept44.scheduling")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
@EnableAspectJAutoProxy
class SchedulingConfiguration {

	/**
	 * Lock provider using {@link JdbcTemplateLockProvider}.
	 *
	 * @param  dataSource the data source
	 * @return            the lock provider
	 */
	@Bean
	LockProvider lockProvider(final DataSource dataSource) {
		return new JdbcTemplateLockProvider(
			JdbcTemplateLockProvider.Configuration.builder()
				.usingDbTime()
				.withJdbcTemplate(new JdbcTemplate(dataSource))
				.build());
	}
}
