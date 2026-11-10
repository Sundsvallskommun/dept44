# Dept44 RabbitMQ Starter

Publishing and consuming over RabbitMQ, with safe defaults and publishing the broker has confirmed.

The starter never declares exchanges, queues or bindings. Each service declares its topology in its own deployment
configuration through rabbitmq-topology, and its AMQP user has no configure permission.

## Getting started

Needs Java 25 and Spring Boot 4. Add the dependency:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-rabbitmq</artifactId>
</dependency>
```

Turn it on and point it at the broker, per environment:

```yaml
dept44:
  rabbitmq:
    enabled: true
spring:
  rabbitmq:
    host: rabbitmq.example.se
    port: 5672
    virtual-host: my-vhost
    username: my-user
    password: my-password
```

Publish with `Dept44RabbitPublisher`, which returns once the broker has confirmed the message, see
[Publishing](#publishing):

```java
publisher.publish(OutgoingMessage.to("my-namespace.my-service", "errand.updated")
	.withPayload(event));
```

Consume with `@RabbitListener` on a method. A listener that throws is retried or dead-lettered, see
[Failures in a listener](#failures-in-a-listener):

```java
@RabbitListener(queues = "my-namespace.other-service.errands")
void onErrandUpdated(final ErrandUpdated event) {
	// ...
}
```

Messages are JSON, written and read with the service's own `JsonMapper`, so dates and null handling match its REST API.
They are not indented, though dept44 indents the REST API's JSON. The producer sends no `__TypeId__` header. The
contract is the JSON, and a consumer gets its type from the listener's parameter. That rules out a `@RabbitListener`
class with several `@RabbitHandler` methods, since Spring AMQP picks the handler by the payload's type. A
`MessageConverter` bean of the service's own replaces the starter's.

### Turned off

`dept44.rabbitmq.enabled` is `false` by default. Off means no RabbitMQ beans and no connection to the broker. Listeners
need no conditions of their own. They are not started, and since their messages pile up in the queue meanwhile, the
starter logs a warning at startup naming each class with a listener.

A bean that injects `Dept44RabbitPublisher` or `RabbitTemplate` cannot be created while RabbitMQ is off. Inject
`Optional<Dept44RabbitPublisher>` if the service must start without it.

Leave out `@EnableRabbit`. Spring Boot applies it when RabbitMQ is on, and with it the service does not start while
RabbitMQ is off.

## Publishing

`publish` throws `RabbitPublishException` when the broker has not taken responsibility for the message, and
`getReason()` says why:

|    Reason     |                                                    Meaning                                                    |
|---------------|---------------------------------------------------------------------------------------------------------------|
| `NACKED`      | The broker refused the message, or the channel closed before the broker confirmed it, e.g. a missing exchange |
| `RETURNED`    | The broker could not route the message to any queue                                                           |
| `TIMEOUT`     | No confirm within `dept44.rabbitmq.publish-confirm-timeout`. The broker may still have the message            |
| `INTERRUPTED` | The thread was interrupted while waiting. The broker may still have the message                               |
| `BLOCKED`     | Not sent. The broker blocks publishing during a memory or disk alarm                                          |

A broker that cannot be reached at all gives Spring AMQP's `AmqpException`, which `RabbitPublishException` extends.

Not every reason is worth trying again:

- `TIMEOUT` and `INTERRUPTED` may pass. The broker may already have the message, so publish it again with the same
  message id.
- `BLOCKED` passes when the alarm clears. Nothing was sent.
- `RETURNED` does not pass. No queue is bound for the routing key, and that is a topology error.
- `NACKED` can go either way. A missing exchange does not pass, a channel that closed may.

A listener that publishes and lets the exception out gets the same split: `TIMEOUT`, `INTERRUPTED` and `BLOCKED` are
retried as an outage, `NACKED` as a server error, and `RETURNED` is dead-lettered at once.

Every message gets a message id. The publisher generates one when the service sets none, and the exception's
`getMessageId()` returns it. Set your own with `withMessageId` when the receiver must recognize a duplicate.

A listener that publishes should derive the id from the message it handles, such as its message id followed by what it
publishes. A retry or a redelivery then publishes the message again under the same id. A generated id is new each
time, so the receiver cannot tell the copies apart.

`publish` sends at once and waits for the broker, also inside `@Transactional`. A rollback after it leaves the message
sent, and a consumer can act on it before the commit is visible. Publish after the commit instead, for example from a
`@TransactionalEventListener(phase = AFTER_COMMIT)`. The message is then lost if the service stops between the commit
and the publish. Where that matters, write the message to an outbox table in the same transaction and publish from
there.

A service with more than one `RabbitTemplate` gets a publisher for the `@Primary` one. Without a primary template the
starter declares no publisher, and the service declares its own with `new Dept44RabbitPublisher(template, timeout)`.

A channel-transacted `RabbitTemplate` or listener container fails the startup. RabbitMQ closes a channel that has both
a transaction and publisher confirms, and the publisher depends on the confirms.

For a returned message Spring AMQP also logs `Returned message but no callback available` at WARN. The publisher gets
the return with the confirm, so the line does not mean anything is misconfigured.

### Requests that expect a reply

`request` publishes like `publish`, but requires a message id, a correlation id and a reply-to address. It does not
wait for the reply, which arrives on the sender's own reply queue:

```java
publisher.request(OutgoingMessage.to("my-namespace.responder", "send")
	.withPayload(request)
	.withMessageId(requestId)
	.withCorrelationId(correlationKey)
	.withReplyTo("my-namespace.responder.reply", "my-namespace.my-service.replies"));
