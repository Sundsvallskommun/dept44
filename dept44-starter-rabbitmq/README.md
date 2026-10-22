# Dept44 RabbitMQ Starter

Publishing and consuming over RabbitMQ, with safe defaults and publishing the broker has confirmed.

The starter never declares exchanges, queues or bindings. Each service declares its topology in its own deployment
configuration through rabbitmq-topology, and its AMQP user has no configure permission.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

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

## Turned off

`dept44.rabbitmq.enabled` is `false` by default. Off means no RabbitMQ beans at all, neither Spring Boot's nor the
starter's, and no connection to the broker. A `@RabbitListener` is not started, so listeners need no conditions of their
own.

A listener can stay in place with RabbitMQ off, waiting to be turned on. Its messages pile up in the queue meanwhile, so
the starter logs a warning at startup naming every class with a listener that receives nothing:

```
RabbitMQ is off (dept44.rabbitmq.enabled is not true), so the listeners in se.sundsvall.example.SmsListener receive nothing
```

A bean that injects `Dept44RabbitPublisher` or `RabbitTemplate` cannot be created while RabbitMQ is off. If the service
must start without RabbitMQ, inject `Optional<Dept44RabbitPublisher>` there.

Leave out `@EnableRabbit`. Spring Boot applies it when RabbitMQ is on. A service that adds it itself does not start
while RabbitMQ is off, since its listeners then have no container factory.

## Defaults

The starter sets these with the lowest precedence, so a service can override any of them in its `application.yml`.

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
| `spring.rabbitmq.listener.simple.prefetch`, `listener.direct.prefetch` | `1`                         | A listener that retries holds what it has prefetched, see below                         |

`Dept44RabbitPublisher` depends on the first three. A service that overrides one of them fails at startup with a
message naming the property.

Spring Boot's RabbitMQ health indicator stays on. It counts in `/actuator/health`, where Spring Boot Admin shows a broker
that does not answer, but not in the liveness and readiness groups the probes use. A service that wants the broker in
readiness adds it itself, with `management.endpoint.health.group.readiness.include=readinessState,rabbit`.

Every `RabbitTemplate`, Spring Boot's or the service's own, publishes on a connection of its own, apart from the one the
listeners consume on. During a memory or disk alarm the broker blocks a connection that publishes, and on a shared
connection the listeners' acks would go unread too, so the alarm would never clear.

A listener prefetches one message rather than Spring AMQP's 250. A prefetched message waits unacknowledged while the
listener retries the one before it, and the broker's `consumer_timeout`, 30 minutes by default, counts from the
delivery. Past it the broker closes the channel and delivers the messages again, while the listener may still be at
work on one. Prefetch times the time a message can take must stay below it. A message can take the 8 minutes of
retries plus the attempts themselves: against a database that does not answer, each attempt waits out Hikari's 30 s,
and the message takes some 18 minutes.

Prefetch does not decide how many messages are handled at once. A consumer takes one at a time whatever its prefetch,
and `spring.rabbitmq.listener.simple.concurrency` or more replicas give more consumers, each with a prefetch of its own.
A higher prefetch saves a round trip to the broker per message, about a millisecond, which only counts for a listener
that is about as quick. A service that raises it shortens the retries too. A batch listener prefetches its batch size,
whatever the setting.

## Publishing

`Dept44RabbitPublisher.publish` returns once the broker has taken responsibility for the message. Otherwise it throws
`RabbitPublishException`, and `getReason()` says why:

|    Reason     |                                                    Meaning                                                    |
|---------------|---------------------------------------------------------------------------------------------------------------|
| `NACKED`      | The broker refused the message, or the channel closed before the broker confirmed it, e.g. a missing exchange |
| `RETURNED`    | The broker could not route the message to any queue                                                           |
| `TIMEOUT`     | No confirm within `dept44.rabbitmq.publish-confirm-timeout`. The broker may still have the message            |
| `INTERRUPTED` | The thread was interrupted while waiting. The broker may still have the message                               |

When the broker cannot be reached at all, Spring AMQP's own `AmqpException` is thrown. `RabbitPublishException`
extends it.

For a returned message Spring AMQP also logs `Returned message but no callback available` at WARN. The publisher gets
the return together with the confirm, so the line does not mean that anything is misconfigured. The starter sets no
`ReturnsCallback` to quiet it, since a template takes only one and the service may need it.

```java
publisher.publish(OutgoingMessage.to("my-namespace.my-service", "errand.updated")
	.withPayload(event)
	.withMessageId(eventId)
	.withHeader("x-custom", value));
```

Every message gets a message id. The publisher generates one when the service sets none, and the exception's
`getMessageId()` returns it. Set your own when the receiver must recognize a duplicate, for instance when a publish that
timed out is retried: it may have reached the broker the first time. A retry in the same call can also reuse the id from
the exception.

What an exception means is up to the service, such as an error to its caller. When a listener publishes, not every
reason is worth trying again:

- `TIMEOUT` and `INTERRUPTED` may pass. The broker may already have the message, so publish it again with the same
  message id.
- `RETURNED` does not pass: no queue is bound for the routing key, and that is a topology error.
- `NACKED` can go either way. The message says why: a missing exchange does not pass, a channel that closed may.

