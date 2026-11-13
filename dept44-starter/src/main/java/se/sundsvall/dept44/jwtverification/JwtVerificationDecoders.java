package se.sundsvall.dept44.jwtverification;

import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.OutageTolerantJWKSetSource;
import com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource;
import com.nimbusds.jose.jwk.source.RefreshAheadCachingJWKSetSource;
import com.nimbusds.jose.jwk.source.RetryingJWKSetSource;
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import se.sundsvall.dept44.jwtverification.JwtVerificationProperties.Verifier;

import static org.springframework.util.StringUtils.hasText;

/**
 * Builds the decoder of one verifier.
 * <p>
 * The key source is assembled from Nimbus's own layers, because {@code JWKSourceBuilder} takes only one URL and
 * {@code NimbusJwtDecoder.withJwkSetUri} turns the rate limit off:
 *
 * <pre>
 * per URL: URLBasedJWKSetSource → RetryingJWKSetSource → OutageTolerantJWKSetSource
 * merged:  MergedJwkSetSource (URLs in parallel) → RateLimitedJWKSetSource → RefreshAheadCachingJWKSetSource
 * </pre>
 *
 * <ul>
 * <li>The keys are fetched on first use, so the service does not depend on the issuer to start.</li>
 * <li>They are cached for five minutes (longer with long timeouts, see {@link Timing}) and refreshed ahead of expiry
 * on a thread of their own, while requests go on with the cached keys. A failed refresh leaves them in use.</li>
 * <li>A JWT with an unknown key id makes the keys be fetched at once, which picks up a rotated key, but at most twice
 * per six seconds, so that made-up key ids do not each cost a call to the issuer.</li>
 * <li>A failed fetch is retried once, and if an endpoint is down, the keys last fetched from it are used for ten times
 * the cache time.</li>
 * <li>The JWT must have an expiry, and must not be expired. If configured, it must have the issuer and one of the
 * audiences. Key URLs must use https.</li>
 * <li>The timeouts are for the worst case. Requests waiting for a fetch wait as long as it can take, so they get its
 * result instead of giving up before it.</li>
 * </ul>
 */
final class JwtVerificationDecoders {

	/**
	 * Nimbus allows two key fetches per interval, so this gives at most ten per 30 seconds.
	 */
	static final Duration KEY_FETCH_INTERVAL = Duration.ofSeconds(6);

	static final Duration MIN_CACHE_TIME_TO_LIVE = Duration.ofMillis(JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE);

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

	private JwtVerificationDecoders() {}

	/**
	 * A verifier's decoder and its key source, which has to be closed to stop the refresh thread.
	 */
	record Decoder(JwtDecoder jwtDecoder, Closeable keySource) {
	}

	/**
	 * How long things may take, derived from the timeouts.
	 *
	 * @param fetch  the longest a fetch can take: two attempts (one retry) of connect and read timeouts, plus a second.
	 *               The URLs are fetched in parallel, so their number does not add up. The keys are also refreshed this
	 *               long before expiry, so that the slowest fetch finishes in time
	 * @param cache  how long the keys are cached, at least five minutes, and long enough to hold a refresh ahead of
	 *               expiry and a fetch after it
	 * @param outage how long the keys last fetched from an endpoint that is down are used
	 */
	record Timing(Duration fetch, Duration cache, Duration outage) {

		static Timing of(final Verifier verifier) {
			final var fetch = verifier.connectTimeout().plus(verifier.readTimeout()).multipliedBy(2).plusSeconds(1);
			final var cache = max(MIN_CACHE_TIME_TO_LIVE, fetch.multipliedBy(3));
			return new Timing(fetch, cache, cache.multipliedBy(10));
		}

		private static Duration max(final Duration a, final Duration b) {
			return a.compareTo(b) >= 0 ? a : b;
		}
	}

	/**
	 * @param  name     the verifier's name, for messages and the refresh thread
	 * @param  verifier the verifier's settings
	 * @return          a decoder that verifies the signature against the verifier's keys, and the expiry
	 */
	static Decoder create(final String name, final Verifier verifier) {
		final var timing = Timing.of(verifier);
		final var retriever = new DefaultResourceRetriever(
			Math.toIntExact(verifier.connectTimeout().toMillis()),
			Math.toIntExact(verifier.readTimeout().toMillis()),
			JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);

		final var perUrl = verifier.jwkSetUris().stream()
			.map(uri -> urlSource(name, uri, retriever, timing))
			.toList();

		final JWKSetSource<SecurityContext> merged = new MergedJwkSetSource(name, perUrl);
		final JWKSetSource<SecurityContext> rateLimited = new RateLimitedJWKSetSource<>(merged, KEY_FETCH_INTERVAL.toMillis(), null);
		final JWKSetSource<SecurityContext> cached = new RefreshAheadCachingJWKSetSource<>(rateLimited,
			timing.cache().toMillis(),
			timing.fetch().toMillis(), // How long a request waits for a fetch another request started
			timing.fetch().toMillis(), // Refreshed ahead of expiry by as long as a fetch can take
			false, // Refreshed when a request finds the expiry near, not on a schedule
			Executors.newSingleThreadExecutor(Thread.ofVirtual().name("jwt-verification-" + name).factory()),
			true, // Shut the executor down when the source is closed
			null);

		final var keySource = new JWKSetBasedJWKSource<>(cached);
		final var jwtDecoder = NimbusJwtDecoder.withJwkSource(keySource)
			.jwsAlgorithms(algorithms -> algorithms.addAll(verifier.algorithms()))
			.build();
		// Spring accepts a JWT without exp by default, which would then be valid forever
		final var timestamps = new JwtTimestampValidator();
		timestamps.setAllowEmptyExpiryClaim(false);
		final var validators = new ArrayList<OAuth2TokenValidator<Jwt>>(List.of(timestamps));
		if (hasText(verifier.issuer())) {
			validators.add(new JwtIssuerValidator(verifier.issuer()));
		}
		if (!verifier.audiences().isEmpty()) {
			validators.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(verifier.audiences()::contains)));
		}
		jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
		return new Decoder(jwtDecoder, keySource);
	}

	private static JWKSetSource<SecurityContext> urlSource(final String name, final String uri, final ResourceRetriever retriever, final Timing timing) {
		final JWKSetSource<SecurityContext> source = new URLBasedJWKSetSource<>(toUrl(name, uri), retriever);
		final JWKSetSource<SecurityContext> retrying = new RetryingJWKSetSource<>(source, null);
		return new OutageTolerantJWKSetSource<>(retrying, timing.outage().toMillis(), null);
	}

	/**
	 * Keys fetched over plain http could be swapped by anyone on the way, who could then sign JWTs of their own. Only a
	 * local endpoint, as in tests, may use http.
	 */
	private static URL toUrl(final String name, final String uri) {
		final URL url;
		try {
			url = URI.create(uri).toURL();
		} catch (final IllegalArgumentException | MalformedURLException e) {
			throw new IllegalArgumentException("Invalid %s.verifiers.%s.jwk-set-uris: %s".formatted(JwtVerificationProperties.PREFIX, name, uri), e);
		}
		if (!"https".equals(url.getProtocol()) && !("http".equals(url.getProtocol()) && LOCAL_HOSTS.contains(url.getHost()))) {
			throw new IllegalArgumentException("%s.verifiers.%s.jwk-set-uris must use https: %s".formatted(JwtVerificationProperties.PREFIX, name, uri));
		}
		return url;
	}
}
