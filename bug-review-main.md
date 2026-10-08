# dept44 bug review — origin/main (e13d46a, 2026-10-07), outside payload logging

Found by four parallel read-only reviews (starter core; support/util/validators/models; clients and authorization;
scheduler, test kit, build tools, formatter, example), then verified one by one: against the code, the library sources
or bytecode in `~/.m2`, and, where a finding was disputed or depended on runtime behaviour, a test run against the
original code (the test fails on main and passes with the fix). Payload logging (Logbook/logback) is out of scope.

An independent review of the same list (pasted into the session) qualified several claims; those were re-checked, and
the descriptions below use the corrected wording and counts. Fleet counts are taken over `api-service-*` (and `pw-*`
where stated) in `~/Code/scit`, with the method given in each note.

The fixes are in the same change as this document. `mvn verify` of the whole reactor passes: all 18 modules, about
1,240 tests, coverage gates, formatting and checkstyle.

Status values: **Fixed**, **Not fixed** (real, with the reason). Rollout notes call out fixes that change behaviour
services can observe.

## Summary

| ID  | Severity |        Area        |                                               Finding                                               |  Status   |
|-----|----------|--------------------|-----------------------------------------------------------------------------------------------------|-----------|
| C1  | Critical | webservicetemplate | SOAP client trusts any server certificate when a keystore is set                                    | Fixed     |
| C2  | Critical | starter            | dept44's `config/application.properties` overrides services' `application.yml`                      | Fixed     |
| C3  | Critical | authorization      | JWT filter never authenticates (anonymous token already present)                                    | Fixed     |
| C4  | Critical | authorization      | JWT role claims mangled by `toString()` before JSON parsing                                         | Fixed     |
| C5  | Critical | authorization      | JWT filter turns downstream exceptions into 401                                                     | Fixed     |
| H1  | High     | scheduler          | An `Error` thrown by a job leaves health UP                                                         | Fixed     |
| H2  | High     | scheduler          | `maximumExecutionTime` only checked after the job returns                                           | Fixed     |
| H3  | High     | scheduler          | `Duration.parse` in `finally` can skip MDC/RequestId cleanup                                        | Fixed     |
| H4  | High     | test               | `verifyAllStubs` verifies by URL only                                                               | Fixed     |
| H5  | High     | formatting         | Formatter never checks `src/integration-test/java`                                                  | Fixed     |
| H6  | High     | starter            | `HandlerMethodValidationException` answered without violations                                      | Fixed     |
| H7  | High     | starter            | Catch-all `Exception` handler blocks cause-based handlers (504, wrapped problems)                   | Fixed     |
| H8  | High     | starter            | `new HttpHeaders(headers)` mutates the exception's own headers                                      | Fixed     |
| H9  | High     | feign              | OAuth2 `ActionRetryer` retries I/O errors and evicts valid tokens                                   | Fixed     |
| H10 | High     | feign              | WSO2 `invalid_token` 401 reaches callers as HTTP 500                                                | Fixed     |
| H11 | High     | feign              | `HttpStatus.valueOf` crashes the error decoder on non-standard codes                                | Fixed     |
| H12 | High     | feign              | Error decoders read a non-repeatable body twice                                                     | Fixed     |
| H13 | High     | starter            | `Problem.xxx(pattern, args)` uses `MessageFormat` (quotes, number grouping)                         | Fixed     |
| M1  | Medium   | validators         | `@ValidUuid` accepts non-canonical strings such as `1-1-1-1-1`                                      | Fixed     |
| M2  | Medium   | validators         | `@ValidNamespace` allows `\|`, the `Relation` delimiter                                             | Fixed     |
| M3  | Medium   | validators         | `@ValidOrganizationNumber` rejects group 6 (enkla bolag)                                            | Fixed     |
| M4  | Medium   | models             | Empty `sortBy` passes validation, then `sort()` throws (500)                                        | Fixed     |
| M5  | Medium   | models             | `@MaxPagingLimit` throws NPE on a null `Integer`                                                    | Fixed     |
| M6  | Medium   | starter            | `@ValidMunicipalityId` / `MunicipalityUtils` accept county codes                                    | Fixed     |
| M7  | Medium   | starter            | `PiiMasker` masks parts of timestamps and misses `_`-adjacent personal numbers                      | Fixed     |
| M8  | Medium   | starter            | `EncodingUtils` misdetects valid text and corrupts mixed content                                    | Fixed     |
| M9  | Medium   | webclient          | Sub-second read/write timeouts are silently disabled                                                | Fixed     |
| M10 | Medium   | webclient          | OAuth2: no token eviction on 401 and no timeout on token fetches                                    | Fixed     |
| M11 | Medium   | starter            | Truststore pins `TLSv1.2`, disabling TLS 1.3 for outgoing calls                                     | Fixed     |
| M12 | Medium   | starter            | Municipality interceptor blocks unrelated paths when `allowed-ids` is set                           | Fixed     |
| M13 | Medium   | scheduler          | No way to set ShedLock `lockAtLeastFor`                                                             | Fixed     |
| M14 | Medium   | example            | Bidirectional `toString`/`equals`/`hashCode` recursion in entities                                  | Fixed     |
| M15 | Medium   | example            | Image download: raw filename in `Content-Disposition`, null MIME type → 500                         | Fixed     |
| L1  | Low      | test               | `withExtensions` has no effect                                                                      | Fixed     |
| L2  | Low      | test               | Non-JSON responses compared with all whitespace stripped                                            | Fixed     |
| L3  | Low      | formatting         | Markdown include `**/*.md` reaches nested `.claude/worktrees`                                       | Fixed     |
| L4  | Low      | test               | `@Load` drops trailing newline and turns CRLF into LF                                               | Not fixed |
| L5  | Low      | starter            | `Relation` does not escape delimiters; null type does not round-trip                                | Fixed     |
| L6  | Low      | starter            | `KeyStoreUtils.loadKeyStore(String, String)` leaks its input stream                                 | Fixed     |
| L7  | Low      | starter            | `DateUtils` javadoc contradicts the code; DST-gap offset wrong                                      | Fixed     |
| L8  | Low      | starter            | WebFlux request id kept in a ThreadLocal across threads                                             | Fixed     |
| L9  | Low      | starter            | `config/spring.properties` is never read                                                            | Fixed     |
| L10 | Low      | starter            | `causeAsProblem` leaks into every service's OpenAPI schema                                          | Fixed     |
| L11 | Low      | starter            | `ThrowableProblem` / `ConstraintViolationProblemResponse` not deserializable; unknown status throws | Fixed     |
| L12 | Low      | webservicetemplate | Client interceptors kept in a `HashSet` (random order)                                              | Fixed     |
| L13 | Low      | authorization      | `shouldNotFilter` logic inverted; bean scan on every request                                        | Fixed     |
| X1  | Low      | models             | `@ValidSortByProperty` ignores inherited fields; NPE on null parameters                             | Fixed     |
| X2  | Low      | validators         | Personal/organization number validators check format only (no date/Luhn)                            | Not fixed |
| X3  | Low      | build-tools        | Truststore validity mojo fails on non-certificate files (`.gitkeep`)                                | Fixed     |
| X4  | Low      | build-tools        | OpenAPI properties mojo surfaces invalid YAML as a raw exception                                    | Fixed     |

