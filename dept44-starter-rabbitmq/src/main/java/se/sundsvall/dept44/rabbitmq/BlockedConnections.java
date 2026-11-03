package se.sundsvall.dept44.rabbitmq;

import com.rabbitmq.client.BlockedListener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;

/**
 * Keeps track of the connections the broker blocks. During a memory or disk alarm the broker stops reading from a
 * connection once it publishes, and tells the client so. Anything sent on it after that waits in the socket until the
 * alarm clears, however long the confirm timeout.
 */
final class BlockedConnections implements ConnectionListener {

	private final Map<Connection, String> blocked = new ConcurrentHashMap<>();

	@Override
	public void onCreate(final Connection connection) {
		connection.addBlockedListener(new BlockedListener() {
			@Override
			public void handleBlocked(final String reason) {
				blocked.put(connection, String.valueOf(reason));
			}

			@Override
			public void handleUnblocked() {
				blocked.remove(connection);
			}
		});
	}

	@Override
	public void onClose(final Connection connection) {
		blocked.remove(connection);
	}

	/**
	 * @return why the broker blocks publishing, such as {@code low on memory}, or empty when it does not
	 */
	Optional<String> reason() {
		return blocked.values().stream().findFirst();
	}
}
