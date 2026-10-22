# Dept44 Benchmarks

[JMH](https://github.com/openjdk/jmh) benchmarks for dept44. The module is built with the rest of the project, so the
benchmarks keep compiling as the code changes. It is never published, and the benchmarks only run when started by hand.

## Running

Build the module and the modules it depends on first, so that the benchmarks run against the current code. Do it
again after a change to `dept44-starter` or `dept44-starter-logback-logserver`:

```bash
mvn install -pl dept44-benchmarks -am -DskipTests
```

A plain `mvn install -DskipTests` does not work, since dept44-example then fails its coverage check.

Run every benchmark. This takes about an hour:

```bash
mvn -pl dept44-benchmarks exec:exec
```

Run some of them, picked by a regular expression over their names:

```bash
mvn -pl dept44-benchmarks exec:exec -Djmh.include=LogbookChainBenchmark
```

Make a quick run to try a change. It uses one fork and short iterations, so the numbers are only a rough guide:

```bash
mvn -pl dept44-benchmarks exec:exec -Djmh.args="-f 1 -wi 1 -i 1 -w 200ms -r 200ms"
```

Other JMH options go in `jmh.args` as well. For example, `-p bodyKiB=100` runs a single value of a parameter, and
`-h` lists the options.

## Reading the results

The results are printed at the end of the run and written to `dept44-benchmarks/target/jmh-result.json`.

- `Score` is the average time per operation in microseconds. Lower is better.
- `Error` is the 99.9% confidence interval. A difference smaller than it is noise.
- `gc.alloc.rate.norm` is the number of bytes allocated per operation. Every run profiles allocation.

Numbers are only comparable between runs on the same machine. Run on a machine that does nothing else, and keep a
laptop plugged in.

### The report

Open `dept44-benchmarks/report.html` in a browser and load a `jmh-result.json`, or drop one onto the page. It draws a
chart for each question the benchmarks answer, switches between time and allocation, and has the values as tables.
Load an earlier run under "Compare with" to see what changed, the largest changes first. The file is read in the
browser and sent nowhere.

Each run overwrites `jmh-result.json`, so copy it somewhere before the next run to keep it for a comparison. To share
a run as a single file, paste its `jmh-result.json` into the `embedded-results` block at the end of `report.html`,
and an earlier run into `embedded-baseline` to compare with it.

## The benchmarks

|            Benchmark            |                                      Measures                                       |               Parameters               |
|---------------------------------|-------------------------------------------------------------------------------------|----------------------------------------|
| AllowlistBodyFilterBenchmark    | The allowlist filter alone, with the maximum size disabled                          | `paths` 0–500, `bodyKiB` 1–1000        |
| AllowlistRuleBenchmark          | The kind of path against the shape of the body, at 200 paths and 100 KiB            | `ruleKind`, `shape`                    |
| JsonBaselineBenchmark           | Parsing a body with Jackson and writing it back: the least the allowlist can cost   | `bodyKiB` 1–1000                       |
| JsonPathFilterBenchmark         | The `logbook.body-filters.json-path` filters with the same paths and bodies         | `paths` 10–200, `bodyKiB` 1–1000       |
| LogbookChainBenchmark           | The payload logging of a GET answered with JSON, through the Logbook dept44 sets up | `allowlist`, `bodyKiB` 1–1000          |
| LogOutputBenchmark              | As above, written to the console and over GELF, with and without PII masking        | `output`, `allowlist`, `bodyKiB` 1–300 |
| SupportManagementChainBenchmark | The chain with the 183 paths of api-service-support-management and pages of errands | `allowlist`, `errands` 1–100, `output` |

The chain benchmarks start the Logbook of `LogbookConfiguration` with dept44's defaults, so a body larger than the
allowlist's `max-size` of 256 KiB is replaced by a note. `LogbookChainBenchmark` discards the payload log at the
logger. `LogOutputBenchmark` writes it with the `logback-spring.xml` of dept44-starter-logback-logserver, as a service
does: to the console, encoded and then discarded, and over GELF to a log server on localhost that never reads what it
is sent. With an output ending in `_MASKED`, the PII masker runs on every line. Reading the body from the servlet
stream is not included in any of them.

`LogOutputTest` checks that each output writes and masks what it claims to, so a change to `logback-spring.xml` that
stops the masking of GELF fails the build.

## Adding a benchmark

Add a class to `se.sundsvall.dept44.benchmarks` with `@Benchmark` methods, annotated like the others. `@Param` fields
must be public. `Payloads` generates bodies, `AllowlistPaths` generates paths, and `LogbookContexts` starts the
Logbook that `LogbookConfiguration` sets up.
