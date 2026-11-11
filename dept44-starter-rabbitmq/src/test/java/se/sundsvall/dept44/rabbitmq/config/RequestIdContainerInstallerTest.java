package se.sundsvall.dept44.rabbitmq.config;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.util.ErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdContainerInstallerTest {

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
		final var installer = new RequestIdContainerInstaller(registry());

		installer.start();

		assertThat(withDefault.getErrorHandler()).isInstanceOfSatisfying(RequestIdErrorHandler.class,
			handler -> assertThat(handler.getDelegate()).isInstanceOf(ConditionalRejectingErrorHandler.class));
		assertThat(withOwn.getErrorHandler()).isInstanceOfSatisfying(RequestIdErrorHandler.class,
			handler -> assertThat(handler.getDelegate()).isSameAs(own));
		verifyNoInteractions(other);
		assertThat(installer.isRunning()).isTrue();
	}

	@Test
	void addsTheRequestIdHeadersToRepliesAfterThePostProcessorsEachListenerHas() {
		final MessagePostProcessor own = message -> message;
		final var withOwn = containerWith(new MessageListenerAdapter(new Object()));
		((MessageListenerAdapter) withOwn.getMessageListener()).setBeforeSendReplyPostProcessors(own);
		final var withNone = containerWith(new MessageListenerAdapter(new Object()));
		final MessageListener plainListener = _ -> {};
		final var plain = containerWith(plainListener);
		when(mockRegistry.getListenerContainers()).thenReturn(List.of(withOwn, withNone, plain));

		new RequestIdContainerInstaller(registry()).start();

		final var withOwnPostProcessors = replyPostProcessors(withOwn);
		assertThat(withOwnPostProcessors).hasSize(2);
		assertThat(withOwnPostProcessors[0]).isSameAs(own);
		assertThat(withOwnPostProcessors[1]).isInstanceOf(RequestIdHeaders.class);
		assertThat(replyPostProcessors(withNone)).singleElement().isInstanceOf(RequestIdHeaders.class);
		assertThat(plain.getMessageListener()).isSameAs(plainListener);
	}

	@Test
	void installsOnlyOnceOverARestart() {
		final var container = containerWith(new MessageListenerAdapter(new Object()));
		when(mockRegistry.getListenerContainers()).thenReturn(List.of(container));
		final var installer = new RequestIdContainerInstaller(registry());

		installer.start();
		final var wrapped = container.getErrorHandler();
		installer.stop();
		installer.start();

		assertThat(container.getErrorHandler()).isSameAs(wrapped);
		assertThat(replyPostProcessors(container)).singleElement().isInstanceOf(RequestIdHeaders.class);
	}

	@Test
	void stops() {
		final var installer = new RequestIdContainerInstaller(registry());
		installer.start();

		installer.stop();

		assertThat(installer.isRunning()).isFalse();
	}

	@Test
	void runsJustBeforeTheRegistryStartsTheContainers() {
		when(mockRegistry.getPhase()).thenReturn(Integer.MAX_VALUE);

		assertThat(new RequestIdContainerInstaller(registry()).getPhase()).isEqualTo(Integer.MAX_VALUE - 1);
	}

	private static SimpleMessageListenerContainer containerWith(final MessageListener listener) {
		final var container = new SimpleMessageListenerContainer();
		container.setMessageListener(listener);
		return container;
	}

	private static MessagePostProcessor @Nullable [] replyPostProcessors(final SimpleMessageListenerContainer container) {
		return ((MessageListenerAdapter) container.getMessageListener()).getBeforeSendReplyPostProcessors();
	}

	private ObjectProvider<RabbitListenerEndpointRegistry> registry() {
		return new StaticListableBeanFactory(Map.of("registry", mockRegistry)).getBeanProvider(RabbitListenerEndpointRegistry.class);
	}
}
