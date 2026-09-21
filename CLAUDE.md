# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

vDeck is an early-stage Maven reactor with two modules, matching `design.md` §2:

- `vdeck-runtime` — pure Java, no Spring. **Currently empty and has no `src/` directory at all**, so
  the build prints a `JAR will be empty` warning for it. That is expected, not a defect.
- `vdeck-app` — the Spring Boot application. Holds `VdeckApplication`, shared path/configuration
  infrastructure and the first implemented features: panel authentication (persisted admin account,
  Spring Security's generated login page, and two separate password screens — the forced
  `/initial-password-change` that asks for no current password and the on-demand `/change-password`
  that does) plus a `home` feature serving `/` with the action island only. Views with navigation
  also embed the markup-only side menu (`templates/common/side-menu.html` + `static/js/side-menu.js`,
  the app's only ES module so far).

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
- `vDeck.properties` next to `vdeck.jar` — the panel's own user-editable settings, described by an
  enum implementing `shared.SystemPropertyIf` and served by
  `shared.service.SystemPropertiesService` (one instance per file). Created with defaults on first
  start; missing keys are restored on the next start.

Interface texts are not a configuration channel (see `AD-009`): the panel is Polish-only and its
texts live in `vdeck-app/src/main/resources/messages.properties`, read from the classpath by Spring
Boot's default `MessageSource`. There is no external translations directory, no
`vdeck.translations-directory` property and no `MessageSource` `@Configuration` class;
`application.yaml` only sets `spring.messages.use-code-as-default-message` so a missing key renders
as the key instead of failing the request. No text is hardcoded: templates use `#{key}` and Java
passes keys, using enums implementing `shared.TranslationKeyIf` (for example
`PasswordChangeMessage`) rather than string literals. Changing a text means changing the repository
and shipping a new release.

Every configured path goes through `PathService.getAbsolutePath(String)`: it must be relative, is
normalized, and after resolution must stay inside the installation directory.

**Running from an IDE or via `spring-boot:run` requires `-Dvdeck.server-directory=<dir>`.**
Without it `ApplicationHome.getDir()` resolves to `vdeck-app/target/classes`, so the settings file
would be written into a directory devtools watches, causing a restart loop.

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
- Log4j2 is the required application logging API and backend; `spring-boot-starter-log4j2` is declared
  in `vdeck-app` and `spring-boot-starter-logging` is excluded from **every** starter that pulls it in
  (exclusions apply per declaration path, so one omission puts Logback back). Production classes use
  Lombok `@Log4j2` by default and do not introduce a parallel logging facade
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
.\mvnw.cmd package                               # executable jar in vdeck-app/target/
.\mvnw.cmd -pl vdeck-app spring-boot:build-image # OCI image

# Run the app. The override is mandatory — see "Configuration channels" above.
# Do NOT add -am: spring-boot:run would also fire on the aggregator, which has no main class
# ("Unable to find a suitable main class"). Run install once so vdeck-runtime is resolvable.
.\mvnw.cmd install -DskipTests
.\mvnw.cmd -pl vdeck-app spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Dvdeck.server-directory=D:\path\to\sandbox"
```

There is no linter or formatter configured.

## Known warnings during the build

Both are expected and must not be "fixed" by adding code:

- `JAR will be empty - no content was marked for inclusion!` for `vdeck-runtime`, which has no sources yet.
- `Mockito is currently self-attaching to enable the inline-mock-maker.` Mockito 5 self-attaches on
  JDK 25. Harmless today; it needs a `-javaagent` entry in the surefire configuration before a future
  JDK turns it into an error.
