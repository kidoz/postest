# Build and run from source

## Prerequisites

- **JDK 21** to run Gradle (required so detekt works)
- **JDK 25** as the project toolchain (Gradle auto-provisions it if missing)
- Internet access for the first dependency download

## Run the app

```bash
./gradlew run
```

Gradle compiles the project and launches Postest from `build/libs/postest-<version>.jar`.

## Build and test

```bash
./gradlew build
./gradlew test
```

Run a single test class:

```bash
./gradlew test --tests "su.kidoz.postest.data.repository.CollectionRepositoryTest"
```

## Lint and static analysis

```bash
./gradlew ktlintCheck detekt
./gradlew ktlintFormat
```

Run Gradle on JDK 21 for detekt. If Gradle runs on JDK 25, detekt 1.23.x fails even though the project compiles with the JDK 25 toolchain.

## Shortcut tasks with just

If you have [`just`](https://github.com/casey/just) installed:

```bash
just run
just build
just test
just lint
just format
just package
```

## Troubleshooting

- **No matching Java toolchain** - install JDK 25 or allow Gradle to download it. Compilation targets toolchain 25 even when Gradle itself runs on 21.
- **detekt fails with an unsupported class version** - switch Gradle to JDK 21.
- **The app starts but the UI is unchanged** - confirm you are running `./gradlew run` or a freshly built bundle, not an old installed app. See [Build installers](build-installers.md).

## Related

- [Build installers](build-installers.md)
- [Architecture](../explanation/architecture.md)
- [Release a new version](release.md)
