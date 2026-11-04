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
The producer sends no `__TypeId__` header. The contract is the JSON, and a consumer gets its type from the listener's
parameter. That rules out a `@RabbitListener` class with several `@RabbitHandler` methods, since Spring AMQP picks the
handler by the payload's type. A `MessageConverter` bean of the service's own replaces the starter's.

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
within an outage of a second. This counts on two things:

- Each queue is a quorum queue with a dead-letter exchange and an `x-delivery-limit`. Without a dead-letter exchange
  the broker drops a rejected message.
- The container acknowledges or rejects each message as the listener returns or throws, the `auto` acknowledge mode
  Spring AMQP defaults to. With `manual`, a message the listener fails on stays unacknowledged until the channel closes.

`TransientFailures` sorts a failure into one of three kinds, by the whole cause chain:

- **Outage**: the database, a connection or a host name that does not answer, an open circuit breaker, or a publish
  that timed out or was blocked. The listener retries with a growing delay, 1 s up to every 30 s, about 8 minutes in
  all, while the message stays unacknowledged and the rest wait. Then the message goes back first in the queue and
  counts against its `x-delivery-limit`. With a limit of 5 it waits out an outage of about 50 minutes before it is
  dead-lettered.
- **Server error**: a 5xx from a service called through dept44's Feign (`ServerProblem`), or a publish the broker
  nacked. It may pass, or it may be this message's data that fails every time, and dept44's Feign error decoder answers
  502 for both. The listener retries it the same way and then dead-letters it, so a message that always fails holds up
  the others for 8 minutes, not 50.
- **Anything else**: a message that cannot be read or does not validate, a 4xx, a bug. Dead-lettered at once.

These may pass, but the split does not know them and dead-letters them at once: a read timeout or a 5xx through
WebClient, and a 429 or 408 through Feign, which dept44's error decoder turns into a `ClientProblem`.

An open circuit breaker counts as an outage whatever opened it, so a message whose server errors open it goes back in
the queue and reaches the dead letter queue through its delivery limit instead.

A listener must be idempotent, since a message can reach it more than once. The retries call it again, a message put
back in the queue is delivered again, and so is one whose channel closes before the ack. A publish retried after a
`TIMEOUT` can also send the same message twice. The message id lets the listener recognize a duplicate.

A listener can decide for itself, without retries and whatever the exception wraps. `AmqpRejectAndDontRequeueException`
dead-letters the message, `ImmediateRequeueAmqpException` puts it back in the queue and
`ImmediateAcknowledgeAmqpException` acknowledges it. A listener interrupted at shutdown puts its message back. A batch
listener's batch is retried and recovered as one.

A service that needs another split declares a `RabbitListenerRetrySettingsCustomizer` with its own exception predicate,
which runs after the starter's, and a `MessageRecoverer`. With batch listeners it must be a `MessageBatchRecoverer`, or
Spring AMQP acknowledges a failed batch. Both can build on `TransientFailures.isTransient` and `isOutage`.

## Operational considerations

### Memory and disk alarms

During an alarm the broker blocks a connection once it publishes. Every `RabbitTemplate` bean therefore publishes on a
connection of its own. On a connection shared with the listeners their acks would go unread too, and the alarm would
never clear. A template created with `new` outside a bean gets neither this nor the request id headers.

The publisher fails a publish on a blocked connection at once with `BLOCKED` rather than waiting until the alarm
clears. The first publish of an alarm is the one that gets the connection blocked, so it ends in `TIMEOUT`.

### Prefetch

A listener prefetches one message, not Spring AMQP's 250. A prefetched message waits unacknowledged while the listener
retries the one before it, and the broker's `consumer_timeout`, 30 minutes by default, counts from the delivery. Past it
the broker closes the channel and delivers the messages again. Prefetch times the time a message can take must stay
below it, and against a database that does not answer a message takes some 18 minutes: the retries plus Hikari's 30 s
per attempt.

Prefetch does not limit how many messages are handled at once. `spring.rabbitmq.listener.simple.concurrency` or more
replicas give more consumers. A higher prefetch saves about a millisecond per message, and a service that raises it must
shorten the retries to match.

### RabbitMQ 4.3 and Spring AMQP

From RabbitMQ 4.3 the delivery limit counts only a failed delivery, a `basic.reject` or a channel that closes, and not a
`basic.nack`. Spring AMQP before 4.1.2 puts a message back with `basic.nack`, so an outage would never reach the limit
([spring-amqp#3507](https://github.com/spring-projects/spring-amqp/issues/3507)). Spring Boot 4.1.2 manages Spring AMQP
4.1.2. A service must not pin an older one.

### Health

Spring Boot's RabbitMQ health indicator counts in `/actuator/health`, where Spring Boot Admin shows a broker that does
not answer, but not in the probes' liveness and readiness groups. To have it in readiness, set
`management.endpoint.health.group.readiness.include=readinessState,rabbit`.

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

|                                   Ends in                                    |                                                    When                                                    |
|------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `was acknowledged`                                                           | The listener returned, or threw `ImmediateAcknowledgeAmqpException`                                        |
| `was put back in the queue, which dead-letters it past its x-delivery-limit` | An outage outlasted the retries, the listener was interrupted, or it threw `ImmediateRequeueAmqpException` |
| `was rejected, for the queue to dead-letter`                                 | Any other failure                                                                                          |
| `failed`                                                                     | Nothing said what becomes of the message, and the container decides                                        |

A failure ends in `failed` with the retry turned off, with `spring.rabbitmq.listener.simple.retry.stateless=false`,
with a `MessageRecoverer` of the service's own that throws the failure on, and with a container factory not set up with
Spring Boot's configurer.

A message gets a line for each delivery, not for each attempt, with its request id. A message put back when its delivery
count has reached the queue's `x-delivery-limit` is dead-lettered by the queue, so its last line says it was put back.
A batch listener logs one line for the batch, with its message ids.

A listener logs no line where the container does not settle each message as the listener returns or throws: with
`acknowledge-mode` `manual` or `none`, and with a `simple.batch-size` above 1 without `consumer-batch-enabled`. The
starter reads these from Spring Boot's properties, so a `@RabbitListener` with an `ackMode` of its own, or one that
returns a `CompletableFuture` or a `Mono`, still logs a line, and the line may be wrong.

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

