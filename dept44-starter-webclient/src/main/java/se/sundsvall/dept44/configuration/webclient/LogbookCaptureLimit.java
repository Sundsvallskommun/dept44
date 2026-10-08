package se.sundsvall.dept44.configuration.webclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_DISPOSITION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Keeps Logbook's Netty client handler from holding more of a response body in memory than the
 * {@link BodyCapturePolicy} allows.
 * <p>
 * Logbook's handler keeps every chunk of a response body it sees and passes that same chunk on to the application, so
 * what it holds cannot be limited by filtering what reaches it. These two handlers go on either side of it instead. The
 * {@link Limiter} keeps its own copy of the body, up to the limit, and shows Logbook empty chunks; with the last chunk
 * Logbook gets the whole copy, or a note that the body was omitted when it turned out to be larger. The
 * {@link Restorer}
 * then passes the original chunks on, so the application receives the response unchanged.
 * <p>
 * A response that arrives as a single full message is passed through as it is.
 */
final class LogbookCaptureLimit {

	private LogbookCaptureLimit() {}

	/**
	 * Goes before Logbook's handler.
	 */
	static final class Limiter extends ChannelInboundHandlerAdapter {

		private final BodyCapturePolicy policy;
		private ByteArrayOutputStream capture;
		private boolean overflowed;
		private String contentType;

		Limiter(final BodyCapturePolicy policy) {
			this.policy = policy;
		}

		@Override
		public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws IOException {
			if (msg instanceof final HttpResponse response && !(msg instanceof HttpContent)) {
				start(response);
				ctx.fireChannelRead(msg);
				return;
			}
			if (capture != null && msg instanceof final HttpContent content && !(msg instanceof HttpResponse)) {
				copy(content.content());
				ctx.fireChannelRead(content instanceof LastHttpContent ? new HiddenLastContent(content, finish()) : new HiddenContent(content));
				return;
			}
			ctx.fireChannelRead(msg);
		}

		/**
		 * A body that may not be captured at all (binary, an attachment, or known to be too large) is not copied either;
		 * Logbook does not capture it.
		 */
		private void start(final HttpResponse response) {
			final var headers = response.headers();
			contentType = headers.get(CONTENT_TYPE);
			final var contentLength = BodyCapturePolicy.parseLength(headers.get(CONTENT_LENGTH));
			capture = policy.allowsCapture(contentType, headers.getAll(CONTENT_DISPOSITION), contentLength) ? new ByteArrayOutputStream() : null;
			overflowed = false;
		}

		private void copy(final ByteBuf content) throws IOException {
			if (overflowed) {
				return;
			}
			final var length = content.readableBytes();
			if (policy.exceedsLimit((long) capture.size() + length)) {
				overflowed = true;
				capture = new ByteArrayOutputStream(0);
				return;
			}
			content.getBytes(content.readerIndex(), capture, length);
		}

		private ByteBuf finish() {
			final var body = overflowed ? policy.omittedNote(contentType).getBytes(UTF_8) : capture.toByteArray();
			capture = null;
			return Unpooled.wrappedBuffer(body);
		}
	}

	/**
	 * Goes after Logbook's handler.
	 */
	static final class Restorer extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
			if (msg instanceof final HiddenContent hidden) {
				hidden.release();
				ctx.fireChannelRead(hidden.original);
				return;
			}
			if (msg instanceof final HiddenLastContent hidden) {
				hidden.release();
				ctx.fireChannelRead(hidden.original);
				return;
			}
			ctx.fireChannelRead(msg);
		}
	}

	/**
	 * What Logbook sees of a chunk: nothing.
	 */
	static final class HiddenContent extends DefaultHttpContent {

		private final HttpContent original;

		HiddenContent(final HttpContent original) {
			super(Unpooled.EMPTY_BUFFER);
			this.original = original;
		}

		// Netty compares chunks by their decoder result only, so the chunk hidden is compared by identity
		@Override
		public boolean equals(final Object other) {
			return other instanceof final HiddenContent hidden && super.equals(hidden) && original == hidden.original;
		}

		@Override
		public int hashCode() {
			return 31 * super.hashCode() + System.identityHashCode(original);
		}
	}

	/**
	 * What Logbook sees of the last chunk: the captured body, or the note that it was omitted.
	 */
	static final class HiddenLastContent extends DefaultLastHttpContent {

		private final HttpContent original;

		HiddenLastContent(final HttpContent original, final ByteBuf shown) {
			super(shown);
			this.original = original;
		}

		// Netty compares chunks by their decoder result only, so the chunk hidden is compared by identity
		@Override
		public boolean equals(final Object other) {
			return other instanceof final HiddenLastContent hidden && super.equals(hidden) && original == hidden.original;
		}

		@Override
		public int hashCode() {
			return 31 * super.hashCode() + System.identityHashCode(original);
		}
	}
}
