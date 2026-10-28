# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

vDeck is an early-stage Maven reactor with five code modules and one distribution module, matching `design.md` §2:

- `vdeck-runtime` — pure Java, no Spring. Owns the serialized lifecycle, Windows FFM launcher,
  launch identity, complete BEST_EFFORT world snapshots, isolated CTRL+C helper, asynchronous Stop
  and manually confirmed Kill. Failed Stop retains the process (except the panel close strategy
  `stop`, AD-031). Kill-on-close protects incomplete
  creation only; a successfully created server can outlive the panel (AD-017). Installation FileLock,
  a secret-free `.vDeck/server.pid` and explicit verified adoption prevent duplicate launches.
- `vdeck-runner` — pure Java, no Spring. Provides `runner.VdeckRunner`, the executable CTRL+C
  helper entry point. Builds a self-contained `target/vdeck-runner.jar`; install it as
  `lib/vdeck-runner.jar` in the PathService installation directory, including for IDE and
  `spring-boot:run` usage. The distribution keeps every JAR in `lib`, so every vDeck JVM receives
  `vdeck.server-directory` (AD-021).
- `vdeck-launcher` — pure Java, no Spring, `lib/vdeck-launcher.jar`. The only program the start script
  runs (AD-024, AD-026). `LauncherMain` (no logger) creates the run log
  `.vDeck/logs/launcher/<day>/<timestamp>.log` before Log4j2 starts. `VdeckLauncher` takes
  `.vDeck/startup.lock` and `.vDeck/vdeck.lock`, hands an active journal to recovery, cleans finished or
  unpublished update leftovers, reads `.vDeck/config/vDeck-start.properties` (`panel.address`,
  `panel.port`, `update.enabled`) and, for a release build with a stopped server, prepares a newer
  GitHub release (`UpdatePreparation`: API, SHA-256 `digest`, `PackageExtractor` (zip4j), executor copy, one
  download aborted after 30 s without data, progress in the console, one 60-minute safety ceiling that
  shuts the HTTP client down, AD-027). A prepared update is handed to the updater;
  any preparation failure starts the installed panel instead. The panel is handed the installation
  lock and the launcher keeps the gate until the panel exits, returning its exit code.
- `vdeck-updater` — pure Java, no Spring, no network, `lib/vdeck-updater.jar`, always run from the
  independent copy in `.vDeck/update/executor`. Applies the journaled transaction
  (`UpdateJournal`, `FileReplacer`, idempotent `UpdateRollback`), merges settings with
  `PropertiesMerger` (AD-025), recovers with `--recover` and hands over to the installed launcher with
  `--after-update`. Run logs go to `.vDeck/logs/updater`.
- `vdeck-runtime` also holds the shared start/update contract in `runtime.installation`:
  `InstallationLock`, `InstallationOwnership`, `HandoffProtocol` (one-time, acknowledged lock handoff
  through `.vDeck/handoff`), `SuccessorStarter`, `UpdateLayout`, `UpdateJournalHeader`,
  `DistributionItem`, `DurableFiles`, `RunLogFile`, `ServerActivityCheck` and `UserConsole`.
  `UserConsole` takes only message keys (`ConsoleMessageIf` enums: `ConsoleMessage`, `LauncherMessage`,
  `UpdaterMessage`); `I18n` resolves them from `vdeck-runtime/src/main/resources/console-messages.properties`
  with `MessageFormat` and Polish number formatting, so no console text is hardcoded.
  `ReleaseVersion` (`MAJOR.MINOR.PATCH`) lives here too, shared by the launcher and the panel.
  `ManagedLaunchStore` owns an `InstallationLock`; `shared.lock.InstallationLockService`, called from
  `VdeckApplication.main()`, is the only place that takes it over from the launcher
  (`-Dvdeck.handoff=<id>`) before Spring starts and registers it as the `installationLock` bean.