```

The reply-to address is an exchange owned by the responder and the routing key the sender's reply queue is bound with.
The responder publishes its reply there with `Dept44RabbitPublisher` and copies the correlation id.

It should not return the reply from its listener method. Spring AMQP sends a return value on the consumer's channel,
the broker does not confirm it, and during an alarm the broker blocks the listeners' acks along with it.

## Failures in a listener

A listener that throws is not delivered the message again at once, which would use up the queue's `x-delivery-limit`
within an outage of a second. This counts on three things:

- Each queue is a quorum queue with a dead-letter exchange, an `x-delivery-limit` and
  `x-dead-letter-strategy: at-least-once`, which also needs `x-overflow: reject-publish`. Without a dead-letter
  exchange the broker drops a rejected message. With the default strategy, `at-most-once`, it dead-letters without
  confirms, and a message the dead letter queue does not take, because it is missing or unavailable, is lost. Set the
  two with a policy (`dead-letter-strategy`, `overflow`): it also applies to a queue that already exists, which RabbitMQ
  refuses to declare again with other arguments.
- The container acknowledges or rejects each message as the listener returns or throws, the `auto` acknowledge mode
  Spring AMQP defaults to. With `manual`, a message the listener fails on stays unacknowledged until the channel closes.
- The container settles each message on its own. A `spring.rabbitmq.listener.simple.batch-size` above 1 fails the
  startup unless `consumer-batch-enabled` is on. The container would otherwise put back the messages handled before a
  failed one, to be handled again, with a `basic.nack` that the delivery limit does not count.

`TransientFailures` sorts a failure into one of three kinds, by the whole cause chain:

- **Outage**: the database, a connection or a host name that does not answer, an open circuit breaker, or a publish
  that timed out, was interrupted or was blocked. The listener retries with a growing delay, 1 s up to every 30 s,
  about 8 minutes in all plus the time the attempts take, and stops after 15 minutes. Meanwhile the message stays
  unacknowledged and the rest wait. Then the message goes back first in the queue and counts against its
  `x-delivery-limit`. With a limit of 5 it waits out an outage of about 50 minutes before it is dead-lettered. A batch
  listener's batch is dead-lettered once its retries are over instead, see RabbitMQ 4.3 and Spring AMQP below.
- **Server error**: a 5xx from a service called through dept44's Feign (`ServerProblem`), or a publish the broker
  nacked. It may pass, or it may be this message's data that fails every time, and dept44's Feign error decoder answers
  502 for both. The listener retries it the same way and then dead-letters it, so a message that always fails holds up
  the others for 8 minutes, not 50.
- **Anything else**: a message that cannot be read or does not validate, a 4xx, a bug. Dead-lettered at once.

These may pass, but the starter's split does not know them and dead-letters them at once: a read timeout or a 5xx
through WebClient, and a 429 or 408 through Feign, which dept44's error decoder turns into a `ClientProblem`. A service
that meets them adds them to the split, see below.

An open circuit breaker counts as an outage whatever opened it, so a message whose server errors open it goes back in
the queue and reaches the dead letter queue through its delivery limit instead.

A listener must be idempotent, since a message can reach it more than once. The retries call it again, a message put
back in the queue is delivered again, and so is one whose channel closes before the ack. A publish retried after a
`TIMEOUT` can also send the same message twice. The message id lets the listener recognize a duplicate, when the
sender sets it rather than lets the publisher generate one.

A listener can decide for itself, without retries and whatever the exception wraps. `AmqpRejectAndDontRequeueException`
dead-letters the message, `ImmediateRequeueAmqpException` puts it back in the queue and
`ImmediateAcknowledgeAmqpException` acknowledges it. A listener interrupted at shutdown puts its message back. A batch
listener's batch is retried and recovered as one.

A listener that returns a `CompletableFuture` or a `Mono` fails after the listener method has returned, and gets no
retries. Spring AMQP puts each failed message back in the queue, so it is delivered again at once and dead-lettered as
soon as it reaches its `x-delivery-limit`, after an outage too.

The retries and what becomes of the message once they are over both follow the `TransientFailures` bean. A service
that knows more failures declares its own, built on the starter's. Each addition is tested against every exception in
the cause chain:

```java
@Bean
TransientFailures transientFailures() {
	return TransientFailures.defaults()
		.withOutage(WebClientRequestException.class::isInstance)
		.withServerError(e -> e instanceof WebClientResponseException response && response.getStatusCode().is5xxServerError());
}
```

A listener that decides for itself, as above, still gets what it asked for. A service that needs more than another
split declares a `MessageRecoverer` of its own. With batch listeners it must be a `MessageBatchRecoverer`, or Spring
AMQP acknowledges a failed batch.

## Operational considerations

### Memory and disk alarms

During an alarm the broker blocks a connection once it publishes. Every `RabbitTemplate` bean therefore publishes on a
connection of its own. On a connection shared with the listeners their acks would go unread too, and the alarm would
never clear. A template created with `new` outside a bean gets neither this nor the request id headers, and
`Dept44RabbitPublisher` refuses it unless it calls `setUsePublisherConnection(true)`.

The template also receives on that connection. Once it has published during an alarm, `receive()` waits until the
alarm clears or its timeout runs out. A `@RabbitListener` consumes on the listeners' connection and is not affected.

The publisher fails a publish on a blocked connection at once with `BLOCKED` rather than waiting until the alarm
clears. The first publish of an alarm is the one that gets the connection blocked, so it ends in `TIMEOUT`.

### Prefetch

A listener prefetches one message, not Spring AMQP's 250. A prefetched message waits unacknowledged while the listener
retries the one before it, and the broker's `consumer_timeout`, 30 minutes by default, counts from the delivery. Past it
the broker closes the channel and delivers the messages again. Prefetch times the time a message can take must stay
below it. The retries stop after 15 minutes, which leaves the last attempt as long again.

Prefetch does not limit how many messages are handled at once. `spring.rabbitmq.listener.simple.concurrency` or more
replicas give more consumers. A higher prefetch saves about a millisecond per message, and a service that raises it must
shorten the retries to match.

### RabbitMQ 4.3 and Spring AMQP

From RabbitMQ 4.3 the delivery limit counts only a failed delivery, a `basic.reject` or a channel that closes, and not a
`basic.nack`. Spring AMQP before 4.1.2 puts a message back with `basic.nack`, so an outage would never reach the limit
([spring-amqp#3507](https://github.com/spring-projects/spring-amqp/issues/3507)). Spring Boot 4.1.2 manages Spring AMQP
4.1.2. A service must not pin an older one.

Spring AMQP 4.1.2 still puts a batch listener's batch back with `basic.nack`, and the batch would come back for as long
as the outage lasts. The starter therefore dead-letters a batch once an outage outlasts its retries.

Spring AMQP also uses `basic.nack` where the listener may settle a message itself: a listener method with a `Channel`
parameter, or a `@RabbitListener` with an `errorHandler`. A message put back after an outage then comes back until the
outage is over, and never reaches the dead letter queue. The starter logs a warning for each such listener at startup.
Without the parameter and the error handler, the message is counted.

### Health

Spring Boot's RabbitMQ health indicator counts in `/actuator/health`, where Spring Boot Admin shows a broker that does
not answer, but not in the probes' liveness and readiness groups. To have it in readiness, set
`management.endpoint.health.group.readiness.include=readinessState,rabbit`.

That indicator only checks that a connection opens. It says nothing about the listeners. Spring AMQP's simple
container, Spring Boot's default, stops for good when the broker refuses its login, as when the broker is rebuilt and
the service's user comes back after the service has tried to reconnect, or when its queue stays missing. Nothing
starts the container again, and its queue fills while the service reports UP. The starter therefore adds two things:

- `rabbitListeners` in `/actuator/health` is DOWN for each container in the registry that should run but does not,
  with its queues. Running is not consuming: a container that retries a broker it cannot reach, or a vhost it may not
  use, is UP. `management.health.rabbit-listeners.enabled=false` turns it off, as any other health indicator.
- Once Spring AMQP has stopped a container for good because the broker lacks the user or the queue, the starter marks
  the application `LivenessState.BROKEN`. The liveness probe then fails, and Kubernetes restarts the pod. The restart
  starts the container again, or fails until the user or the queue is back. This covers a container the service
  declares as a bean of its own too, and cannot be turned off.

The restart costs the REST API as well. The pod is down while it restarts, and crash-loops while the user or the queue
is missing. After a broker rebuild that is about a minute. A queue removed from the topology while its consumer still
listens to it keeps the service down until one of the two is fixed.

A container the service stops itself shows as DOWN in `rabbitListeners`, but does not restart the pod. Neither does a
fatal failure the listener causes, such as a message it cannot handle, since the restarted service would only get the
message again. The direct container retries a refused login and a missing queue until they are back, and needs
neither.

### Request id over the queue

A message carries the request id and the user's identifier as the headers `x-request-id` and `X-Sent-By`, as Feign and
WebClient do over HTTP, and the listener handles it under them, so both sides log the same request id. Every
`RabbitTemplate` bean sets them, and so does a reply a listener returns, but a header the service sets itself wins. A
message without a request id gets a new one, and so does a batch. The container's error handler, which logs a failed
listener, runs under the message's or the batch's request id.

A container the service declares as a bean of its own, outside Spring's listener registry, gets none of this. A
container created after startup, such as one for a `@RabbitListener` on a `@Lazy` bean, gets no request id in its error
handler or on its replies.

### A line for each message

The starter logs a line at INFO for each message, on the logger `se.sundsvall.dept44.rabbitmq.messages`.
`Dept44RabbitPublisher` logs a message the broker has confirmed, and a listener a message it has handled, with how long
that took and what became of it:

```
Message 0f28f6e7-... to exchange 'my-namespace.my-service' with routing key 'errand.updated' was confirmed by the broker
Message 0f28f6e7-... from queue 'my-namespace.other-service.errands' with routing key 'errand.updated' (delivery count 0, 14 ms) was acknowledged
```

|                                   Ends in                                    |                                                           When                                                            |
|------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `was acknowledged`                                                           | The listener returned, or threw `ImmediateAcknowledgeAmqpException`                                                       |
| `was put back in the queue, which dead-letters it past its x-delivery-limit` | An outage outlasted a single message's retries, the listener was interrupted, or it threw `ImmediateRequeueAmqpException` |
| `was rejected, for the queue to dead-letter`                                 | Any other failure                                                                                                         |
| `failed`                                                                     | Nothing said what becomes of the message, and the container decides                                                       |

A failure ends in `failed` with the retry turned off, with `spring.rabbitmq.listener.simple.retry.stateless=false`,
with a `MessageRecoverer` of the service's own that throws the failure on, and with a container factory not set up with
Spring Boot's configurer.

A message gets a line for each delivery, not for each attempt, with its request id. A message put back when its delivery
count has reached the queue's `x-delivery-limit` is dead-lettered by the queue, so its last line says it was put back.
A batch listener logs one line for the batch, with its message ids.

A listener logs no line where the container does not settle each message as the listener returns or throws: with
`acknowledge-mode` `manual` or `none`. The starter reads it from Spring Boot's properties, so a `@RabbitListener` with
an `ackMode` of its own, or one that returns a `CompletableFuture` or a `Mono`, still logs a line, and the line may be
wrong.

The lines leave out the body and the headers, which may hold personal data. A message that is rejected, or put back once
its retries are over, also gets a WARN line with the cause. A failed publish and a publish through a `RabbitTemplate`
are not logged.

Turn the lines off with `logging.level.se.sundsvall.dept44.rabbitmq.messages=WARN`. With
`dept44.logback.pii-masking.enabled=true` a message id that is a UUID, as a generated one is, is masked, unless the
service also sets `dept44.logback.pii-masking.uuid=false`.

## Configuration

|                 Property                  | Default |                                                                            |
|-------------------------------------------|---------|----------------------------------------------------------------------------|
| `dept44.rabbitmq.enabled`                 | `false` | Only `true` turns RabbitMQ on, see [Turned off](#turned-off)               |
| `dept44.rabbitmq.publish-confirm-timeout` | `5s`    | How long `Dept44RabbitPublisher` waits for the broker to confirm a message |

The starter also sets these Spring Boot defaults, with the lowest precedence so a service can override them.
`Dept44RabbitPublisher` depends on the first three, and a service that overrides one of them fails at startup.

|                                Property                                |           Default           |                                           Why                                           |
|------------------------------------------------------------------------|-----------------------------|-----------------------------------------------------------------------------------------|
| `spring.rabbitmq.publisher-confirm-type`                               | `correlated`                | The broker confirms each message                                                        |
| `spring.rabbitmq.publisher-returns`                                    | `true`                      | The template listens for messages the broker returns                                    |
| `spring.rabbitmq.template.mandatory`                                   | `true`                      | The broker returns a message it cannot route, instead of confirming and dropping it     |
| `spring.rabbitmq.connection-timeout`                                   | `5s`                        | A broker that cannot be reached fails a publish in seconds, not after the client's 60 s |
| `spring.rabbitmq.dynamic`                                              | `false`                     | No `RabbitAdmin`. The topology comes from rabbitmq-topology                             |
| `spring.rabbitmq.template.observation-enabled`                         | `true`                      | Trace context follows the message once tracing is on                                    |
| `spring.rabbitmq.listener.simple.observation-enabled`                  | `true`                      | As above, on the consuming side                                                         |
| `spring.rabbitmq.listener.direct.observation-enabled`                  | `true`                      | As above, for a service that uses the direct listener container                         |
| `spring.rabbitmq.listener.simple.retry.*`, `listener.direct.retry.*`   | on, 1 s to 30 s, 20 retries | See [Failures in a listener](#failures-in-a-listener)                                   |
| `spring.rabbitmq.listener.simple.prefetch`, `listener.direct.prefetch` | `1`                         | See [Prefetch](#prefetch)                                                               |

# dept44-starter-rabbitmq: the classes

## Overview

The starter connects a dept44 service to RabbitMQ. It publishes with confirms from the broker, retries transient failures in listeners and dead-letters the rest. It also carries the request id across messages and reports the health of the listeners. Everything is off until the service sets `dept44.rabbitmq.enabled=true`.

- `se.sundsvall.dept44.rabbitmq` is what services use: the publisher, the message, the publish exception and the rules for which failures are transient.
- `se.sundsvall.dept44.rabbitmq.config` wires it all together. Its classes are package-private, except the two that Spring Boot loads by name and `RabbitMqProperties`.

The defaults live in `dept44-rabbitmq-default.properties`, and a service overrides any of them in its own configuration. They turn on publisher confirms and returns, set prefetch to 1, retry for about 8 minutes (15 at most) and set `dynamic=false`, since the topology is declared through rabbitmq-topology.

## The `se.sundsvall.dept44.rabbitmq` package

This is what a service writes code against. Every class except `BlockedConnections` is public.

|          Class           |                                                                                                                                                                                                                       What it does                                                                                                                                                                                                                        |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Dept44RabbitPublisher`  | Publishes a message and returns only once the broker has confirmed it and not returned it as unroutable. Otherwise it throws `RabbitPublishException`. `publish` sends, and `request` also requires a message id, a correlation id and a reply-to. It generates a message id when the message has none. It refuses a template that shares a connection with the listeners or lacks confirms and returns. It logs each confirmed message without its body. |
| `OutgoingMessage`        | The message for the publisher: exchange, routing key, payload (sent as JSON), message id, correlation id, reply-to and headers. Immutable, so every `with` method returns a new message. Not a record, so `toString` never prints the payload.                                                                                                                                                                                                            |
| `RabbitPublishException` | Thrown when the broker has not taken responsibility for the message. `getReason()` says why: `NACKED`, `RETURNED`, `TIMEOUT`, `INTERRUPTED` or `BLOCKED`. After `TIMEOUT` and `INTERRUPTED` the broker may still have the message, so publish it again with the same message id.                                                                                                                                                                          |
| `TransientFailures`      | Decides which failures are transient. An outage (database, network, broker, open circuit breaker) is retried and then put back in the queue. A server error (a 5xx from a called service, a nacked publish) is retried and then dead-lettered. Anything else is dead-lettered at once. A service adds failures of its own with a bean built on `defaults()`.                                                                                              |
| `BlockedConnections`     | Tracks the connections the broker blocks during a memory or disk alarm. The publisher then fails with `BLOCKED` at once instead of waiting in the socket until the alarm clears. Package-private.                                                                                                                                                                                                                                                         |

