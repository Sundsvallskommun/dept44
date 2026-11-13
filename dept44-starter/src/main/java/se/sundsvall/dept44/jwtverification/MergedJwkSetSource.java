package se.sundsvall.dept44.jwtverification;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The keys of several JWKS endpoints as one key set, so that a verifier with several endpoints has one cache and one
 * rate limit: a rotation on any of them costs one fetch.
 * <p>
 * The endpoints are fetched in parallel. If some fail, the keys of the others are returned, so that one issuer's outage
 * does not reject JWTs signed
 * by another. It fails only if all endpoints fail.
 */
final class MergedJwkSetSource implements JWKSetSource<SecurityContext> {

	private static final Logger LOGGER = LoggerFactory.getLogger(MergedJwkSetSource.class);

	private final String verifierName;
	private final List<JWKSetSource<SecurityContext>> sources;

	MergedJwkSetSource(final String verifierName, final List<JWKSetSource<SecurityContext>> sources) {
		if (sources.isEmpty()) {
			throw new IllegalArgumentException("At least one JWK set source is needed");
		}
		this.verifierName = verifierName;
		this.sources = List.copyOf(sources);
	}

	@Override
	public JWKSet getJWKSet(final JWKSetCacheRefreshEvaluator refreshEvaluator, final long currentTime, final SecurityContext context) throws KeySourceException {
		// Equal keys are dropped, e.g. when two URLs publish the same key
		final var keys = new LinkedHashSet<JWK>();
		final var failures = new ArrayList<KeySourceException>();
		for (final var fetched : fetchAll(refreshEvaluator, currentTime, context)) {
			if (fetched.failure() != null) {
				failures.add(fetched.failure());
			} else {
				keys.addAll(fetched.keys().getKeys());
			}
		}

		if (failures.size() == sources.size()) {
			final var failure = failures.getFirst();
			failures.stream().skip(1).forEach(failure::addSuppressed);
			throw failure;
		}
		failures.forEach(e -> LOGGER.warn("JWT verifier '{}' could not fetch keys, using the keys of its other endpoints: {}", verifierName, e.getMessage()));

		return new JWKSet(List.copyOf(keys));
	}

	/**
	 * Fetches from all sources in parallel, so that a slow endpoint does not add its time to the others'.
	 *
	 * @return per source, in order, its keys or what it failed with
	 */
	private List<Fetched> fetchAll(final JWKSetCacheRefreshEvaluator refreshEvaluator, final long currentTime, final SecurityContext context) throws KeySourceException {
		if (sources.size() == 1) {
			return List.of(fetch(sources.getFirst(), refreshEvaluator, currentTime, context));
		}

		try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			final var futures = sources.stream()
				.map(source -> executor.submit(() -> fetch(source, refreshEvaluator, currentTime, context)))
				.toList();
			final var results = new ArrayList<Fetched>();
			for (final var future : futures) {
				results.add(future.get());
			}
			return results;
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new KeySourceException("Interrupted while fetching the keys", e);
		} catch (final ExecutionException e) {
			// fetch() catches what the sources throw, so this is unexpected
			throw new KeySourceException("Could not fetch the keys: " + e.getCause().getMessage(), e.getCause());
		}
	}

	private static Fetched fetch(final JWKSetSource<SecurityContext> source, final JWKSetCacheRefreshEvaluator refreshEvaluator, final long currentTime, final SecurityContext context) {
		try {
			return new Fetched(source.getJWKSet(refreshEvaluator, currentTime, context), null);
		} catch (final KeySourceException e) {
			return new Fetched(null, e);
		}
	}

	/**
	 * One source's keys, or what it failed with.
	 */
	private record Fetched(JWKSet keys, KeySourceException failure) {
	}

	@Override
	public void close() throws IOException {
		IOException failure = null;
		for (final var source : sources) {
			try {
				source.close();
			} catch (final IOException e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}
}
