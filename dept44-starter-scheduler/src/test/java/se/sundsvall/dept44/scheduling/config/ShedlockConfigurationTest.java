package se.sundsvall.dept44.scheduling.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigurationTest {

	private SchedulingConfiguration schedulingConfiguration;

	@BeforeEach
	void setUp() {
		schedulingConfiguration = new SchedulingConfiguration();
	}

	@Test
	void testLockProvider() {
		// arrange
		final var dataSource = mock(DataSource.class);

		// act
		final var lockProvider = schedulingConfiguration.lockProvider(dataSource);

		// assert
		assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
		final var jdbcTemplateLockProvider = (JdbcTemplateLockProvider) lockProvider;
		assertThat(jdbcTemplateLockProvider).isNotNull();
	}
}
