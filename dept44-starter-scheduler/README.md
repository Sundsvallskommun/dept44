# Dept44 Scheduler Starter

A Spring Boot library that streamlines scheduled task management by offering robust exception handling, automated health
checks, and schedlock integration.

## Prerequisites

- Java 25
- Maven 3.9.9+
- Spring Boot 4.x

## Installation

Include the dependency in your `pom.xml`:

```xml

<dependency>
	<groupId>se.sundsvall.dept44</groupId>
	<artifactId>dept44-starter-scheduler</artifactId>
</dependency>
```

## Features

1. **Automatic Health Tracking**  
   Each scheduled task is registered with a dedicated health indicator. This allows real-time monitoring of task status
   via the `/actuator/health` endpoint.

2. **Centralized Exception Handling**  
   Exceptions thrown during task execution are logged and set the task's health status to "RESTRICTED". So does an
   `Error` (such as `OutOfMemoryError`), which is logged and then passed on.

3. **ShedLock Integration**  
   Ensures that only one instance of a task runs at a time across distributed systems.

4. **Unique Request ID Assignment**  
   Each task execution is assigned a unique `RequestId` for improved traceability in logs and monitoring tools.

5. **Customizable Configurations**  
   Scheduling, locking, and execution time limits are all configurable via properties or YAML files.

6. **Execution Time Limit**  
   A run that takes longer than `maximumExecutionTime` sets the health status to "RESTRICTED", already while it is still
   running. The limit is an ISO-8601 duration (`PT2M`) or the simple form also used for the lock (`2m`); an invalid value
   falls back to two minutes and is logged as a warning on every run.

7. **Manual Health Management**  
   Allows for custom handling of exceptions within tasks while still enabling health status updates programmatically.

## Usage

1. **Create Scheduled Tasks**  
   Annotate a method with `@Dept44Scheduled` to handle exceptions, enforce locking, and monitor task durations:

   ```java
   @Component
   public class ScheduledTasks {
       @Dept44Scheduled(
           cron = "${scheduler.scheduled-task.cron}",
           name = "${scheduler.scheduled-task.name}",
           lockAtMostFor = "${schedulers.scheduled-task.shedlock-lock-at-most-for}",
           lockAtLeastFor = "${schedulers.scheduled-task.shedlock-lock-at-least-for}",
           maximumExecutionTime = "${scheduler.scheduled-task.maximum-execution-time}"
       )
       public void scheduledTask() {
           // Task logic
       }
   }
   ```
2. **Add database table for shedlock**  
   Add the following table to your database as shedlock requires it to work properly:

   ```sql
   create table shedlock
   (
       name       varchar(64)  not null,
       lock_until timestamp(3) not null,
       locked_at  timestamp(3) not null default current_timestamp(3),
       locked_by  varchar(255) not null,
       primary key (name)
   );
   ```
3. **Manually Adjust Health**  
   Should you require customized exception handling, manually set the health status:

   ```java
   @Component
   public class ScheduledTasks {
       private final Dept44HealthUtility dept44HealthUtility;

       public ScheduledTasks(final Dept44HealthUtility dept44HealthUtility) {
           this.dept44HealthUtility = dept44HealthUtility;
       }

       @Dept44Scheduled(
           cron = "${scheduler.scheduled-task.cron}",
           name = "${scheduler.scheduled-task.name}",
           lockAtMostFor = "${schedulers.scheduled-task.shedlock-lock-at-most-for}",
           maximumExecutionTime = "${scheduler.scheduled-task.maximum-execution-time}"
       )
       public void scheduledTask() {
           try {
               // Task logic
           } catch (final Exception e) {
               dept44HealthUtility.setHealthIndicatorUnhealthy("ScheduledTask", e.getMessage());
           }
       }
   }
   ```

## Configuration

### application.properties

```properties
# Task name
scheduler.scheduled-task.name=ScheduledTask
# Run every 5 minutes
scheduler.scheduled-task.cron=0 0/5 * * * ?
# Lock at most for 2 minutes
schedulers.scheduled-task.shedlock-lock-at-most-for=PT2M
# Optional: keep the lock for at least 1 minute, so that a pod whose trigger fires a little later cannot run the same
# schedule again. It must not be longer than lock-at-most-for (two minutes unless set), or the application fails to
# start; raise lock-at-most-for first if needed.
schedulers.scheduled-task.shedlock-lock-at-least-for=PT1M
# Limit execution time to 2 minutes; a run that takes longer marks the task as unhealthy, also while it is still running
scheduler.scheduled-task.maximum-execution-time=PT2M
```

### application.yml

```yaml
scheduler:
  scheduled-task:
    name: "ScheduledTask"
    cron: "0 0/5 * * * ?"
    shedlock-lock-at-most-for: "PT2M"
    shedlock-lock-at-least-for: "PT1M"
    maximum-execution-time: "PT2M"
```

## Contributing

Contributions are appreciated.
See [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for details.

## License

Released under the [MIT License](https://github.com/Sundsvallskommun/.github/blob/main/LICENSE).  
© 2024 Sundsvalls kommun
