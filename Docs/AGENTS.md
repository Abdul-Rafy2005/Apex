# AGENTS.md — Apex Build Constraints \& Context

This file is the operating manual for any AI coding agent (Claude Code, Cursor, etc.) working on this repository. Read this fully before writing any code. If an instruction here conflicts with a convenience shortcut, **this file wins.**

Full product context lives in `PRD.md`. Read that first for *what* and *why*. This file governs *how*.

\---

## 1\. Project Identity

**Apex** is a real-time market simulation and portfolio intelligence platform. It is being built as a **production-grade, multi-tenant enterprise application** — not a tutorial project, not a weekend hackathon app. Every decision should be the one a senior engineer would defend in a design review, including when a simpler shortcut exists.

Core non-negotiable: **crypto is a data provider, not the product identity.** Anything that hardcodes crypto-specific assumptions into core trading/analytics logic (instead of going through the `MarketDataProvider` abstraction) is wrong.

\---

## 2\. Golden Rules (never violate these)

1. **Never use `float`/`double` for money or quantities.** Always `BigDecimal` (Java) / decimal-safe handling (frontend displays formatted strings, does not do float math on money).
2. **Never use `ddl-auto=update`/`create` in any environment.** Schema changes go through Flyway migrations, checked into version control, reviewed like code.
3. **Never compute analytics synchronously on a hot read path.** Analytics are precomputed async (RabbitMQ consumer) and read from cache/snapshot tables.
4. **Never trust the client for tenant/user scoping.** Every query for user-owned data must be scoped server-side from the authenticated principal, not from a client-supplied `userId`/`orgId` parameter.
5. **Never let a trade execute twice.** Every trade execution endpoint requires and honors an `Idempotency-Key` header.
6. **Never mutate or delete a `Trade` row.** The ledger is append-only. Corrections are new offsetting entries.
7. **Never hardcode secrets, API keys, or credentials.** Environment variables only, `.env.example` committed, `.env` gitignored.
8. **Never ship a feature without both unit and integration tests.** See §6.
9. **Never invent product scope.** If a phase prompt or the PRD doesn't ask for it, don't build it — flag it as a suggestion instead of silently adding it.
10. **Never introduce a new library/framework not listed in the tech stack (§4) without explicitly flagging it and asking first.**

\---

## 3\. Architecture Rules

* **Layering:** `Controller → Service → Repository`. Controllers contain no business logic — only request/response mapping and calling a service. Services contain no HTTP concerns. Repositories contain no business logic.
* **External integrations are always behind an interface**: `MarketDataProvider`, `AiJournalGenerator`, etc. Concrete implementations (`BinanceMarketDataProvider`, `CoinGeckoMarketDataProvider`, `ClaudeJournalGenerator`) are swappable and mockable in tests.
* **DTOs, never entities, cross the API boundary.** Entities are persistence-layer only. Mapping via MapStruct or explicit mapper methods — no leaking JPA entities into controller responses.
* **Events for side effects.** Anything that isn't the primary transactional write (analytics recompute, notifications, leaderboard update) happens via a RabbitMQ event consumer, not an inline call chained onto the trade transaction.
* **Every RabbitMQ consumer is idempotent** and has a dead-letter queue configured. Assume messages can be delivered more than once.
* **Multi-tenancy is enforced in the service layer**, via the authenticated `Principal` → resolve `userId`/`orgId` server-side, always. Write an integration test for cross-tenant isolation on any new user-data endpoint.
* **API versioning:** all endpoints under `/api/v1/`.
* **Error responses** follow RFC 7807 (`application/problem+json`) uniformly — one global `@ControllerAdvice` exception handler, no ad hoc error shapes per controller.

\---

## 4\. Tech Stack (locked — do not substitute without asking)

**Backend:** Java 21, Spring Boot 4.x, Spring Security + JWT, Spring Data JPA, Flyway, Jakarta Validation, Maven.

> **Jackson 3 note:** Spring Boot 4.x ships Jackson 3 (`tools.jackson.databind`). Annotation imports remain in `com.fasterxml.jackson.annotation` (e.g., `@JsonIgnoreProperties`, `@JsonProperty`). Core databind imports use `tools.jackson.databind.ObjectMapper` — NOT `com.fasterxml.jackson.databind`. When writing new code or tests, always verify the import path; Jackson 2 and Jackson 3 share the annotation package but have different databind packages.
**Data:** PostgreSQL 16, Redis 7, RabbitMQ.
**Real-time:** WebSocket (STOMP/SockJS), Redis pub/sub for multi-instance fan-out.
**Frontend:** React 18 + TypeScript (strict mode on), Tailwind CSS, TanStack Query, Zustand, Recharts / lightweight-charts.
**Testing:** JUnit 5 + Mockito + Testcontainers (backend), Vitest + React Testing Library + Playwright (frontend).
**Docs:** springdoc-openapi / Swagger UI, auto-generated.
**Infra:** Docker, Docker Compose (local), GitHub Actions (CI).

Full package/dependency versions get pinned in `pom.xml` / `package.json` during Phase 0 and should not drift casually.

\---

## 5\. Backend Conventions

### Package structure (by feature, not by layer-at-top-level)

```
com.abdulrafy.backend
 ├── auth/            (controller, service, dto, entity, repository)
 ├── organization/
 ├── market/
 ├── trading/
 ├── analytics/
 ├── journal/          (AI trade journal)
 ├── notification/
 ├── common/           (shared config, error handling, security, utils)
 └── ApexApplication.java
```

