# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

vDeck is a freshly scaffolded Spring Boot application — the only Java sources are
`VdeckApplication` (the `@SpringBootApplication` entry point) and a `contextLoads` smoke test.
There is no domain code, no controllers, no entities, and no templates yet. Do not infer the
architecture from the current empty package layout; follow the design document described below.

Base package: `com.github.pplociennik.vdeck` (`src/main/java`, mirrored in `src/test/java`).

## Required design context

Before reviewing, planning, or implementing any feature, first read
`.docs/architectural_decisions.md`. It is the highest-priority repository document for critical
architectural decisions. Then read `.docs/first_design_ideas.md`, which contains the detailed agreed
architecture, module and package boundaries, process lifecycle, recovery, persistence, RCON, and
data-safety rules. Feature-specific plans and implementations must remain consistent with both
documents. If they conflict, `.docs/architectural_decisions.md` takes precedence.

Also read every Markdown file in `.docs/rules/` before making changes. These files contain mandatory
repository-wide working rules. Start with `.docs/rules/README.md`, which indexes the rule set,
defines precedence between documents, and provides the repository-wide Definition of Done. In
particular, `.docs/rules/documentation.md` defines the required
Javadoc coverage and the documentation that must be updated together with code changes. All Javadoc
must always be written in English. `.docs/rules/unit-tests.md` defines the mandatory unit-test
coverage, test-class structure, GIVEN/WHEN/THEN convention, and AssertJ assertion style for new or
modified Java classes containing behavior. `.docs/rules/clean-code.md` defines the mandatory readability,
maintainability, naming, design, modern-Java, public nullability annotation, and `requireNonNull`
conventions. `.docs/rules/maven.md` defines the mandatory minimal POM structure, formatting,
dependency and plugin version-property, inheritance, and Maven verification rules.
`.docs/rules/security.md` defines mandatory secret handling, authentication, input validation,
process and file safety, and application logging rules.

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
  `spring-boot-starter-test`.
- Spring MVC + Thymeleaf for the web layer, Spring Data JPA + SQLite (`org.xerial:sqlite-jdbc`) for persistence
- Log4j2 is the required application logging API and backend; production classes use Lombok
  `@Log4j2` by default and do not introduce a parallel logging facade
- Lombok is preferred wherever it safely removes mechanical boilerplate without hiding validation or
  domain behavior, but Java records take precedence when both approaches model an immutable data
  carrier equally well. Its annotation processor is wired explicitly in `pom.xml` for both
  `default-compile` and `default-testCompile`; adding other processors means adding them to *both*
  `annotationProcessorPaths` blocks
- spring-boot-devtools on the runtime classpath — restart is auto-disabled under tests

## Commands

Use the wrapper (`.\mvnw.cmd` in PowerShell, `./mvnw` in Bash) — it guarantees Maven 3.9.16.

```powershell
.\mvnw.cmd spring-boot:run          # run the app
.\mvnw.cmd test                     # full test suite
.\mvnw.cmd test -Dtest=ClassName    # single test class
.\mvnw.cmd test -Dtest=ClassName#methodName
.\mvnw.cmd package                  # build the executable jar into target/
.\mvnw.cmd spring-boot:build-image  # OCI image
```

There is no linter or formatter configured.

## Known blocker: no datasource is configured

`src/main/resources/application.yaml` sets only `spring.application.name`. Because
`spring-boot-starter-data-jpa` is on the classpath and SQLite has no embedded-datasource
auto-configuration, both `spring-boot:run` and `mvnw test` currently fail during context refresh with:

```
Failed to determine a suitable driver class
```

This is a pre-existing failure, not something a change introduced. Any work that boots the Spring
context needs a datasource first, e.g.:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:vdeck.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.community.dialect.SQLiteDialect
```

The SQLite Hibernate dialect lives in `org.hibernate.orm:hibernate-community-dialects`, which is
**not** currently a dependency — add it before relying on `database-platform`. Alternatively, slice
tests (`@WebMvcTest`, `@DataJpaTest` with an in-memory DB) avoid booting the full context.