A listener that publishes and lets the exception out is handled as [Failures in a listener](#failures-in-a-listener)
describes: `TIMEOUT` and `INTERRUPTED` as an outage, `NACKED` as a server error, and `RETURNED` is dead-lettered at
once.

Each publish waits for a round trip to the broker.

### Requests that expect a reply

`request` publishes like `publish`, but refuses a message without a message id, a correlation id and a reply-to address.
The responder publishes its reply to the reply-to address and copies the correlation id.

```java
publisher.request(OutgoingMessage.to("my-namespace.responder", "send")
	.withPayload(request)
	.withMessageId(requestId)
	.withCorrelationId(correlationKey)
	.withReplyTo("my-namespace.responder.reply", "my-namespace.my-service.replies"));
```

The reply-to address is an exchange owned by the responder and the routing key the sender's reply queue is bound with.
It is sent as `exchange/routing-key`.

## Request id over the queue

A message carries the request id and the identifier of the user as the headers `x-request-id` and `X-Sent-By`, the same
names Feign and WebClient use over HTTP. The listener handles the message under them, so the log lines on both sides
of the queue have the same request id. A message without a request id gets a new one, as an HTTP request does.

Both work without any code in the service: every message from a `RabbitTemplate`, Spring Boot's or the service's own,
gets the headers, and every listener container factory, Spring Boot's and the service's own, sets them on its listener
threads. A header the service sets itself is left as it is.

That includes the line that reports a failed listener. The container's error handler logs it after the listener has
returned, so the starter wraps the error handler each container has, Spring AMQP's or the service's own, to run it
under the failed message's request id. A container the service declares as a bean of its own, outside Spring's
listener registry, keeps its error handler as it is.

## Failures in a listener

A listener that throws is not delivered the message again at once. Delivered again within milliseconds, a message would
use up the queue's `x-delivery-limit` during an outage of a second, and be dead-lettered.

`TransientFailures` tells a failure that may pass from one that fails the same way every time:

- **Outage**: the service's own database cannot be reached or times out, a connection is refused, reset or times out,
  a host name does not resolve, a circuit breaker has opened, or a publish is not confirmed in time. Every message meets
  it alike while it lasts. The listener retries it in place with a growing delay: 1 s, 2 s, 4 s and so on up to every
  30 s, about 8 minutes in all. Meanwhile the message stays unacknowledged and the rest wait, as when the service is
  down. After the retries the message goes back in the queue, at its place first in line, and counts against its
  `x-delivery-limit`. A queue with a limit of 5 allows six deliveries, so the message waits out an outage of about 50
  minutes before it dead-letters.
- **Server error**: a service called through dept44's Feign answers with a 5xx (`ServerProblem`). It may be the
  service restarting or overloaded, which passes, or this message's data that it fails on every time, which does not.
  dept44's Feign error decoder answers 502 for all of them, so they cannot be told apart. The listener retries it the
  same way, and once the retries are over the message is dead-lettered rather than put back. A message that always
  fails holds up the others for about 8 minutes, not 50. A publish the broker nacks is handled the same way: a channel
  that closed passes, an exchange that is missing does not.
- **Anything else**: a message that cannot be read or does not validate, a 4xx from a called service, a bug. It is
  dead-lettered at once, without retries.

The whole cause chain counts, so a service's own exception that wraps one of the transient ones is retried too.

dept44 services put a circuit breaker on their Feign clients and repositories, and an open one counts as an outage
whatever opened it. A message whose server errors open the breaker can therefore go back in the queue rather than be
dead-lettered, and then reaches the dead letter queue through its delivery limit instead.

A listener can also decide for itself, whatever the exception wraps, and is not retried:
`AmqpRejectAndDontRequeueException` has the message dead-lettered, `ImmediateRequeueAmqpException` puts it back in the
queue and `ImmediateAcknowledgeAmqpException` acknowledges it. A listener that is interrupted, as when the service shuts
down, puts its message back in the queue whatever the failure. A batch listener's batch is retried and recovered as
one.

The retries hold the message unacknowledged, which is why a listener prefetches only one, see [Defaults](#defaults).

The delivery limit only ends an outage while the broker counts a message that is put back. Up to RabbitMQ 4.2, the
version in TEST, it counts every requeue. From 4.3 it counts only a failed delivery: `basic.reject` and a channel that
closes count, `basic.nack` does not. Spring AMQP 4.1.1 puts the message back with `basic.nack`, so against RabbitMQ 4.3
or later an outage is waited out however long it lasts. Spring AMQP rejects each delivery from 4.1.2 and 4.2.0
([spring-amqp#3507](https://github.com/spring-projects/spring-amqp/issues/3507)), so upgrade it before the broker.

A service that needs another split declares a `RabbitListenerRetrySettingsCustomizer` with its own exception predicate,
which runs after the starter's, and a `MessageRecoverer` of its own. With batch listeners it must be a
`MessageBatchRecoverer`, or Spring AMQP acknowledges a failed batch. Both can build on `TransientFailures.isTransient`
and `isOutage`. The delays are Spring Boot's `spring.rabbitmq.listener.simple.retry.*` properties.

## JSON

Messages are JSON, written and read with the service's own `JsonMapper`, so dates and null handling are the same as in
its REST API. The producer does not send its class name in `__TypeId__`. The contract is the JSON, and a consumer gets
its type from the parameter of its listener method.

That rules out a `@RabbitListener` on a class with several `@RabbitHandler` methods: Spring AMQP picks the handler by
the payload's type, and without the header the payload is a `Map`. Put `@RabbitListener` on the method instead.

A service that defines its own `MessageConverter` bean replaces this one.

## Configuration

|                 Property                  | Default |                                                                            |
|-------------------------------------------|---------|----------------------------------------------------------------------------|
| `dept44.rabbitmq.enabled`                 | `false` | Only `true` turns RabbitMQ on, see [Turned off](#turned-off)               |
| `dept44.rabbitmq.publish-confirm-timeout` | `5s`    | How long `Dept44RabbitPublisher` waits for the broker to confirm a message |