## Rollout: what services will notice

Upgrading to a dept44 with these fixes changes behaviour that services' own tests or configuration may depend on:

- **C2** — values in a service's root `application.yml` now win over dept44's defaults in production (e.g.
  datawarehousereader's Hikari pool of 50). Check that those values are still wanted.
- **C1** — financial-aid (SSBTEK) and digital-mail-sender (Skatteverket) now verify the server certificate and host
  name. Try both against a test environment first; pass `truststore.getTrustManagerFactory()` to the builder if the
  server is only trusted through dept44's truststore.
- **L10** — 61 services' checked-in OpenAPI baselines contain `causeAsProblem` and must be regenerated
  (`dept44-example`'s is updated here).
- **H4** — AppTests with a stub that is never matched now fail.
- **H5** — unformatted integration tests now fail `dept44-formatting:check`; run `mvn dept44-formatting:apply`.
- **H6** — validation errors from controllers without class-level `@Validated` now have title `Constraint Violation`
  and a `violations` list.
- **M3** — the organization number message changed (14 files in 9 services assert the old one).
- **H7, H10** — wrapped timeouts and problems change status (500 to 504, or the problem's own status).
- **H9** — OAuth Feign clients no longer retry I/O errors once.
- **H13** — numbers in formatted problem details are no longer digit-grouped.
- **L2** — non-JSON AppTest responses are compared more strictly.

## Remaining work outside this repository

- `api-service-operaton/operaton-process/.../ProcessService.java:60` passes `'%s'` to `Problem.internalServerError`,
  which uses `{0}` placeholders, so the business key never appears in the message (H13).
- `@EnableWebFlux` on `WebFluxConfiguration`'s nested configuration may make Spring Boot's WebFlux auto-configuration
  back off; flagged by a review agent as unverified, not examined here (L8).

## Findings

### C1 — SOAP client trusts any server certificate when a keystore is set

`dept44-starter-webservicetemplate/.../WebServiceTemplateBuilder.java:299`. With a client keystore, the SSL context is
built with `loadTrustMaterial(keyStore, (_, _) -> true)` and `NoopHostnameVerifier`, so neither the server certificate
nor the host name is checked. A man-in-the-middle can impersonate SSBTEK or Skatteverket (financial-aid,
digital-mail-sender) and read or forge personal data.

**Status:** Fixed

**Verified:** Read the code: with a keystore, `loadTrustMaterial(keyStore, (_, _) -> true)` and `NoopHostnameVerifier.INSTANCE`. A trust strategy returning true makes httpcore5's trust manager accept any chain.

**Fix:** The server chain is now checked against the JVM default trust store (or a `TrustManagerFactory` passed with the new `withTrustManagerFactory(...)`, e.g. `truststore.getTrustManagerFactory()`), or against the certificates in the client keystore — the trust the original `loadTrustMaterial(keyStore, ...)` call asked for. A new `AnyOfTrustManager` combines the two, since JSSE only consults the first trust manager. Host names are verified with the default verifier. Tests: `AnyOfTrustManagerTest`, `WebServiceTemplateBuilderTest`.

**Rollout:** Only financial-aid (SSBTEK) and digital-mail-sender (Skatteverket) build a SOAP client with a keystore. Before deploying, check in a test environment that each server's certificate chains to the JVM trust store or the keystore, and that the URL's host matches the certificate. Note that dept44's `Truststore` replaces the JVM default `SSLContext` but not the `TrustManagerFactory` default; if a server is only trusted through dept44's truststore, pass `truststore.getTrustManagerFactory()` to the builder.

### C2 — dept44 defaults override services' `application.yml`

`dept44-starter/src/main/resources/config/application.properties`. Spring Boot ranks `classpath:/config/` above `classpath:/`, so every key dept44 sets beats the same key in a service's root `application.yml` (or `.properties`). Profile-specific files, environment variables and other external configuration still win, so services that set these values that way are unaffected; values set only in a root `application.yml` (Hikari pool sizes and timeouts in datawarehousereader, casemanagement, memories, snailmail-sender and alkt) were ignored.

**Status:** Fixed

**Verified:** Read the file and Spring Boot's documented search order (`optional:classpath:/;optional:classpath:/config/` — later locations win). The review agent also ran a minimal Boot 4.1.1 application showing the `config/` value winning. The new test `Dept44DefaultPropertiesEnvironmentPostProcessorTest#applicationConfigurationOverridesTheDefaultsInARunningApplication` runs a real `SpringApplication`.

**Fix:** The defaults moved to `dept44-default.properties`, added with the lowest precedence by the new `Dept44DefaultPropertiesEnvironmentPostProcessor` (registered in `META-INF/spring.factories`, runs after config data). Only `spring.config.import=optional:file:.env[.properties]` stays in `config/application.properties`, because an import is only processed from a config data file.

**Rollout:** Values services already set in their root `application.yml` start to apply in production — for example datawarehousereader's Hikari `maximum-pool-size: 50` and `minimum-idle: 10` instead of 10 and 3. Each affected service should confirm its values are still what it wants. A service that lists `spring.autoconfigure.exclude` in its root config would now replace dept44's exclusion of `UserDetailsServiceAutoConfiguration`; none does today outside test profiles (which already replaced it).

### C3 — JWT filter never authenticates

`dept44-starter-authorization/.../JwtAuthorizationExtractionFilter.java:112`. The filter is a plain bean, which Spring Boot registers at the lowest precedence, after Spring Security's filter chain (order -100); by then dept44's `SecurityConfiguration` (default `HttpSecurity`, which includes `anonymous()`) has set an `AnonymousAuthenticationToken`. The filter only set the authentication when it was null. No fleet service uses this starter today.

**Status:** Fixed

**Verified:** Proven in a running server: `JwtAuthorizationIntegrationTest` (real Tomcat, dept44's security chain, the filter bean) returns 200 for a valid token with the fix, and 401 for both a `@PreAuthorize("isAuthenticated() and !isAnonymous()")` and a `hasAuthority('READ')` endpoint when only the original `isNull(...)` check is put back.

**Fix:** An anonymous authentication is now replaced (`null` or `AnonymousAuthenticationToken`), in a new `SecurityContext`. Tests: `JwtAuthorizationIntegrationTest`, `JwtAuthorizationExtractionFilterTest`.

### C4 — JWT role claims mangled

`dept44-starter-authorization/.../util/JwtTokenUtil.java:52`. Claim values are turned back into text with `toString()`
before `JsonPath.parse`: `"A, B"` becomes two accesses, `"007"` becomes `7`, nested objects fail to parse (401).

**Status:** Fixed

**Verified:** Reproduced with json-path directly: `Objects.toString(List.of("A, B", "007"))` is `[A, B, 007]`, which json-smart parses as `["A","B",7]`, so `$[?(@ == 'B')]` matches. With the original code, `JwtTokenUtilTest#getRolesKeepsAccessValuesIntact` fails with `InvalidJsonException` on `"x]y"` (a 401 for the whole request).

**Fix:** Role accesses are serialized with Jackson before json-path parses them. Test: `JwtTokenUtilTest#getRolesKeepsAccessValuesIntact` (signed token with `"A, B"`, `"007"`, `"x]y"` and a nested object).

### C5 — JWT filter turns downstream exceptions into 401

`JwtAuthorizationExtractionFilter.java:106-132`. `chain.doFilter` sits inside the `try`, so any exception from the rest
of the chain is answered as a 401 "credentials" problem.

**Status:** Fixed

**Verified:** Read the code: `chain.doFilter` sat inside the `try` whose `catch (Exception)` answers 401.

**Fix:** `chain.doFilter` moved after the `try`; only token reading is answered with 401. Test: `JwtAuthorizationExtractionFilterTest#doFilterInternalLetsExceptionsFromTheRestOfTheChainThrough`.

### H1 — An `Error` thrown by a job leaves health UP

`dept44-starter-scheduler/.../Dept44SchedulerAspect.java:134`. Only `Exception` is caught; after `resetErrors()` the
`finally` block calls `setHealthy()` for an `Error`.

**Status:** Fixed

**Verified:** Read the aspect: only `Exception` is caught; for an `Error`, `finally` finds no errors (reset at the start) and calls `setHealthy()`. Confirmed by the pasted review.

**Fix:** The aspect records whether the run reached an outcome; if not (an `Error` was thrown), `finally` marks the run unhealthy and logs a FAILURE, and the `Error` propagates to Spring's scheduler (not swallowed). Test: `Dept44SchedulerAspectTest#testAroundScheduledMethodError`.

### H2 — `maximumExecutionTime` only checked after the job returns

`Dept44SchedulerAspect.java:138-146`. A hung job never makes health unhealthy.

**Status:** Fixed

**Verified:** Read the aspect: the duration check is in `finally`, after the job returns. Confirmed by the pasted review.

**Fix:** `Dept44HealthIndicator` records when a run starts and its maximum execution time; `health()` reports RESTRICTED ("Maximum execution time exceeded, still running since ...") while a run is still going past its maximum. Tests in `Dept44HealthIndicatorTest`.

### H3 — `Duration.parse` in `finally`

`Dept44SchedulerAspect.java:120, 141`. A value ShedLock accepts (`5m`) or an unresolved placeholder throws inside
`finally`, replacing the original exception and skipping MDC and `RequestId.reset()`; the scheduler thread then reuses
one stale request id.

**Status:** Fixed

**Verified:** Read the aspect and `RequestId`: `Duration.parse` in `finally` throws for `5m` or an unresolved placeholder, before `RequestId.reset()`; `RequestId.init` only creates a new id when the thread's counter is 0. Confirmed by the pasted review.

**Fix:** The maximum is parsed before the run with Spring Boot's `DurationStyle` (ISO-8601 and the simple `5m` form ShedLock accepts); an invalid value logs a warning and uses the 2-minute default. Tests: `Dept44SchedulerAspectMaximumExecutionTimeTest`, `Dept44SchedulerAspectTest#testAroundScheduledMethodWithInvalidMaximumExecutionTime`.

### H4 — `verifyAllStubs` verifies by URL only

`dept44-starter-test/.../AbstractAppTest.java:642`. Each stub was verified with `anyRequestedFor(url)`, ignoring method, query, headers and body, so a stub that was never matched passed when another stub for the same URL was. `verifyAllStubs()` (also called by `verifyStubs()` and `sendRequestAndVerifyResponse()`) is called directly in 40 test files across 11 services.

**Status:** Fixed

**Verified:** Read the code (also confirmed by the pasted review). New real-WireMock test: a `POST` stub for a URL that only a `GET` requested passed the old check.

**Fix:** A stub counts as called when a matched request was served by a stub with the same request pattern and scenario state, so method, query, headers and body count. Stubs are compared by what they match rather than by id, because each `setupCall()` loads the stub files again and a stub without an explicit id gets a new one every time (the first version of this fix compared ids and failed `dept44-example`'s `PetInventoryCircuitBreakerIT` for exactly that reason). Tests in `AbstractAppTestTest`, including `testVerifyAllStubsTreatsAStubLoadedAgainAsTheSameStub`.

**Rollout:** AppTests with a stub that is never matched (for example a second stub for the same URL that only differs by method, body or query, or a fallback stub) will now fail; remove or fix such stubs.

### H5 — Formatter never checks `src/integration-test/java`

`dept44-formatting-plugin/src/main/resources/config.xml`. The java block has no `<includes>`, and Spotless's default covers only the main and test source directories, so `src/integration-test/java` (433 Java files under `api-service-*/src/integration-test/java`) is never checked.

**Status:** Fixed

**Verified:** The review agent checked Spotless's default includes with `javap`; confirmed by the pasted review.

**Fix:** Explicit includes for `src/main/java`, `src/test/java` and `src/integration-test/java`. Test: `FormattingConfigurationTest`.

**Rollout:** Services whose integration tests are not formatted will fail `dept44-formatting:check`; run `mvn dept44-formatting:apply` once. dept44's own `dept44-example` had nine unformatted integration test files (import order and indentation), reformatted here.

### H6 — Method validation errors lose their violations

`dept44-starter/.../problem/ProblemExceptionHandler.java:76`. For controllers without class-level `@Validated`, Spring
throws `HandlerMethodValidationException`; the parent handler answers 400 "Validation failure" without `violations`.

**Status:** Fixed

**Verified:** `ProblemExceptionHandlerMvcTest` runs the handler in Spring MVC with a controller without `@Validated`: with the original handler the response is 400 "Bad Request" without violations, for both a constrained path variable and a `@Valid` object.

**Fix:** `handleHandlerMethodValidationException` is overridden to answer a `ConstraintViolationProblem`. Parameter violations take their field name from the underlying `ConstraintViolation` (`method.parameter`, as for `@Validated`), violations inside a `@Valid` object their field name.

**Rollout:** Services whose controllers lack class-level `@Validated` now get `title: Constraint Violation` and a `violations` list instead of `title: Bad Request`; their failure tests asserting the old body need updating.

### H7 — Catch-all handler blocks cause-based handlers

`ProblemExceptionHandler.java:222-242`. Spring only matches an exception's cause when nothing matches the exception
itself, and `Exception.class` always matches. Wrapped timeouts give 500 instead of 504; a wrapped `ThrowableProblem`
gives 500 instead of its own status.

**Status:** Fixed

**Verified:** `ProblemExceptionHandlerMvcTest`: with the original handler, an exception wrapping a `SocketTimeoutException` gives 500 (not 504), and a `CompletionException` wrapping a 404 problem gives 500.

**Fix:** The catch-all handler looks down the cause chain (cycle-safe) for a `ThrowableProblem` (answered with its own status, headers and body), a `SocketTimeoutException` (504) or a `CallNotPermittedException` (503) before answering 500. This also fixes H10.

**Rollout:** Wrapped timeouts and problems change status (500 to 504, or to the problem's status).

### H8 — Handler mutates the exception's own headers

`ProblemExceptionHandler.java:81-85`. In Spring 7, `new HttpHeaders(headers)` wraps the same map (only `EMPTY` gets a
new one); `setContentType` writes into a possibly shared, static problem's headers.

**Status:** Fixed

**Verified:** Spring Web 7.0.9 source: `new HttpHeaders(HttpHeaders)` keeps the same map (`unwrap`) unless the argument is `EMPTY`; `HttpHeaders.copyOf` copies.

**Fix:** `HttpHeaders.copyOf(headers)`. Test: `ProblemExceptionHandlerTest#handlingAProblemLeavesItsOwnHeadersUntouched`.

### H9 — OAuth2 retryer retries I/O errors

`dept44-starter-feign/.../retryer/ActionRetryer.java:23-31`, wired by `FeignMultiCustomizer.withRetryableOAuth2Interceptor*` and used directly by digital-mail-sender, digital-registered-letter and pw-parking-permit (all for OAuth token refresh). Every `RetryableException` triggered a retry and the token removal, including the ones Feign makes from I/O errors (status -1) and from a `Retry-After` response: a timed-out POST that the server processed is sent twice, and a still-valid token is evicted. Without an OAuth customizer the Spring Cloud OpenFeign default is never to retry.

**Status:** Fixed

**Verified:** Feign 13.6.1 bytecode: `FeignException.errorExecuting` builds `new RetryableException(-1, ...)`; `SynchronousMethodHandler` hands every `RetryableException` to the retryer.

**Fix:** `ActionRetryer` acts and retries only for a rejected token (status 401, which is what `WSO2RetryResponseVerifier` produces); anything else is propagated at once. Tests: `ActionRetryerTest#failuresOtherThanARejectedTokenAreNotRetried`.

**Rollout:** OAuth clients no longer retry I/O errors once. A service that relied on that implicit single retry for flaky connections should add an explicit retry policy.

### H10 — WSO2 `invalid_token` reaches callers as 500

`dept44-starter-feign/.../decoder/AbstractErrorDecoder.java:84-92`. The decoder returns a `RetryableException`; without
a retryer, or after the retry, Feign rethrows it and the service answers 500.

**Status:** Fixed

**Verified:** `ErrorDecoderEdgeCasesTest#rejectedTokenThatIsNotRetriedReachesTheCallerAsTheMappedProblem`: the decoder returns a `RetryableException` whose cause is the mapped problem; on the original handler such a wrapped problem gave 500 (H7).

**Fix:** Fixed by H7: the catch-all handler unwraps the cause, so the caller gets the mapped problem's status (502, or 401 when it is a bypass code).

### H11 — `HttpStatus.valueOf` on non-standard codes

`AbstractErrorDecoder.java` (`mapToProblem`, `ErrorMessage.create`) and `ProblemErrorDecoder.java:108, 160`. `HttpStatus.valueOf` throws for codes without a constant (499, 520-527, 598...). Every message path ends in `ErrorMessage.create`, including the last-resort fallback, which is outside any catch, so `decode()` throws for such a status with or without a body (for decoders extending `AbstractErrorDecoder`).

**Status:** Fixed

**Verified:** `ErrorDecoderEdgeCasesTest#statusWithoutAnHttpStatusConstantGivesAProblem` on the original code: `IllegalArgumentException: No matching constant for [499]` (also 520, 598), without a body.

**Fix:** `HttpStatus.resolve` with an "Unknown Status" reason phrase; a bypass code without a constant falls back to 502.

### H12 — Error body read twice

`AbstractErrorDecoder.java:125` then `ProblemErrorDecoder.java:80` / `JsonPathErrorDecoder.java:78`. Fails for
non-repeatable bodies (logger level `NONE`).

**Status:** Fixed

**Verified:** `ErrorDecoderEdgeCasesTest#bodyThatCanOnlyBeReadOnceIsStillUsedForTheMessage` on the original code: the detail is lost (the blank check consumed the stream).

**Fix:** `decode` makes a non-repeatable body repeatable first, reading at most `MAX_ERROR_BODY_SIZE` bytes.

### H13 — `Problem.xxx(pattern, args)` use `MessageFormat`

`dept44-starter/.../problem/Problem.java:82, 113, 145, 177`. Apostrophes quote (`'{0}'` stays literal), numbers get
locale grouping (`1 234 567`).

**Status:** Fixed

**Verified:** JDK `MessageFormat`: `'{0}'` stays literal and numbers are grouped by locale.

**Fix:** A `formatDetail` helper doubles apostrophes and inserts each parameter's `toString()`, keeping `{0}` placeholders. Test: `ProblemTest#detailParametersKeepApostrophesAndAreNotLocaleFormatted` (sv-SE locale).

**Rollout:** Numbers in formatted details lose their grouping (`1234567` instead of `1,234,567`). Not fixed here: `api-service-operaton/operaton-process/.../ProcessService.java:60` calls `Problem.internalServerError("... '%s'", key)` with a `%s` placeholder, so the key never appears; that call site must use `{0}`.

### M1 — `@ValidUuid` is lenient

`dept44-common-validators/.../ValidUuidConstraintValidator.java:48`. `UUID.fromString` accepts `1-1-1-1-1`.

**Status:** Fixed

**Verified:** JDK 25: `UUID.fromString("1-1-1-1-1")` returns `00000001-0001-0001-0001-000000000001`.

**Fix:** The canonical 8-4-4-4-12 hexadecimal form is required (`ValidUuidConstraintValidatorTest`).

### M2 — `@ValidNamespace` allows `|`

`ValidNamespaceConstraintValidator.java:15`. `[\w|\-]` — the pipe is a literal inside a character class. A validated
namespace breaks `Relation.parseRelation`.

**Status:** Fixed

**Verified:** Read the regex `[\w|\-]{2,32}`; the test asserted that `my|namespace` is valid.

**Fix:** `[\w\-]{2,32}` (letters, digits, `_` and `-`, as the message says); the test now asserts pipes are rejected.

### M3 — `@ValidOrganizationNumber` rejects group 6

`ValidOrganizationNumberConstraintValidator.java:18`. Leading digit `[1235789]` excludes 6 (enkla bolag).

**Status:** Fixed

**Verified:** Read the regex `[1235789]...`; the test expected `6021112233` to be invalid. Group 6 (enkla bolag) is a real organization number group.

**Fix:** `^([1235-9][\d][2-9]\d{7})$`; message and javadoc updated.

**Rollout:** The default message contains the regular expression; 14 files in 9 services (`api-service-*`, `pw-*`) contain the old one, mostly failure tests asserting the message, and must be updated.

### M4 — Empty `sortBy` gives 500

`dept44-models/.../AbstractParameterPagingAndSortingBase.java:40-44`. `?sortBy=` binds to an empty list, which passes `@ValidSortByProperty`, and `sort()` then calls `Sort.by(direction, new String[0])`, which throws "At least one property must be given". Callers that pass `sort()` to `PageRequest.of` (e.g. datawarehousereader, checklist) then answer 500.

**Status:** Fixed

**Verified:** Read the code; the review agent ran `Sort.by` with an empty array against spring-data-commons 4.0.5.

**Fix:** An empty `sortBy` gives `Sort.unsorted()` (`AbstractParameterPagingAndSortingBaseTest#isUnsortedWithEmptySortBy`).

### M5 — `@MaxPagingLimit` NPE on null

`dept44-models/.../MaxPagingLimitConstraintValidator.java:17`. `value <= maxLimit` unboxes null.

**Status:** Fixed

**Verified:** Read the code: `value <= maxLimit` unboxes the `Integer`; the annotation targets parameters and type uses, where the value can be null.

**Fix:** Null is valid, as for the built-in constraints (`MaxPagingLimitImplTest#isValidWithoutLimit`).

### M6 — County codes accepted as municipality ids

`dept44-starter/src/main/resources/data/municipality.yml`, `MunicipalityUtils.java:27`. The 21 county (län) codes are
loaded into the same map as the 290 municipalities.

**Status:** Fixed

**Verified:** Read `municipality.yml`: 290 four-digit municipality codes and 21 two-digit county codes, all loaded into the same map; `existsById("22")` is true. No fleet code calls `MunicipalityUtils` directly; it is reached through `@ValidMunicipalityId`.

**Fix:** Only four-digit codes are loaded (the data file is unchanged). `MunicipalityUtilsTest#countiesAreNotMunicipalities`.

**Rollout:** A county code is no longer a valid municipality id. No fleet request or test uses a two-digit municipality id.

### M7 — `PiiMasker` over- and under-masks

`dept44-starter/.../util/PiiMasker.java:49, 58`. `2024-03-08 09:15:22` → `2024-**-** **:15:22`;
`beslut_199001011234.pdf` is left unmasked.

**Status:** Fixed

**Verified:** Pattern check: `2024-03-08 09:15:22` became `2024-**-** **:15:22` and `beslut_199001011234.pdf` was left unmasked (the pasted review reproduced both).

**Fix:** Personal numbers are bounded by "no letter or digit" instead of `\b`, so `_` separates while hex tokens are still left alone. A phone number may not start right after a digit and a date separator, nor end before a time/decimal separator and a digit. New cases in `PiiMaskerTest`.

**Rollout:** Not covered: the space-separated personal number form `19900101 1234` is still not masked (matching it would also mask ordinary number pairs).

### M8 — `EncodingUtils` misdetection

`dept44-starter/.../util/EncodingUtils.java:23, 43-45`. Valid text (e.g. Cyrillic) is detected as double encoded, and
the repair destroys non-Latin-1 characters.

**Status:** Fixed

**Verified:** Ran the original logic: `«яблоку»` was "fixed" to `�??????�`, `Сказку\u00a0читай` to question marks, and `Ålder: 3 år; Ã\u0085sa` lost its correct `Å` (the byte heuristic matches any character ending in 0x83 before U+0080–U+00BF).

**Fix:** Each double-encoded character (an ISO-8859-1 lead character followed by the continuation characters its lead byte announces) is repaired on its own, and only when it decodes as strictly valid UTF-8. Correctly encoded text, including non-Latin scripts, is left as it is, and mixed text is repaired correctly. New cases in `EncodingUtilsTest`, including 3- and 4-byte characters.

**Rollout:** `isDoubleEncodedUTF8Content` now also detects double-encoded characters other than the å/ä/ö family (e.g. `â‚¬` for €).

### M9 — WebClient sub-second timeouts disabled

`dept44-starter-webclient/.../WebClientBuilder.java:248-249`. `toSeconds()` truncates 500 ms to 0, which Netty treats
as "no timeout".

**Status:** Fixed

**Verified:** `WebClientBuilderTest#testSubSecondReadTimeoutIsApplied` against a server that never answers: with the original `toSeconds()` a 300 ms read timeout never fires (the call is still waiting after 5 s).

**Fix:** `ReadTimeoutHandler`/`WriteTimeoutHandler` get milliseconds.

### M10 — WebClient OAuth2 safeguards missing

`WebClientBuilder.java:233-240`. No eviction of a rejected token, and the default token client has no timeout.

**Status:** Fixed

**Verified:** Spring Security 7.1.1 bytecode: the manager-only constructor of `ServerOAuth2AuthorizedClientExchangeFilterFunction` installs a response handler that returns the response unchanged (`aload_1; areturn`), so nothing evicts a rejected token; `ClientCredentialsReactiveOAuth2AuthorizedClientProvider` defaults to a `WebClientReactiveClientCredentialsTokenResponseClient` with a plain `WebClient` (no timeouts).

**Fix:** `setAuthorizationFailureHandler(new RemoveAuthorizedClientReactiveOAuth2AuthorizationFailureHandler(...))` removes the rejected client from the client service; the token client uses a connector with the builder's timeouts, without payload logging (the token request body holds the client secret). `WebClientBuilderTest#testBuildFromCustomValuesWithOAuth2` checks the failure handler is installed.

### M11 — Truststore pins TLS 1.2

`dept44-starter/.../security/Truststore.java:87`. `SSLContext.getInstance("TLSv1.2")` enables only TLS 1.2 on client
sockets, and that context becomes the JVM default.

**Status:** Fixed

**Verified:** Agent JDK probe and the pasted review: an `SSLContext.getInstance("TLSv1.2")` context enables only TLSv1.2 for client sockets; it becomes the JVM default.

**Fix:** `SSLContext.getInstance("TLS")` (TLS 1.3 and 1.2). `TruststoreTest` asserts both are offered.

### M12 — Municipality interceptor blocks unrelated paths

`dept44-starter/.../configuration/WebConfiguration.java:126-156`. With `municipality.allowed-ids` set, the interceptor's patterns cover every MVC path, and it reads a fixed index of the raw URI (shared with the MDC filter's `mdc.municipalityId.uriIndex`). Paths without a municipality at that index, such as `/api-docs`, are rejected with 501, or fail with `ArrayIndexOutOfBoundsException` when shorter. No fleet repository sets `allowed-ids`, so it may only be set through deployment configuration.

**Status:** Fixed

**Verified:** Read the code: `pathArray[municipalityIdUriIndex]` on any path, with no length check.

**Fix:** The interceptor checks the handler's `{municipalityId}` path variable (from `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`) and lets requests without one through. Tests in `WebConfigurationTest$MunicipalityIdInterceptorTest`.

**Rollout:** Endpoints whose municipality path variable is named something other than `municipalityId` are no longer checked.

### M13 — No `lockAtLeastFor`

`dept44-starter-scheduler/.../Dept44Scheduled.java`, `SchedulingConfiguration.java`. There is no `lockAtLeastFor` alias or default, so the lock is released as soon as a short task finishes; whether a second pod then runs it again in the same period depends on timing (clock skew, a busy single scheduler thread) and deployment. 76 `@Dept44Scheduled(` uses in `api-service-*/src/main`.

**Status:** Fixed

**Verified:** ShedLock defaults `lockAtLeastFor` to zero; `@Dept44Scheduled` had no way to set it.

**Fix:** `lockAtLeastFor` alias added (`Dept44ScheduledTest`). No default is set: a safe minimum depends on each task's interval.

**Rollout:** Services must set `lockAtLeastFor` on their tasks (somewhat less than the shortest interval) for this to have any effect.

### M14 — Example entity recursion

`dept44-example/.../PetNameEntity.java`, `PetImageEntity.java`. `PetNameEntity` includes its images and `PetImageEntity` its pet in `toString`, `equals` and `hashCode`, so a pet with an image whose back reference is set recurses until the stack overflows; `PetImageEntity.toString` also prints the whole image `byte[]`. The example is copied by services.

**Status:** Fixed

**Verified:** Read the code.

**Fix:** `PetImageEntity` leaves the `petName` back reference out of `equals`, `hashCode` and `toString` (which prints the pet's id and the content's size instead). Test: `PetImageEntityTest#petWithImagesCanBeComparedAndPrinted`.

### M15 — Example image download

`dept44-example/.../PetInventoryResource.java:102-104`. Raw filename in `Content-Disposition`; null MIME type → 500.

**Status:** Fixed

**Verified:** Read the code: `"attachment; filename=\"%s\""` with the client-supplied name, and `MediaType.parseMediaType(null)` for an image stored without a MIME type.

**Fix:** `ContentDisposition.attachment().filename(name, UTF_8)` (quoted and RFC 5987 encoded); `application/octet-stream` when no MIME type is stored. Test: `PetInventoryResourceTest#getPetImageWithClientSuppliedFileNameAndNoMimeType`.

### L1 — `withExtensions` has no effect

`AbstractAppTest.java:184-187`. Extensions are added to the configuration after the WireMock server was built.

**Status:** Fixed

**Verified:** Proven with a running WireMock server: a global response transformer added through `getOptions()` after start is never applied (the stub's own body is returned). No fleet caller.

**Fix:** `withExtensions` now throws `UnsupportedOperationException` explaining that extensions must be registered when the server is created, and is deprecated for removal.

### L2 — Whitespace-stripped comparison

`AbstractAppTest.java:426-432`. `isEqualToIgnoringWhitespace` makes `Anna Svensson` equal `AnnaSvensson`.

**Status:** Fixed

**Verified:** Read the code: `isEqualToIgnoringWhitespace` strips all whitespace (confirmed by the pasted review).

**Fix:** XML responses (application/xml, text/xml, `+xml`) are compared with XMLUnit for similarity, ignoring formatting whitespace and comments; other text with `isEqualToNormalizingWhitespace` (runs of whitespace count as one space). Test: `AbstractAppTestTest#testNonJsonResponsesAreComparedWithoutIgnoringAllWhitespace`.

**Rollout:** A non-JSON expected response that only matched because all whitespace was ignored now fails (56 `.xml` and 12 `.txt` response/expected fixtures exist under `api-service-*`/`pw-*` `__files`, some of them stub bodies rather than expected responses).

### L3 — Markdown include reaches nested worktrees

`config.xml:43-49`. Only `**/target/**` is excluded.

**Status:** Fixed

**Fix:** `**/.claude/**` added to the markdown excludes. Test: `FormattingConfigurationTest`.

### L4 — `@Load` alters strings

`dept44-starter-test/.../ResourceLoaderExtension.java:104-108`. `lines()` joined with `\n` drops the trailing newline
and CRLF.

**Status:** Not fixed

**Verified:** Read the code: lines are joined with `\n`, so CRLF becomes LF and a final line break is dropped (confirmed by the pasted review).

**Fix:** The behaviour is kept and documented on `@Load`: fixtures such as tokens and ids are loaded with `@Load` and passed on as they are (e.g. `valid_jwt.txt` in the authorization module), and keeping a final line break would break such tests across the fleet. A fixture whose exact line endings matter must be read directly.

### L5 — `Relation` round trip

`dept44-starter/.../support/Relation.java`. Values containing `;` or `|` serialize to unparseable strings; a null type
comes back as `""`.

**Status:** Fixed

**Verified:** Agent ran the real class: a `;` in a resource id serializes, then fails to parse; a null type comes back as `""`.

**Fix:** `toRelationString()` refuses values containing `|` or `;` with an `IllegalArgumentException` (the string format is shared between services, so escaping would break older parsers); an empty type section parses to `null`. Tests in `RelationTest`.

### L6 — `KeyStoreUtils` stream leak

`dept44-starter/.../util/KeyStoreUtils.java:61`.

**Status:** Fixed

**Verified:** Read the code: the `String` overload never closed the stream (the other two used try-with-resources).

**Fix:** Delegates to the `Resource` overload, which closes the stream.

### L7 — `DateUtils`

`dept44-starter/.../util/DateUtils.java:31-35, 73`. Javadoc example contradicts the code; a time in a DST gap gets an
offset that is not the local offset at that instant.

**Status:** Fixed

**Verified:** JDK: `ZoneRules.getOffset(LocalDateTime)` returns the pre-transition offset in a gap, giving `2021-03-28T02:30+01:00`, an offset Stockholm does not have at that instant. The javadoc examples for `LocalDateTime` contradicted the code and its test.

**Fix:** `localDateTime.atZone(zone).toOffsetDateTime()` (same result outside gaps; `03:30+02:00` in the gap, the same instant). Javadoc corrected. Gap and overlap cases in `DateUtilsTest`.

### L8 — WebFlux request id

`dept44-starter/.../configuration/WebFluxConfiguration.java:36-46`. `RequestId.init` and `reset` can run on different
threads.

**Status:** Fixed

**Verified:** Read `RequestId`: `init` only creates an id when the thread's counter is 0, so a second request on the same event loop before the first completes reuses the first id, and a `doFinally` on another thread leaves the counter above 0 for good. Latent: no reactive service in the fleet.

**Fix:** The WebFlux filter takes the id from the request header or generates one per request, sets the response header and writes it to the Reactor context (`RequestId.CONTEXT_KEY`), without thread-bound state. `RequestIdExchangeFilterFunction` (WebClient) reads it from the Reactor context, falling back to `RequestId.get()`. Tests: `WebFluxConfigurationTest#requestsHandledOnTheSameThreadGetTheirOwnRequestIds`, `RequestIdExchangeFilterFunctionTest`.

**Rollout:** Not examined: whether `@EnableWebFlux` on the nested configuration makes Spring Boot's WebFlux auto-configuration back off (the agent flagged it as unverified).

### L9 — `config/spring.properties` never read

`dept44-starter/src/main/resources/config/spring.properties`. Spring only reads `spring.properties` from the classpath
root.

**Status:** Fixed

**Verified:** Spring's `SpringProperties` reads `spring.properties` from the classpath root only; the file was under `config/` (confirmed by the pasted review). `config/bootstrap.properties`, next to it, is read: dept44-starter depends on `spring-cloud-starter-bootstrap`.

**Fix:** The dead file is removed. Moving it to the root instead would, for the first time, cap every service's test context cache at 1 — a fleet-wide change to test speed that was never in effect, so it is not done here.

### L10 — `causeAsProblem` in OpenAPI

`dept44-starter/.../problem/ThrowableProblem.java:141-144`. The getter is not ignored, so every service's schema shows a
property real responses never contain.

**Status:** Fixed

**Verified:** `ProblemDeserializationTest#throwableProblemSerializesWithoutCauseAsProblem` fails on the original code.

**Fix:** `@JsonIgnore` on `getCauseAsProblem()`.

**Rollout:** 61 services' checked-in OpenAPI specs contain `causeAsProblem` (counted as `openapi*.y*ml` files under `api-service-*/src`); their OpenAPI contract tests will fail until the baseline is regenerated.

### L11 — Problem deserialization

`ThrowableProblem` has a `@JsonCreator`, but inherits `@JsonDeserialize(as = ProblemResponse.class)` from `Problem`, so Jackson never reaches it; `ConstraintViolationProblemResponse` has no creator; status codes without an `HttpStatus` constant throw in every creator and in the handler's `toProblemResponse`.

**Status:** Fixed

**Verified:** `ProblemDeserializationTest` on the original code: "Class ProblemResponse not subtype of ThrowableProblem", "no Creators" for `ConstraintViolationProblemResponse`, and "No matching constant for [499]".

**Fix:** `@JsonDeserialize` on `ThrowableProblem` (as `ConstraintViolationProblem` already had), a `@JsonCreator` on `ConstraintViolationProblemResponse`, and `HttpStatus.resolve` instead of `valueOf` (an unknown code gives no status instead of an exception).

### L12 — SOAP interceptor order

`WebServiceTemplateBuilder.java:56, 203, 236`. A `HashSet` gives a random interceptor order.

**Status:** Fixed

**Verified:** Read the code: `new HashSet<>()` for client interceptors.

**Fix:** `LinkedHashSet`: interceptors run in the order they are added. Test: `WebServiceTemplateBuilderTest#testClientInterceptorsKeepTheOrderTheyWereAddedIn` (20 interceptors).

### L13 — `shouldNotFilter` inverted

`JwtAuthorizationExtractionFilter.java:75-83`. Skips the filter when `@EnableJwtAuthorization` is present; masked today
because the annotation is not found on the CGLIB proxy. Scans beans on every request.

**Status:** Fixed

**Verified:** Read the code and test: returns true (skip) when the annotation is found; the test asserted that. In a running application the CGLIB proxy hides the non-`@Inherited` annotation, so the filter always ran.

**Fix:** The override is removed. The filter bean only exists when `@EnableJwtAuthorization` imports its configuration, so the check was redundant (and would wrongly skip the filter when the annotation sits on a configuration class other than the application). This also removes a bean scan per request and the filter's `ApplicationContext` dependency (constructor now has four parameters).

### X1 — `@ValidSortByProperty`

`dept44-models/.../ValidSortByPropertyConstraintValidator.java`. Only `getDeclaredFields()`; NPE on null parameters.

**Status:** Fixed

**Verified:** Read the code: only `getDeclaredFields()` of the entity class; `parameters.getSortBy()` on null parameters.

**Fix:** `@Column` fields of superclasses (such as a `@MappedSuperclass`) are included; null parameters are valid. Tests in `ValidSortByPropertyConstraintValidatorTest`.

### X2 — Personal/organization number validators check format only

No date or Luhn check digit validation.

**Status:** Not fixed

**Verified:** Read the validators: format only (`199013451234` passes).

**Fix:** Not changed: this is how the validators are specified (a format check), and adding a date and Luhn check would reject the made-up numbers that fleet test data and fixtures use throughout. A stricter validator would be a separate, opt-in constraint.

### X3 — Truststore validity mojo

`dept44-build-tools/.../CheckTruststoreValidityMojo.java`. Fails on non-certificate files the runtime skips.

**Status:** Fixed

**Verified:** Read the mojo: `generateCertificate` on every file, and its `CertificateException` fails the build (the runtime `Truststore` logs and skips such files).

**Fix:** Files that are not X.509 certificates are skipped with a warning. Test: `CheckTruststoreValidityMojoTest#executeSkipsFilesThatAreNotCertificates`.

### X4 — OpenAPI properties mojo

`dept44-build-tools/.../CheckOpenApiPropertiesMojo.java`. Invalid YAML surfaces as a raw unchecked exception.

**Status:** Fixed

**Verified:** Jackson 3's `JacksonException` is unchecked, so the mojo's `catch (IOException)` missed it.

**Fix:** Both are caught and reported as a `MojoFailureException` naming the file. Test: `CheckOpenApiPropertiesMojoTest#executeWithInvalidYaml`.
