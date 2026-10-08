package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.ResponseProcessingStage;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * Logs responses for Logbook's {@code LogbookHttpRequestInterceptor}, in place of Logbook's
 * {@code LogbookHttpResponseInterceptor}, without holding more of a body in memory than the {@link BodyCapturePolicy}
 * allows.
 * <p>
 * Logbook's interceptor reads the whole response body into memory when it logs it, also a body of unknown length. This
 * one reads at most the allowed size plus one byte. A body that fits is logged and handed on in memory; a larger one is
 * logged as omitted and handed on as a stream, starting with the bytes already read.
 */
public final class BoundedLogbookHttpResponseInterceptor implements HttpResponseInterceptor {

	/**
	 * Where {@code LogbookHttpRequestInterceptor} keeps the request's logging stage (Logbook's
	 * {@code org.zalando.logbook.httpclient5.Attributes.STAGE}, which is not public).
	 */
	static final String STAGE = Logbook.class.getName() + ".STAGE";

	private static final Logger LOG = LoggerFactory.getLogger(BoundedLogbookHttpResponseInterceptor.class);

	private final BodyCapturePolicy policy;

	public BoundedLogbookHttpResponseInterceptor(final BodyCapturePolicy policy) {
		this.policy = policy;
	}

	@Override
	public void process(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context) {
		try {
			if (context.getAttribute(STAGE) instanceof final ResponseProcessingStage stage) {
				stage.process(new LoggedResponse(response, policy)).write();
			}
		} catch (final IOException | RuntimeException e) {
			LOG.warn("Unable to log response. Will skip the response logging step.", e);
		}
	}

	/**
	 * Logbook view of the response, reading the body only when Logbook asks for it.
	 */
	static final class LoggedResponse implements org.zalando.logbook.HttpResponse {

		private static final byte[] EMPTY = new byte[0];

		private final HttpResponse response;
		private final BodyCapturePolicy policy;
		private boolean withBody;
		private byte[] body;

		LoggedResponse(final HttpResponse response, final BodyCapturePolicy policy) {
			this.response = response;
			this.policy = policy;
		}

		@Override
		public int getStatus() {
			return response.getCode();
		}

		@Override
		public String getProtocolVersion() {
			return Optional.ofNullable(response.getVersion()).map(ProtocolVersion::toString).orElse("HTTP/1.1");
		}

		@Override
		public Origin getOrigin() {
			return Origin.REMOTE;
		}

		@Override
		public HttpHeaders getHeaders() {
			final Map<String, List<String>> headers = new LinkedHashMap<>();
			for (final Header header : response.getHeaders()) {
				headers.computeIfAbsent(header.getName(), _ -> new ArrayList<>()).add(header.getValue());
			}
			return HttpHeaders.of(headers);
		}

		@Override
		public String getContentType() {
			return Optional.ofNullable(response.getFirstHeader(CONTENT_TYPE)).map(Header::getValue).orElse(null);
		}

		@Override
		public Charset getCharset() {
			return Optional.ofNullable(getContentType())
				.map(ContentType::parseLenient)
				.map(ContentType::getCharset)
				.orElse(UTF_8);
		}

		@Override
		public org.zalando.logbook.HttpResponse withBody() {
			withBody = true;
			return this;
		}

		@Override
		public org.zalando.logbook.HttpResponse withoutBody() {
			withBody = false;
			return this;
		}

		@Override
		public byte[] getBody() throws IOException {
			if (!withBody) {
				return EMPTY;
			}
			if (body == null) {
				body = capture();
			}
			return body;
		}

		private byte[] capture() throws IOException {
			if (!(response instanceof final ClassicHttpResponse classicResponse) || classicResponse.getEntity() == null) {
				return EMPTY;
			}

			final var entity = classicResponse.getEntity();
			final var input = entity.getContent();
			final var head = policy.isLimited() ? input.readNBytes(policy.readLimit()) : input.readAllBytes();
			if (!policy.exceedsLimit(head.length)) {
				input.close();
				classicResponse.setEntity(new ByteArrayEntity(head, contentType(entity), entity.getContentEncoding(), entity.isChunked()));
				return head;
			}

			classicResponse.setEntity(new InputStreamEntity(new SequenceInputStream(new ByteArrayInputStream(head), input), entity.getContentLength(), contentType(entity),
				entity.getContentEncoding()));
			return policy.omittedNote(getContentType()).getBytes(getCharset());
		}

		private static ContentType contentType(final HttpEntity entity) {
			return Optional.ofNullable(entity.getContentType()).map(ContentType::parseLenient).orElse(null);
		}
	}
}