- `vdeck-app` — the Spring Boot application. Holds `VdeckApplication`, shared path/configuration
  infrastructure and the first implemented features: panel authentication (persisted admin account,
  Spring Security's generated login page, and two separate password screens — the forced
  `/initial-password-change` that asks for no current password and the on-demand `/change-password`
  that does) plus a `home` feature serving `/` with the action island only, and a `launch` feature
  whose panels edit the Valheim launch profile stored in `valheim-server.properties`: the four
  panels of the side menu's expandable "Serwer" branch (AD-030) — `/server-settings/server`
  ("Ogólne"), `/server-settings/access` ("Dostęp"), `/server-settings/world` ("Świat i zapis") and
  `/server-settings/inner-server-backups` ("Kopie automatyczne"), one `ServerSettingsController`, one form record
  each implementing the sealed `dto.LaunchProfileForm`, one shared view `launch/server-settings.html`
  with per-panel fragments in `launch/server-settings-panels.html` — and `/game-settings`
  ("Ustawienia gry", with a hidden-by-default "Zaawansowane" section whose visibility is remembered in
  `vDeck.properties`). Each panel stores only its own settings. The `server` feature starts
  that profile through `POST /server/start`, validates it, prepares per-launch logs and a secret-free
  mirror script, and supplies the action island with runtime status. `LaunchProfileService` publishes an immutable
  `LaunchConfiguration` (secret-free profile plus password) after settings initialization and every
  successful profile save. Start captures it once for validation and command generation. The builder
  returns `LaunchSpec` directly; the mirror script is rendered from that same specification. Start, Stop and manually confirmed Kill are wired to the island.
  Restart is not implemented. `server-status.js` polls the island every two seconds while visible.
  The `logs` feature serves `/console` ("Konsola", in the side menu's "Procesy" section): `console.js`
  polls `GET /console/log?offset=N` every second and `LogServiceImpl` reads the log file named by
  `ServerStatus.getLogFile()` from that byte (at most 256 KiB, cut at the last line break; the last
  256 KiB without an offset). It keeps nothing in memory and uses no SSE. The `releasenotes` feature
  (AD-029) shows the „Co nowego w vDeck" card on `/` after an update: `ReleaseNotesReader` reads
  `release-notes/<X.Y.Z>.md` from the classpath, `ReleaseNotesServiceImpl` selects versions newer than
  the highest row of `release_notes_acknowledgements` and not newer than the installed version
  (`BuildProperties` from the `build-info` goal, `-SNAPSHOT` stripped; without it the feature is off),
  and `POST /release-notes/acknowledge` („Zamknij") stores the installed version. `DataInitializer`
  acknowledges it on a fresh database, so a new installation shows nothing. Views with navigation embed the side menu
  (`templates/common/side-menu.html` + `static/js/side-menu.js`) and, as a separate fragment, the round
  home button next to its toggle (`templates/common/home-button.html`); `static/js/game-settings.js` controls the advanced settings section.
- `.dist` (`vdeck-dist`) — packaging-only module depending on app and runner. Builds
  `target/vDeck-dist-${project.version}.zip` from the root reactor's `package` phase. Contains the
  start script, user READMEs and `.vDeck/config` defaults (including `vDeck-start.properties`, whose
  keys tests keep equal to `StartSettings`; `vDeck.properties` and `valheim-server.properties` are kept
  equal to their enums by `DistributionTemplatesTest`). AntRun downloads the pinned Windows x64
  Temurin JRE 25 and checks SHA-256; Asciidoctor converts the Polish user manual
  (`dokumentacja/instrukcja-uzytkownika/instrukcja-uzytkownika.adoc`) to `vDeck-instrukcja.pdf`,
  failing on any warning such as a missing image; Resources stages both executable JARs; Assembly
  creates the ZIP. AD-022: the manual must always be current — every user-visible change updates its
  text, screenshots (`zrzuty/zrzuty-ekranu.mjs`, Playwright, run by hand against a freshly extracted
  package) and drawings (`rysunki/*.mmd` rendered to PNG) in the same change (`documentation.md` §3.6).
  Generated `.dist/jre` and JARs are ignored by Git and removed by `clean`, while templates remain.
  `start-vDeck.bat` uses the bundled JRE, sets the installation as its working directory and runs the
  launcher, or the executor's updater with `--recover` while `.vDeck/update/apply.journal` exists; the
  Java call is one parsed block ending in `call exit /b %%^ERRORLEVEL%%` so an update can replace the
  script safely. The panel binds to loopback by default. Test extracted copies instead of running the versioned staging directory.
- `.github/workflows/build.yml` — GitHub Actions on `windows-latest` (FFM tests need Windows). Every
  push and pull request to `main` runs `mvnw test` only. A push to `main` whose version has no
  `-SNAPSHOT` suffix and no `v<version>` tag yet runs `clean package` and publishes a GitHub Release
  with `vDeck-dist-<version>.zip` marked `--latest`, tagging the built commit (AD-023). Installed
  launchers update from that release (AD-024); the repository must have immutable releases enabled. Releasing means setting the
  version in all POMs (`versions:set`) and merging to `main`; a failed tag lookup fails the job.
  The release description is `vdeck-app/src/main/resources/release-notes/<version>.md` (`--notes-file`
  plus `--generate-notes`); the workflow and `.env/release.bat` refuse a release whose file has no
  `- **` entry, and `release.bat` creates the next version's file from `.env/release-notes-template.md`.
  Every user-visible change adds an entry to the current version's file (`documentation.md` §3.7):
  sections `## Nowości`, `## Zmiany`, `## Poprawki` in that order, each entry `- **Title**` (≤ 80
  chars) plus one description line indented by two spaces (≤ 200 chars). `ReleaseNotesFilesTest` fails
  the build on a malformed file or a missing file for the built version.

`VdeckApplication.main()` lets `InstallationLockService` accept the launcher handoff, if any, and then start only Spring. `config.ConsoleControlConfiguration` creates the idle
`ConsoleControlSender` bean and injects it into runtime. Each signal request starts a short-lived
runner process with `--ctrl-sender <pid> <creation-instant> <absolute-executable>`; no helper process
or waiting thread is started during bean creation (AD-020). Helper exit codes are 0 (event sent), 1 (signal/identity failure) and 2 (invalid
arguments). A malformed helper invocation never starts the panel. Successful sending does not prove
that Valheim saved or exited. The stop plan is `.docs/plans/server-stop.md`. RCON is excluded from this implementation.
AD-018 places snapshots in `<installation>/.vDeck/<snapshot.directory>` (`vDeck.properties`, default
`shutdown-snapshots`, a single directory name) through `BackupPathService`; every directory vDeck creates lives
below `.vDeck`. The root is resolved per launch and persisted in the `server.pid` token for adoption. Stop requires a complete snapshot
before CTRL+C. Confirmed Kill attempts a snapshot, then immediately forces termination even if
copying failed; it sends no CTRL+C and requires no additional confirmation (AD-017). Cancellation,
deadline and identity checks still apply. Missing required source saves give WORLD_SAVE_MISSING.
Snapshots retain `snapshot-latest` through atomic replacement, rollback and crash recovery (AD-019),
with 8 GiB per set and 8192 entries. Obsolete `.old-*` and `.tmp-*` directories are cleaned; legacy
`snapshot-<operationId>` directories are preserved. Windows file identity uses volume serial and
128-bit FileId through FFM. Installation roots are canonicalized; directory checks reject redirects
below their trusted root. Invalid recovery tokens are quarantined after an empty installation process
scan. Adoption uses `ServerAdoptException` (AD-014). No automatic force is performed, except on panel
close: `config.ServerShutdownCoordinator` runs in `@PreDestroy` (after the web server stopped, before the
runtime bean is destroyed) and applies the `feature.server.service.PanelCloseStrategy` chosen by
`panel.close.strategy` in `vDeck.properties` (AD-031): `stop` (default — graceful shutdown, then
`StopRequest.shutdownKill` for a process left in STOP_FAILED), `graceful` (no force) or `leave` (no
signal); an unknown value means `stop`. `StopCompletion` waits at most 6 minutes per runtime operation
and never throws. The runtime then releases resources without terminating a surviving process.
Real Valheim save consistency and CTRL+C behavior
still require a Windows deployment test.

Only packages holding real code exist; the rest of the `design.md` §3 tree is created when
its feature is implemented. Do not infer missing architecture from the incomplete package layout.

Base package: `com.github.pplociennik.vdeck` (`vdeck-app/src/main/java`, mirrored in
`vdeck-app/src/test/java`).

## Feature and infrastructure package ownership

The package locations below are explicit architectural decisions (`AD-003` through `AD-005`), not
interchangeable technical naming conventions:

- every vertical feature belongs under `com.github.pplociennik.vdeck.feature.<name>`; no feature is
  created directly under the base package;
- account, password-change and login-domain code belongs to `feature.authentication`; its JPA model
  belongs to `feature.authentication.domain`, web input records to `feature.authentication.dto`, and
  repository interfaces to `feature.authentication.persistence`;
- service contracts belong to the feature's `service` package and implementations to `service.impl`;
  consumers inject the contract, while implementation classes remain package-private where possible;
- infrastructure services use the same `service` and `service.impl` split inside their owning
  infrastructure package, as in `shared.service.PathService` and `shared.service.SystemPropertiesService`;
- every Spring `@Configuration` class belongs to the top-level
  `com.github.pplociennik.vdeck.config` composition root; `VdeckApplication` is the bootstrap-class
  exception that remains in the base package;
- cross-cutting Spring Security filters and handlers belong to the top-level
  `com.github.pplociennik.vdeck.security` package, not under `feature.authentication`;
- startup data initialization, including `DataInitializer`, belongs to the top-level
  `com.github.pplociennik.vdeck.config` package, even when it injects an authentication repository.

Use the narrowest viable Java visibility. Spring components and their constructors are package-private
unless an actual cross-package contract or a Java/framework requirement makes them public.

## Configuration channels

Configuration is split by owner (see `AD-002`):

- `application.yaml` inside the jar — Spring Boot infrastructure only (datasource, ports, log levels).
  Not user-editable.
- User-editable `.properties` files, **all** in `<installation>/.vDeck/config/` (`AD-010`), never
  directly next to `vdeck.jar`: `vDeck.properties` (the panel's own settings) and
  `valheim-server.properties` (the Valheim launch profile, `AD-013`). Each file is described by an
  enum implementing `shared.SystemPropertyIf` (key + `isMandatory()`, set through the enum
  constructor). A single `shared.service.SystemPropertiesService` bean serves every file (`AD-013`):
  each call takes the file name (the enum's `FILE_NAME`), and `config.SystemPropertiesInitializer`
  loads every file with `loadFile(fileName, defaults)` at startup, so a new file is registered there.
  Sets of values (defaults, batch writes) are passed as `shared.SystemPropertyValues`, built per call
  with `SystemPropertyValues.create().with(property, value)`, never as a raw `Map`. Typed values are
  read through the static `shared.PropertyReader` (`read`, `readInt`, `readBoolean`, `readOptional`),
  which takes the service, file name, setting and fallback, instead of per-service read helpers.
  Resolve a file only through `PathService.getConfigurationFile(fileName)`. Created with defaults (and the directory) on first
  start; missing keys are restored on the next start; an optional setting may be empty.

Interface texts are not a configuration channel (see `AD-009`): the panel is Polish-only and its
texts live in `vdeck-app/src/main/resources/messages.properties`, read from the classpath by Spring
Boot's default `MessageSource`. There is no external translations directory, no
`vdeck.translations-directory` property and no `MessageSource` `@Configuration` class;
`application.yaml` only sets `spring.messages.use-code-as-default-message` so a missing key renders
as the key instead of failing the request. No text is hardcoded: templates use `#{key}` and Java
passes keys, using enums implementing `shared.TranslationKeyIf` (for example
`PasswordChangeMessage`) rather than string literals. Changing a text means changing the repository
and shipping a new release.

Every configured path, including the AD-018 backup root, goes
through `PathService.getAbsolutePath(String)`: it must be relative, is
normalized, and after resolution must stay inside the installation directory.

**Running from an IDE or via `spring-boot:run` requires `-Dvdeck.server-directory=<dir>`.**
Without it `ApplicationHome.getDir()` resolves to `vdeck-app/target/classes`, so the settings file
would be written into a directory devtools watches, causing a restart loop.

`VdeckApplication.main()` first runs `feature.server.ServerExecutableCheck`: without a regular
`valheim_server.exe` in the installation directory it prints a console message and exits with 1
before the launcher handoff and Spring. It is on by default; `-Dvdeck.server-file-check=false`
disables it, e.g. for a sandbox without the game server (or `JAVA_TOOL_OPTIONS` for the package).

Native Windows calls require `--enable-native-access=ALL-UNNAMED` when running with `java -jar`
or from an IDE. Surefire and `spring-boot:run` set it in their POM configuration; when overriding
`spring-boot.run.jvmArguments`, include this flag along with the installation-directory override.

## Exception handling

Web-layer errors follow `AD-014`. Every main process of a feature ends a rejection **and** a failure with
one process exception in the feature's `exc` package (`LaunchProfileUpdateException`,
`PasswordChangeException`, `ServerStartException`) carrying a `TranslationKeyIf` enum constant and,
for a failure, the cause.
Services return no result objects and controllers do not catch; an annotation-validation failure is
thrown as the same process exception. The feature's `@ControllerAdvice` (in the feature root, limited
by `assignableTypes`, `@Order( Ordered.HIGHEST_PRECEDENCE )`) logs once and redirects back to the
submitted screen with the message key (and the form without secrets) as flash attributes; the GET
handler adds the stored form only when the model holds no flashed one. Every other exception goes to
`shared.web.DefaultExceptionHandler`, which logs every `RuntimeException` at `WARN` and sends `500`.
A `catch` is only allowed where it changes the
meaning of the error (translation to a process exception, checked-to-unchecked, rollback, cleanup,
documented fallback value).

## Required design context

Before reviewing, planning, or implementing any feature, first read
`.docs/architectural_decisions.md`. It is the highest-priority repository document for critical
architectural decisions. Then read `.docs/design.md`, which contains the detailed agreed
architecture, module and package boundaries, process lifecycle, recovery, persistence, RCON, and
data-safety rules. Feature-specific plans and implementations must remain consistent with both
documents. If they conflict, `.docs/architectural_decisions.md` takes precedence.

Also read every Markdown file in `.docs/rules/` before making changes. These files contain mandatory
repository-wide working rules. Start with `.docs/rules/README.md`, which indexes the rule set,
defines precedence between documents, and provides the repository-wide Definition of Done. In
particular, `.docs/rules/documentation.md` defines the required
Javadoc coverage, the documentation that must be updated together with code changes, and the
project documentation kept in the top-level `dokumentacja/` directory (`dokumentacja-projektowa.md`
plus one Mermaid `.mmd` source per diagram under `dokumentacja/diagramy/<feature>/`, cross-cutting
diagrams in `dokumentacja/diagramy/wspolne/`, and the rendered `.svg` that the document actually
embeds in each directory's `img/` subdirectory — regenerate it with mermaid-cli whenever the source
changes; never paste Mermaid code into the document). All Javadoc
must always be written in English. `.docs/rules/unit-tests.md` defines the mandatory unit-test
coverage, test-class structure, GIVEN/WHEN/THEN convention, and AssertJ assertion style for new or
modified Java classes containing behavior. `.docs/rules/clean-code.md` defines the mandatory readability,
maintainability, naming, design, modern-Java, public nullability annotation, and `requireNonNull`
conventions. `.docs/rules/maven.md` defines the mandatory minimal POM structure, formatting,
dependency and plugin version-property, inheritance, and Maven verification rules.
`.docs/rules/security.md` defines mandatory secret handling, authentication, input validation,
process and file safety, and application logging rules. `.docs/ui.md` is mandatory before any
interface change: it defines stylesheet organisation (all custom properties in `css/variables.css`,
imported by every other sheet), reusable components as Thymeleaf fragments in `templates/common/`
with their styles in the global sheet, the single SVG sprite, translations and the requirement that
every view works on phones and tablets in both orientations. `.docs/rules/formating.md` defines the
mandatory code layout — 4-space indents, the project's inner-space bracket style
(`method( arg )`, `Map< K, V >`), import grouping, and the rule that public methods always come
first in a class.

Do not silently override a documented decision. If a requested change conflicts with the design,
call out the conflict and update the design document only when the user has approved the new
decision.

## Stack

- Java 25, Maven (wrapper pins Maven 3.9.16)
- Spring Boot **4.1.0** — note the Boot 4 package moves: autoconfiguration classes live under
  `org.springframework.boot.<module>.autoconfigure.*` (e.g. `org.springframework.boot.jdbc.autoconfigure`),
  not the Boot 3 `org.springframework.boot.autoconfigure.<module>` locations. Starters are also
  renamed: `spring-boot-starter-webmvc` (not `-web`), and test starters are per-module
  (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-thymeleaf-test`) rather than a single
  `spring-boot-starter-test`. Security and Web MVC test starters are declared only in `vdeck-app`;
  keep Spring test dependencies out of the parent so they do not leak into `vdeck-runtime`.
- JUnit 5, AssertJ and Mockito are declared individually in the root POM, for the same reason
- Spring MVC + Thymeleaf for the web layer, Spring Data JPA + SQLite (`org.xerial:sqlite-jdbc`) for persistence
- Liquibase owns the schema (AD-028): Spring Boot applies XML changesets at panel startup and
  Hibernate runs with `ddl-auto: none`. `db/changelog/db.changelog-master.xml` only includes one
  `versions/<version>.xml` per project version; a released version file is never edited, and each
  task's changesets form one block headed by a separator comment with the task number and
  description (`clean-code.md` §13). A new changeset's id is its creation time as
  `yyyy-MM-dd HH:mm:ss`; existing ids (`VDEC-24-create-panel-users`) stay unchanged. Every changeset starts with `preConditions onFail="MARK_RAN"`
  checking that its change is not applied yet (e.g. `not tableExists`), so an existing object never
  aborts startup; preconditions are outside the checksum, so adding them to a released file is the
  one allowed edit. `spring.liquibase.analytics-enabled: false` keeps Liquibase off
  the network. `DatabaseSchemaIntegrationTest` validates the schema against the entities
- Log4j2 is the required application logging API and backend; `spring-boot-starter-log4j2` is declared
  in `vdeck-app` and `spring-boot-starter-logging` is excluded from **every** starter that pulls it in
  (exclusions apply per declaration path, so one omission puts Logback back). Production classes use
  Lombok `@Log4j2` by default and do not introduce a parallel logging facade. Each panel run also logs to
  `.vDeck/logs/panel/<day>/<timestamp>.log` (AD-032): `shared.log.PanelRunLog` creates the file in
  `main()` and adds `log4j2-vdeck-file.xml` to `logging.log4j2.config.override` (a system property, so it
  repeats the console override of `application.yaml`; `PanelRunLogTest` keeps them equal). Without a file
  (tests) nothing is added. `config.PanelLogRetention` applies `panel.log.retention.enabled`/`.days`
  after start; `shared.LogRetention` interprets that pair and the `game.log.retention.*` pair alike —
  `0` days keeps every log
- Lombok is preferred wherever it safely removes mechanical boilerplate without hiding validation or
  domain behavior, but Java records take precedence when both approaches model an immutable data
  carrier equally well. Its annotation processor is wired explicitly in `pom.xml` for both
  `default-compile` and `default-testCompile`; adding other processors means adding them to *both*
  `annotationProcessorPaths` blocks
- spring-boot-devtools on the runtime classpath — restart is auto-disabled under tests

## Commands

Use the wrapper (`.\mvnw.cmd` in PowerShell, `./mvnw` in Bash) — it guarantees Maven 3.9.16.

```powershell
.\mvnw.cmd test                                  # full test suite, whole reactor
.\mvnw.cmd test -pl vdeck-app -Dtest=ClassName   # single test class
.\mvnw.cmd test -pl vdeck-app -Dtest=ClassName#methodName
.\mvnw.cmd package                               # target/vDeck-dist-<version>.zip with both JARs and JRE 25
.\mvnw.cmd -pl vdeck-app spring-boot:build-image # OCI image

# Run the app. The override is mandatory — see "Configuration channels" above.
# Do NOT add -am: spring-boot:run would also fire on the aggregator, which has no main class
# ("Unable to find a suitable main class"). Run install once so vdeck-runtime is resolvable.
.\mvnw.cmd install -DskipTests
New-Item -ItemType Directory -Force D:\path\to\sandbox\lib
Copy-Item vdeck-runner\target\vdeck-runner.jar D:\path\to\sandbox\lib\vdeck-runner.jar
.\mvnw.cmd -pl vdeck-app spring-boot:run `
  "-Dspring-boot.run.jvmArguments=--enable-native-access=ALL-UNNAMED -Dvdeck.server-directory=D:\path\to\sandbox"

# Run the packaged launcher (as the start script does) against an extracted installation.
D:\path\to\installation\start-vDeck.bat

# Run a packaged panel against an explicit installation directory.
java --enable-native-access=ALL-UNNAMED "-Dvdeck.server-directory=D:\path\to\sandbox" `
  -jar vdeck-app\target\vdeck.jar
```

There is no linter or formatter configured.

## Known warnings during the build

The following warning is expected:

- `Mockito is currently self-attaching to enable the inline-mock-maker.` Mockito 5 self-attaches on
  JDK 25. Harmless today; it needs a `-javaagent` entry in the surefire configuration before a future
  JDK turns it into an error.
- `sun.misc.Unsafe::arrayBaseOffset has been called by org.jruby.util.StringSupport` during the
  `.dist` user manual conversion. JRuby, which runs Asciidoctor, still uses `sun.misc.Unsafe`;
  harmless until a JDK removes the method, which requires an asciidoctorj release on a newer JRuby.
