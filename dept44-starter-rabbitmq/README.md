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

Leave out `@EnableRabbit`. Spring Boot applies it when RabbitMQ is on. A service that adds it itself has its listeners
processed even when RabbitMQ is off.

## Defaults

The starter sets these with the lowest precedence, so a service can override any of them in its `application.yml`.

|                       Property                        |   Default    |                                           Why                                           |
|-------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------|
| `spring.rabbitmq.publisher-confirm-type`              | `correlated` | The broker confirms each message                                                        |
| `spring.rabbitmq.publisher-returns`                   | `true`       | The template listens for messages the broker returns                                    |
| `spring.rabbitmq.template.mandatory`                  | `true`       | The broker returns a message it cannot route, instead of confirming and dropping it     |
| `spring.rabbitmq.connection-timeout`                  | `5s`         | A broker that cannot be reached fails a publish in seconds, not after the client's 60 s |
| `spring.rabbitmq.dynamic`                             | `false`      | No `RabbitAdmin`. The topology comes from rabbitmq-topology                             |
| `spring.rabbitmq.template.observation-enabled`        | `true`       | Trace context follows the message once tracing is on                                    |
| `spring.rabbitmq.listener.simple.observation-enabled` | `true`       | As above, on the consuming side                                                         |
| `spring.rabbitmq.listener.direct.observation-enabled` | `true`       | As above, for a service that uses the direct listener container                         |
| `management.health.rabbit.enabled`                    | `false`      | A broker that does not answer does not take the service down                            |

`Dept44RabbitPublisher` depends on the first three. A service that overrides one of them fails at startup with a
message naming the property.

The starter also publishes on a connection of its own, apart from the one its listeners consume on. During a memory or
disk alarm the broker blocks a connection that publishes, and on a shared connection the listeners' acks would go unread
too, so the alarm would never clear.

Prefetch and concurrency are left to the service. The right values depend on how large the messages are and what the
consumer does with them.

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

```java
publisher.publish(OutgoingMessage.to("my-namespace.my-service", "errand.updated")
	.withPayload(event)
	.withMessageId(eventId)
	.withHeader("x-custom", value));
```

Every message gets a message id. The publisher generates one when the service sets none. Set your own when the receiver
must recognize a duplicate, for instance when a publish that timed out is retried: it may have reached the broker the
first time.

What an exception means is up to the service, such as an error to its caller. When a listener publishes, not every
reason is worth trying again:

- `TIMEOUT` and `INTERRUPTED` may pass. The broker may already have the message, so publish it again with the same
  message id.
- `RETURNED` does not pass: no queue is bound for the routing key, and that is a topology error.
- `NACKED` can go either way. The message says why: a missing exchange does not pass, a channel that closed may.

A listener that lets an exception out has its message redelivered at once, and again until the queue's
`x-delivery-limit` dead-letters it. For an error that does not pass, throw `AmqpRejectAndDontRequeueException` to
dead-letter the message straight away.

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

