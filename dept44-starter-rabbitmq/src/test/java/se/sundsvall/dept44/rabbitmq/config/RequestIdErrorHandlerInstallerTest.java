package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.util.ErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdErrorHandlerInstallerTest {

	@Mock
	private RabbitListenerEndpointRegistry mockRegistry;

	@Test
	void wrapsTheErrorHandlerEachContainerHas() {
		final var withDefault = new SimpleMessageListenerContainer();
		final var withOwn = new SimpleMessageListenerContainer();
		final ErrorHandler own = _ -> {};
		withOwn.setErrorHandler(own);
		final var other = mock(MessageListenerContainer.class);
		when(mockRegistry.getListenerContainers()).thenReturn(List.of(withDefault, withOwn, other));
		final var installer = new RequestIdErrorHandlerInstaller(registry());

		installer.start();

		assertThat(withDefault.getErrorHandler()).isInstanceOfSatisfying(RequestIdErrorHandler.class,
			handler -> assertThat(handler.getDelegate()).isInstanceOf(ConditionalRejectingErrorHandler.class));
		assertThat(withOwn.getErrorHandler()).isInstanceOfSatisfying(RequestIdErrorHandler.class,
			handler -> assertThat(handler.getDelegate()).isSameAs(own));
		verifyNoInteractions(other);
		assertThat(installer.isRunning()).isTrue();
	}

	@Test
	void wrapsOnlyOnceOverARestart() {
		final var container = new SimpleMessageListenerContainer();
		when(mockRegistry.getListenerContainers()).thenReturn(List.of(container));
		final var installer = new RequestIdErrorHandlerInstaller(registry());

		installer.start();
		final var wrapped = container.getErrorHandler();
		installer.stop();
		installer.start();

		assertThat(container.getErrorHandler()).isSameAs(wrapped);
	}

	@Test
	void stops() {
		final var installer = new RequestIdErrorHandlerInstaller(registry());
		installer.start();

		installer.stop();

		assertThat(installer.isRunning()).isFalse();
	}

	@Test
	void runsJustBeforeTheRegistryStartsTheContainers() {
		when(mockRegistry.getPhase()).thenReturn(Integer.MAX_VALUE);

		assertThat(new RequestIdErrorHandlerInstaller(registry()).getPhase()).isEqualTo(Integer.MAX_VALUE - 1);
	}

	private ObjectProvider<RabbitListenerEndpointRegistry> registry() {
		return new StaticListableBeanFactory(Map.of("registry", mockRegistry)).getBeanProvider(RabbitListenerEndpointRegistry.class);
	}
}
