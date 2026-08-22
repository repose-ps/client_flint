# Reproducible build and regression verification

The project uses the pinned Maven build introduced in Refinement Step 6. Refinement Step 7 adds renderer pixel-golden coverage to the same regression gate.

## Requirements

- JDK 17 or newer.
- Network access the first time `mvnw` bootstraps Apache Maven 3.9.16 and Maven resolves the pinned plugins.
- The authentic revision-377 cache fixture for the full regression gate.

The wrapper pins Apache Maven 3.9.16 and verifies the downloaded distribution with the SHA-512 published by Apache. Maven plugin versions are also pinned in `pom.xml`.

## Compile and package

```sh
./mvnw clean package
```

This compiles the 100 production sources and all retained test sources with Java 17 `--release` semantics plus `-Xlint:deprecation` and `-Xlint:unchecked`. Warnings fail the build.

The output JAR is written under `target/` and declares `rs2.Client` as its main class.

## Full regression gate

The cache-backed suites intentionally use the separately supplied authentic revision-377 cache rather than copying that large fixture into the source repository.

```sh
./verify.sh /path/to/rscache
```

or directly:

```sh
./mvnw clean verify -Drscache=/path/to/rscache
```

Windows:

```bat
verify.cmd C:\path\to\rscache
```

The `verify` phase runs `rs2.RegressionSuite`, which launches every retained suite in its own JVM:

1. `rs2.concurrent.ThreadLifecycleSafetyTest`
2. `rs2.net.NetworkResourceRobustnessTest`
3. `rs2.media.RendererGoldenTest`
4. `rs2.cache.Revision377CacheSmokeTest`
5. `rs2.cache.BootstrapArchiveRecoveryTest`
6. `rs2.cache.Revision377OnDemandDecompressionTest`

The runner validates the cache fixture before starting and fails the build immediately if any suite exits unsuccessfully. `RendererGoldenTest` hashes deterministic software-renderer pixel buffers and also loads the authentic texture archive from the revision-377 cache for textured-triangle coverage.

## Historical parity suites

The historical multi-million-check parity sources described in earlier project handoffs were not present in the Step-5 source baseline. Step 6 therefore retains and wires every test source that actually exists in the authoritative baseline; it does not fabricate replacements for unavailable parity-suite source files. If those historical sources are recovered later, they should be added under `test/` and registered in `RegressionSuite` so they become part of the same `verify` gate.