## The `se.sundsvall.dept44.rabbitmq.config` package

This wires everything together, and a service touches none of it directly. Only the three classes marked public are visible outside the package.

|    Area    |                            Class                             |                                                                                                                                                                                                                                                              What it does                                                                                                                                                                                                                                                              |
|------------|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| On and off | `RabbitMqAutoConfigurationImportFilter` (public)             | Reads `dept44.rabbitmq.enabled`, where only `true` turns RabbitMQ on. Off removes every RabbitMQ auto-configuration, Spring Boot's and the starter's: no beans, no connection, no listeners. `RabbitMqDisabledConfiguration` loads instead.                                                                                                                                                                                                                                                                                            |
| On and off | `RabbitMqDisabledConfiguration`                              | Loads when RabbitMQ is off and declares only `DisabledListenerWarning`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| On and off | `DisabledListenerWarning`                                    | Warns at startup when the service has a `@RabbitListener` but RabbitMQ is off. Without it, a listener would stop receiving without a trace while its queue grows.                                                                                                                                                                                                                                                                                                                                                                      |
| Settings   | `RabbitMqDefaultPropertiesEnvironmentPostProcessor` (public) | Adds `dept44-rabbitmq-default.properties` last in the environment, so that the service's own configuration wins.                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Settings   | `RabbitMqProperties` (public)                                | The starter's own settings under `dept44.rabbitmq`. Today only `publish-confirm-timeout`, 5 s by default.                                                                                                                                                                                                                                                                                                                                                                                                                              |
| Settings   | `RabbitMqConfiguration`                                      | The auto-configuration when RabbitMQ is on. It declares the JSON converter without type headers, gives every `RabbitTemplate` a publishing connection of its own and the request id, and puts the request id and log advice first in every listener container factory. It ties the retry to `TransientFailures` with a 15-minute cap, and declares the recoverer, the publisher and the health beans. It fails the startup for a transacted channel with publisher confirms, and for a `batch-size` above 1 without consumer batching. |
| Listeners  | `TransientFailureRecoverer`                                  | Decides what becomes of a message once its retries are over. An outage goes back in the queue and counts against `x-delivery-limit`. A listener that threw `AmqpRejectAndDontRequeueException`, or one of the two other exceptions that set the outcome, gets what it asked for. Anything else is rejected and dead-lettered. It logs the id and the queue, never the body.                                                                                                                                                            |
| Listeners  | `ListenerLogAdvice`                                          | Logs a line per delivery with the message id, queue, routing key, delivery count, outcome and time taken. It leaves out the body and the headers, which may hold personal data. It sits before the retry, so there is one line per delivery, not per attempt.                                                                                                                                                                                                                                                                          |
| Request id | `MessageRequestId`                                           | Puts the message's request id and user identifier on the thread while the message is handled. A message without a request id gets a new one, as an HTTP request does.                                                                                                                                                                                                                                                                                                                                                                  |
| Request id | `RequestIdListenerAdvice`                                    | First in the advice chain. Runs the listener under the message's request id and clears it afterwards, so that the log lines on both sides of the queue carry the same id.                                                                                                                                                                                                                                                                                                                                                              |
| Request id | `RequestIdHeaders`                                           | Sends the request id and user identifier as headers on every outgoing message and reply, under the names Feign and WebClient use. A header the service has set itself stays as it is.                                                                                                                                                                                                                                                                                                                                                  |
| Request id | `RequestIdErrorHandler`                                      | Runs the container's error handler under the message's request id. The container calls it after the advice has already cleared the id.                                                                                                                                                                                                                                                                                                                                                                                                 |
| Request id | `RequestIdContainerInstaller`                                | Goes through every container in the registry before they start. It wraps the error handler in `RequestIdErrorHandler` and adds `RequestIdHeaders` to the replies a listener returns. It warns about a listener whose failed messages go back with a `basic.nack`, which does not count against `x-delivery-limit`, and fails the startup for a transacted container while publisher confirms are on.                                                                                                                                   |
| Health     | `RabbitListenersHealthIndicator`                             | The `rabbitListeners` health indicator. It is DOWN, with the queues listed, when a container that should start automatically has stopped, and UNKNOWN before the context has started. `management.health.rabbit-listeners.enabled=false` turns it off.                                                                                                                                                                                                                                                                                 |
| Health     | `StoppedListenerLiveness`                                    | Marks liveness BROKEN when a container has stopped for good because the broker lacks the service's user or queue. The liveness probe then fails, and Kubernetes restarts the pod. It cannot be turned off.                                                                                                                                                                                                                                                                                                                             |

