package se.sundsvall.dept44.jwtverification;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MergedJwkSetSourceTest {

	private static final JWKSetCacheRefreshEvaluator EVALUATOR = JWKSetCacheRefreshEvaluator.forceRefresh();

	@Mock
	private JWKSetSource<SecurityContext> firstMock;

	@Mock
	private JWKSetSource<SecurityContext> secondMock;

	@Test
	void keysOfAllSourcesAreMergedWithoutDuplicates() throws Exception {
		final var shared = rsaKey("shared");
		final var own = rsaKey("own");
		when(firstMock.getJWKSet(any(), anyLong(), any())).thenReturn(new JWKSet(shared));
		when(secondMock.getJWKSet(any(), anyLong(), any())).thenReturn(new JWKSet(List.of(shared, own)));

		final var keys = new MergedJwkSetSource("gateway", List.of(firstMock, secondMock)).getJWKSet(EVALUATOR, 0, null).getKeys();

		assertThat(keys).containsExactly(shared, own);
	}

	@Test
	void keysOfTheOtherSourcesAreReturnedWhenOneFails() throws Exception {
		final var key = rsaKey("key");
		when(firstMock.getJWKSet(any(), anyLong(), any())).thenThrow(new KeySourceException("down"));
		when(secondMock.getJWKSet(any(), anyLong(), any())).thenReturn(new JWKSet(key));

		final var keys = new MergedJwkSetSource("gateway", List.of(firstMock, secondMock)).getJWKSet(EVALUATOR, 0, null).getKeys();

		assertThat(keys).containsExactly(key);
	}

	@Test
	void failsWhenAllSourcesFail() throws Exception {
		final var first = new KeySourceException("first down");
		final var second = new KeySourceException("second down");
		when(firstMock.getJWKSet(any(), anyLong(), any())).thenThrow(first);
		when(secondMock.getJWKSet(any(), anyLong(), any())).thenThrow(second);
		final var source = new MergedJwkSetSource("gateway", List.of(firstMock, secondMock));

		assertThatExceptionOfType(KeySourceException.class)
			.isThrownBy(() -> source.getJWKSet(EVALUATOR, 0, null))
			.isSameAs(first)
			.satisfies(e -> assertThat(e.getSuppressed()).containsExactly(second));
	}

	@Test
	void atLeastOneSourceIsNeeded() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MergedJwkSetSource("gateway", List.of()));
	}

	@Test
	void closeClosesAllSources() throws Exception {
		new MergedJwkSetSource("gateway", List.of(firstMock, secondMock)).close();

		verify(firstMock).close();
		verify(secondMock).close();
	}

	@Test
	void closeClosesTheRestWhenOneFailsAndReportsAllFailures() throws Exception {
		final var first = new IOException("first");
		final var second = new IOException("second");
		doThrow(first).when(firstMock).close();
		doThrow(second).when(secondMock).close();
		final var source = new MergedJwkSetSource("gateway", List.of(firstMock, secondMock));

		assertThatExceptionOfType(IOException.class)
			.isThrownBy(source::close)
			.isSameAs(first)
			.satisfies(e -> assertThat(e.getSuppressed()).containsExactly(second));
		verify(secondMock).close();
	}

	private static RSAKey rsaKey(final String keyId) throws Exception {
		return new RSAKeyGenerator(2048).keyID(keyId).generate().toPublicJWK();
	}
}