Each feature package is internally layered (`\*.controller`, `\*.service`, `\*.dto`, `\*.entity`, `\*.repository`, `\*.mapper`). This keeps the codebase navigable for both humans and agents — an agent asked to work on trading doesn't need to touch six top-level layer folders.

### Naming

* REST endpoints: plural nouns, kebab-case where multi-word (`/api/v1/trade-journal`).
* DTOs suffixed `Request`/`Response` (e.g., `CreateTradeRequest`, `PortfolioResponse`).
* Entities are plain nouns (`Trade`, `Portfolio`), never suffixed `Entity`.
* Services suffixed `Service`, interfaces for anything with >1 implementation or that needs mocking in tests.

### Validation \& error handling

* Every incoming DTO annotated with Jakarta Validation constraints — no manual `if (x == null) throw` boilerplate where an annotation does the job.
* One global exception handler (`@RestControllerAdvice`). Domain exceptions extend a common `ApexException` hierarchy mapped to appropriate HTTP statuses.

### Database migrations

* Every schema change is a new Flyway migration file (`V{n}\_\_description.sql`), never edit a past migration.
* Migrations reviewed like any other code change.

\---

## 6\. Testing Requirements (mandatory, every phase)

**Unit tests**

* Every service method with non-trivial logic gets a unit test (Mockito for dependencies).
* Analytics calculations (Sharpe ratio, drawdown, win rate, etc.) require unit tests with hand-verified expected values — these are financial formulas, correctness is not optional.

**Integration tests**

* Use **Testcontainers** — real PostgreSQL, Redis, RabbitMQ in test containers, not mocks, for anything touching the database or messaging.
* Every new REST endpoint gets at least one integration test hitting the real Spring context.
* Every multi-tenant-sensitive endpoint gets an explicit cross-tenant isolation test (User A cannot read User B's portfolio/trades/analytics, even by guessing IDs).
* Trade execution gets a concurrency test proving idempotency (same `Idempotency-Key` sent twice → one trade recorded).

**Frontend**

* Component tests (Vitest + RTL) for anything with logic/state, not for pure presentational components.
* Playwright E2E for the critical path only: sign up → deposit virtual cash (auto on signup) → execute a trade → see it reflected in portfolio/analytics.

**Definition of Done for any phase** = code compiles, all unit tests pass, all integration tests pass, Swagger docs reflect new endpoints, and a short manual verification note is provided back to the user. No phase is "done" on code existing alone.

\---

## 7\. Frontend Conventions

### Structure

```
src/
 ├── app/              (routing, layout shells)
 ├── features/         (market, trading, portfolio, analytics, journal, org, auth)
 │    └── <feature>/ (components, hooks, api, types)
 ├── components/ui/    (design-system primitives: Button, Card, Table, Badge, etc.)
 ├── design-system/    (tokens: colors, spacing, typography — single source of truth)
 ├── lib/               (api client, query client config, utils)
 └── store/             (Zustand stores — UI state only, never server data)
```

* **Server state lives in TanStack Query, not Zustand.** Zustand is for UI-only state (sidebar collapsed, active theme, modal open). Don't cache API responses in Zustand — this is a common AI-generated-app mistake and explicitly forbidden here.
* **No inline hex colors or magic spacing values in components.** Everything pulls from the design-system tokens (Tailwind config extended with the token set, not ad hoc arbitrary values like `mt-\[13px]`).
* All numeric/financial values rendered with a shared `<Price>`/`<Percentage>` formatting component — not repeated `toFixed()` calls scattered across components.
* Dark mode is the default theme; build against it first.

### What "not a typical AI-generated app" means concretely

* No purple-gradient-on-white generic SaaS look.
* No unstyled default browser form elements.
* No inconsistent card shadows/radii across screens — one elevation system, applied consistently.
* Every screen has designed loading, empty, and error states — not a bare `Loading...` string.
* Numbers in monospace, aligned in tables (`text-right`, tabular-nums).

\---

## 8\. Git \& Workflow Conventions

* Conventional commits: `feat(trading): add market buy endpoint`, `fix(analytics): correct sharpe ratio denominator`, `test(auth): add cross-tenant isolation test`.
* One phase (per `IMPLEMENTATION\_PLAN.md`) = one feature branch = one PR, even if working solo — keeps history reviewable.
* No direct commits of generated build artifacts, `.env`, or `node\_modules`/`target`.

\---

## 9\. Communication Protocol for the AI Agent

* **After completing each phase, stop.** Do not proceed to the next phase automatically. Summarize: what was built, what was tested (and results), what to manually verify, and any deviations from the phase prompt with justification.
* **If a phase prompt is ambiguous or conflicts with this file or the PRD, state the ambiguity and proceed with the most conservative, PRD-consistent interpretation** rather than guessing silently.
* **If asked to skip tests "to move faster," push back once, explain the risk, then comply only if explicitly reconfirmed** — but note it clearly in the phase summary as a known gap.
* Do not introduce speculative abstractions for requirements that don't exist yet ("might need this later") — YAGNI, except where this file/PRD explicitly calls out a forward-compatible schema decision (e.g., the `Order` table existing ahead of limit-order execution).

\---

## 10\. Reference Documents

* `PRD.md` — full product scope, data model, NFRs.
* `IMPLEMENTATION\_PLAN.md` — the exact sequence of build phases and the prompt to issue for each.

