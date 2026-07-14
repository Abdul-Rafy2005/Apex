# Apex — Phase-by-Phase Build Prompts

How to use this file:
1. Make sure `PRD.md` and `AGENTS.md` are in the repo root — reference them in every prompt (already done below).
2. Copy **one phase's prompt at a time** into your IDE agent. Do not batch phases.
3. After the agent reports back, actually run the app / tests yourself and confirm before moving to the next phase.
4. Each prompt ends with an explicit instruction to stop and report — do not let the agent chain into the next phase.

Every phase prompt assumes the agent has read `AGENTS.md` and `PRD.md` in full and will follow them without restating them.

---

## Phase 0 — Repository Scaffolding & Infrastructure

```
Read AGENTS.md and PRD.md in full before starting.

Set up the Apex monorepo skeleton. Do not build any feature logic yet — this phase is
purely infrastructure and scaffolding.

Scope:
1. Create the repo structure: /backend (Spring Boot, Java 21, Maven) and /frontend
   (React 18 + TypeScript, Vite).
2.    Backend: initialize Spring Boot 4.x with dependencies for Web, Security, Data JPA,
   Validation, PostgreSQL driver, Flyway, springdoc-openapi, Lombok, Testcontainers
   (test scope). Set up the feature-based package structure defined in AGENTS.md
   section 5 with empty placeholder packages (auth, organization, market, trading,
   analytics, journal, notification, common).
3. Frontend: initialize with Vite + React + TypeScript (strict mode), Tailwind CSS,
   TanStack Query, Zustand, React Router. Set up the folder structure defined in
   AGENTS.md section 7. Create the design-system token file (colors, spacing,
   typography, radius, dark-mode-first) as a starting point — no components yet.
4. docker-compose.yml at repo root: PostgreSQL 16, Redis 7, RabbitMQ (with management
   plugin), and the backend service. One command (`docker compose up`) should boot
   the full local environment.
5. Backend application.yml with profiles for local/test, reading DB/Redis/RabbitMQ
   connection info from environment variables (with sensible local defaults).
   Create .env.example.
6. Set up Flyway with an initial empty baseline migration.
7. Set up GitHub Actions CI workflow: on PR, run backend build + unit tests, and
   frontend lint + type-check + unit tests.
8. Write a root README.md with setup instructions (prereqs, how to run locally,
   how to run tests).

Testing requirement for this phase:
- A trivial backend health-check integration test (Testcontainers-backed, hits
  /actuator/health) proving the Spring context boots against a real containerized
  Postgres.
- A trivial frontend test proving the app renders without crashing.
- Confirm `docker compose up` successfully starts all services and the backend
  connects to Postgres/Redis/RabbitMQ on startup (log evidence).

When done, STOP and report back:
- Exact commands to run the app locally and to run the test suites.
- Confirmation all tests pass.
- Any deviations from AGENTS.md/PRD.md and why.
Do not proceed to Phase 1 until I confirm.
```

---

## Phase 1 — Auth, Users & Multi-Tenant Foundation

```
Read AGENTS.md and PRD.md before starting. This phase builds on Phase 0's scaffolding.

Scope (see PRD sections 7 and 10 for the data model):
1. Flyway migrations for: users, organizations, memberships, portfolios (create with
   a starting virtual cash balance on user signup — no payment flow, just seed
   balance, e.g. 100,000 virtual USD, configurable via application property).
2. Entities, repositories, DTOs, mappers for User, Organization, Membership,
   Portfolio per AGENTS.md conventions (DTOs never leak entities).
3. Registration and login endpoints: POST /api/v1/auth/register, POST
   /api/v1/auth/login, POST /api/v1/auth/refresh. Passwords hashed with
   BCrypt/Argon2. JWT access + refresh token issuance.
4. Spring Security configuration: JWT filter, method-level RBAC using the roles
   defined in PRD section 7 (SUPER_ADMIN, ORG_ADMIN, INSTRUCTOR, TRADER).
   Registration defaults to TRADER with no organization (individual user).
5. Organization endpoints: create organization, invite/join, list members
   (ORG_ADMIN/INSTRUCTOR only), per RBAC rules.
6. GET /api/v1/users/me returning the authenticated user's profile + portfolio
   summary.
7. Global exception handling (RFC 7807 problem+json) via @RestControllerAdvice,
   used from this phase onward for all errors.
8. Swagger/OpenAPI docs must reflect all new endpoints.

Testing requirement for this phase:
- Unit tests: password hashing, JWT generation/validation, RBAC authorization logic.
- Integration tests (Testcontainers, real Postgres): registration, login, refresh
  flow end-to-end; invalid credentials rejected; duplicate email rejected.
- Integration test: cross-tenant isolation — User A (org X) cannot access User B's
  (org Y) profile or organization data, and a TRADER cannot call ORG_ADMIN-only
  endpoints.
- Integration test: organization creation and membership role assignment.

When done, STOP and report back:
- Summary of endpoints created (link to Swagger UI path).
- Test results (all passing).
- How to manually verify: exact curl/Postman steps to register, log in, and hit
  a protected endpoint.
- Any deviations and why.
Do not proceed to Phase 2 until I confirm.
```

---

## Phase 2 — Market Data Service

```
Read AGENTS.md and PRD.md before starting (PRD section 5.1).

Scope:
1. Define the MarketDataProvider interface (fetch live price, historical
   OHLCV, top gainers/losers, symbol metadata) per AGENTS.md section 3 — this
   interface is the contract, not a specific provider.
2. Implement one concrete provider (CoinGecko REST is simplest to start reliably;
   Binance WebSocket can be added as a second implementation in this phase or
   flagged as a fast-follow — use your judgement and state the choice).
3. Asset entity/migration (symbol, name, precision, provider_source) and an
   endpoint to list supported/tradable assets: GET /api/v1/market/assets.
4. GET /api/v1/market/prices (live prices, cached in Redis with a short TTL —
   do not call the external provider on every request).
5. GET /api/v1/market/overview (top gainers/losers/trending).
6. GET /api/v1/market/{symbol}/history?interval=... for OHLCV candlestick data.
7. A scheduled/background job (or dedicated ingestion component) that refreshes
   the Redis price cache periodically — this is the single ingestion point, not
   per-request fan-out to the external API (see AGENTS.md - avoid duplicate
   external subscriptions).
8. Basic resilience: if the external provider fails/rate-limits, serve last-known
   cached price rather than erroring the whole endpoint, and log the degradation.

Testing requirement for this phase:
- Unit tests for the MarketDataProvider implementation's parsing/mapping logic,
  with the actual HTTP client mocked (no real network calls in unit tests).
- Integration test (Testcontainers Redis): price caching behavior — first call
  populates cache, subsequent calls within TTL don't re-hit the (mocked) provider.
- Integration test: /api/v1/market/assets and /api/v1/market/prices return
  expected shape and are covered by Swagger docs.
- A test proving the fallback-to-last-known-price behavior when the provider
  call fails.

When done, STOP and report back:
- Which provider was implemented and why.
- Test results.
- How to manually verify live prices are updating (steps + expected output).
- Any deviations and why.
Do not proceed to Phase 3 until I confirm.
```

---

## Phase 3 — Trading Engine (Portfolio, Trades, Balance)

```
Read AGENTS.md and PRD.md before starting (PRD section 5.2, and the Golden Rules
in AGENTS.md section 2 — pay particular attention to BigDecimal, idempotency,
optimistic locking, and the append-only ledger rule).

Scope:
1. Migrations for holdings, trades (append-only), and orders (schema only — only
   MARKET type is functionally executed in this phase; LIMIT/STOP_LOSS/
   TAKE_PROFIT rows can exist in the schema but have no execution logic yet).
2. POST /api/v1/trading/execute — executes a market buy or sell against the
   current cached price from the Market Data Service. Requires an
   Idempotency-Key header; duplicate keys return the original result, not a
   second trade.
3. Portfolio balance updates use optimistic locking (@Version) on Portfolio.
   Write a concurrency test proving two simultaneous trade requests don't
   corrupt the balance.
4. GET /api/v1/portfolio — current holdings, cash balance, unrealized P/L
   (computed against live prices at request time, cheap enough to be
   synchronous — this is distinct from the heavier Analytics Engine in Phase 5).
5. GET /api/v1/portfolio/trades — paginated trade history.
6. Validation: cannot sell more than currently held; cannot buy with
   insufficient cash balance; asset must be tradable (exists in Market Data's
   asset list).
7. Publish a TradeExecuted event to RabbitMQ on successful trade (consumers
   come in later phases — for now, just publish it and log receipt with a
   placeholder consumer to prove the pipe works end-to-end).

Testing requirement for this phase:
- Unit tests: trade validation logic (insufficient funds, insufficient holdings,
  invalid asset), P/L calculation logic.
- Integration tests (Testcontainers: Postgres + RabbitMQ): full buy → sell
  lifecycle updates portfolio and holdings correctly; trade history reflects
  append-only records; idempotency key replay returns the same trade, does not
  create a duplicate.
- Concurrency integration test: fire two concurrent buy requests against the
  same portfolio with limited cash, confirm no overdraft and correct final
  balance (proves optimistic locking works).
- Integration test confirming a TradeExecuted message is published and consumed
  by the placeholder consumer.

When done, STOP and report back:
- Summary of endpoints and event flow.
- Test results, including the concurrency test outcome specifically.
- How to manually verify a trade end-to-end.
- Any deviations and why.
Do not proceed to Phase 4 until I confirm.
```

---

## Phase 4 — Real-Time Gateway (WebSocket + Redis Pub/Sub)

```
Read AGENTS.md and PRD.md before starting (PRD sections 8 and 9.2).

Scope:
1. WebSocket configuration (STOMP/SockJS) with JWT-authenticated connections
   (reuse the auth mechanism from Phase 1 — no separate auth scheme).
2. Live price broadcast: as the Market Data ingestion job (Phase 2) refreshes
   prices, broadcast updates to subscribed clients on a per-symbol topic.
3. Portfolio update broadcast: when a TradeExecuted event is consumed, push a
   real-time portfolio update to that specific user's session.
4. Use Redis pub/sub as the backplane so this works correctly across multiple
   backend instances, not just a single-instance in-memory broadcast (this is a
   direct requirement from AGENTS.md/PRD NFRs — do not skip it even though it
   adds complexity, and explain your implementation choice in the report).
5. Frontend: a WebSocket client hook/service that subscribes to price and
   portfolio topics and exposes the data via TanStack Query cache updates or a
   dedicated real-time store — follow the state-management rules in AGENTS.md
   section 7 (server-derived data should still flow through TanStack Query
   patterns, not become an ad hoc parallel state system).

Testing requirement for this phase:
- Integration test: authenticated WebSocket connection succeeds, unauthenticated
  connection is rejected.
- Integration test: a simulated price update results in a message delivered to
  a subscribed test client.
- Integration test: a simulated TradeExecuted event results in a portfolio
  update delivered only to the owning user's session (not broadcast to others).
- Frontend test: the WebSocket hook correctly updates local state/cache on a
  mocked incoming message.

When done, STOP and report back:
- How the multi-instance fan-out was implemented (Redis pub/sub mechanics).
- Test results.
- How to manually verify: open two browser sessions, confirm price ticks and
  trade-triggered portfolio updates appear live without refresh.
- Any deviations and why.
Do not proceed to Phase 5 until I confirm.
```

---

## Phase 5 — Analytics Engine

```
Read AGENTS.md and PRD.md before starting (PRD section 5.3 — the exact metric
list is authoritative). Analytics correctness matters — these are real financial
formulas, verify them by hand before writing tests, don't just assert whatever
the code happens to output.

Scope:
1. PerformanceSnapshot migration/entity per PRD section 10.
2. A RabbitMQ consumer on TradeExecuted (and a scheduled daily job) that
   recomputes and persists a snapshot: total return, win rate, avg win/loss,
   largest gain/loss, max drawdown, Sharpe ratio, avg holding period,
   best/worst asset, allocation breakdown, daily P/L series, composite risk
   score. Document the exact formula used for each in code comments and in
   your phase report.
3. GET /api/v1/analytics/summary — returns the latest snapshot (served from
   the precomputed table/cache, not calculated live).
4. GET /api/v1/analytics/history — time series for charting (daily P/L, return
   over time).
5. Cache the summary response in Redis with invalidation on new snapshot
   creation.

Testing requirement for this phase:
- Unit tests for EVERY metric calculation with hand-computed expected values
  from a fixed set of sample trades (this is the most important test coverage
  in the whole project — do not skip edge cases like zero trades, all-losing
  trades, or a single trade).
- Integration test: executing trades (reuse Phase 3 flow) triggers the
  consumer and results in a new, correct PerformanceSnapshot row.
- Integration test: /api/v1/analytics/summary reflects cached data and is
  correctly invalidated after a new snapshot.
- Cross-tenant isolation test: User A cannot fetch User B's analytics.

When done, STOP and report back:
- The exact formulas used for each metric (so I can sanity-check them).
- Test results.
- How to manually verify: sample trade sequence and expected analytics output.
- Any deviations and why.
Do not proceed to Phase 6 until I confirm.
```

---

## Phase 6 — AI Trade Journal

```
Read AGENTS.md and PRD.md before starting (PRD section 5.4). This is a
narration layer over Phase 5's output, not a new data-analysis system — the
LLM receives structured, already-correct metrics and narrates them, it does
not compute anything itself.

Scope:
1. TradeJournalEntry migration/entity.
2. AiJournalGenerator interface (per AGENTS.md section 3 — swappable/mockable),
   with a concrete implementation calling the Anthropic API. Build a structured
   prompt that passes the day's trade summary + relevant PerformanceSnapshot
   fields and asks for a short behavioral narrative (2-4 sentences), explicitly
   instructing the model not to invent numbers not present in the input.
3. A scheduled daily job that generates a journal entry per active user
   (user had at least one trade that day) and stores it.
4. POST /api/v1/journal/generate — on-demand generation for the current day
   (rate-limited to prevent abuse, e.g. once per hour).
5. GET /api/v1/journal — paginated history of a user's journal entries.
6. On generation, publish a notification (ties into Phase 7, but publish the
   event now even if Phase 7's consumer doesn't exist yet — same pattern as
   Phase 3's TradeExecuted event).

Testing requirement for this phase:
- Unit test: prompt construction from a given metrics payload produces the
  expected structured input (mock the LLM call itself — do not hit the real
  API in unit tests).
- Integration test: the AiJournalGenerator interface can be swapped for a fake
  implementation in tests (proves the abstraction actually works, not just
  exists).
- Integration test: on-demand generation endpoint respects the rate limit.
- Integration test: journal history endpoint is correctly scoped per user.

When done, STOP and report back:
- Sample generated journal output from a real test run.
- Test results.
- Any deviations and why (including if you used a different LLM
  integration approach and why).
Do not proceed to Phase 7 until I confirm.
```

---

## Phase 7 — Notifications & Event Consumers Completion

```
Read AGENTS.md and PRD.md before starting (PRD section 5.6 and the event flow
diagram in section 8). This phase wires up the notification consumers that
prior phases published events for but didn't yet fully consume.

Scope:
1. Notification migration/entity.
2. RabbitMQ consumers for: TradeExecuted → "trade executed" notification;
   Journal generated → "journal ready" notification. Ensure consumers are
   idempotent and dead-letter-queue configured per AGENTS.md section 3.
3. Real-time delivery of new notifications via the WebSocket gateway from
   Phase 4 (per-user topic).
4. GET /api/v1/notifications (paginated, unread-first) and
   PATCH /api/v1/notifications/{id}/read.
5. Org leaderboard: a Redis sorted-set-backed leaderboard updated on
   PerformanceSnapshot creation (opt-in — respect a user's
   leaderboard-visibility preference from their profile).
6. GET /api/v1/organizations/{id}/leaderboard (ORG_ADMIN/INSTRUCTOR/member
   visibility per RBAC rules).

Testing requirement for this phase:
- Integration tests for each consumer: message in → correct notification row
  created → correct WebSocket delivery to the right user only.
- Integration test: duplicate message delivery (simulate redelivery) does not
  create duplicate notifications (idempotency).
- Integration test: leaderboard reflects snapshot updates and respects
  opt-out visibility.
- Cross-tenant test: a user cannot see another org's leaderboard.

When done, STOP and report back:
- Test results.
- How to manually verify real-time notification delivery.
- Any deviations and why.
Do not proceed to Phase 8 until I confirm.
```

---

## Phase 8 — Frontend Foundation (Design System, Shell, Auth Screens)

```
Read AGENTS.md and PRD.md before starting (PRD section 13 is authoritative for
visual direction — dark-mode-first, monospace numerics, restrained semantic
color use, terminal-grade density done cleanly, not generic AI-app aesthetics).

Scope:
1. Build out components/ui: Button, Input, Card, Table (with tabular-nums
   support), Badge, Modal, Tabs, Toast/notification component, Skeleton
   loading states — all driven by the design-system tokens from Phase 0, no
   inline arbitrary Tailwind values.
2. App shell: sidebar navigation, top bar (portfolio value snapshot, user
   menu, notification bell), responsive breakpoint behavior per PRD 13.3
   (desktop-first, simplified mobile view).
3. Auth screens: login, register, using the Phase 1 backend. Token
   storage/refresh handling per the security posture noted in AGENTS.md.
4. Routing structure for all screens listed in PRD 13.2 (build the shells/
   routes now; full screen content comes in later phases) with route guards
   based on auth state and role.
5. Global error boundary and toast notification system wired to API error
   responses (RFC 7807 shape from the backend).

Testing requirement for this phase:
- Component tests for the core design-system primitives (Button, Table,
  Input at minimum).
- Component test for the login/register flow (mocked API) covering success
  and validation-error states.
- Playwright E2E: unauthenticated user is redirected to login; after login,
  lands on the dashboard shell.

When done, STOP and report back:
- Screenshots or a description of the visual result (colors, typography,
  layout) so I can sanity-check the design direction before more screens are
  built on top of it.
- Test results.
- Any deviations and why.
Do not proceed to Phase 9 until I confirm.
```

---

## Phase 9 — Frontend: Market & Dashboard Screens

```
Read AGENTS.md and PRD.md before starting.

Scope:
1. Dashboard screen (PRD 13.2.1): portfolio value, daily P/L, allocation
   chart, watchlist ticker, recent trades — wired to Phase 3/5 backend data
   via TanStack Query, with live updates via the Phase 4 WebSocket hook.
2. Market screen (PRD 13.2.2): live price table (sortable), gainers/losers,
   search/filter, candlestick chart component (Recharts or lightweight-charts)
   on asset selection, wired to Phase 2 endpoints.
3. Proper loading/empty/error states for every data-fetching component per
   AGENTS.md section 7.
4. Price flash-on-update micro-interaction (per PRD 13.1 — functional motion,
   not decorative).

Testing requirement for this phase:
- Component tests for the price table and dashboard summary cards (mocked
  data, including edge cases: empty portfolio, zero trades).
- Playwright E2E: navigating to Market, searching for an asset, viewing its
  chart.

When done, STOP and report back:
- Test results.
- Any deviations and why.
Do not proceed to Phase 10 until I confirm.
```

---

## Phase 10 — Frontend: Trading Screen

```
Read AGENTS.md and PRD.md before starting.

Scope:
1. Trade screen (PRD 13.2.3): asset detail view, order panel for market
   buy/sell, live price display, quantity/cash input with validation
   mirroring backend rules (insufficient funds/holdings) for immediate
   feedback before hitting the API.
2. Idempotency-Key generation on the client for each trade submission
   (per AGENTS.md Golden Rule 5) — must be a fresh key per logical submission,
   reused only on automatic retry of the same submission.
3. Optimistic UI feedback on trade submission with reconciliation once the
   real-time portfolio update arrives via WebSocket.
4. Trade confirmation and error toast handling.

Testing requirement for this phase:
- Component tests: order panel validation logic (insufficient funds shown
  correctly, quantity limits).
- Playwright E2E: full trade flow — search asset, submit a buy, see it
  reflected in portfolio without a page refresh.

When done, STOP and report back:
- Test results.
- Any deviations and why.
Do not proceed to Phase 11 until I confirm.
```

---

## Phase 11 — Frontend: Portfolio, Analytics & Trade Journal Screens

```
Read AGENTS.md and PRD.md before starting.

Scope:
1. Portfolio screen (PRD 13.2.4): holdings table, trade history (paginated),
   realized/unrealized P/L.
2. Analytics screen (PRD 13.2.5): Sharpe ratio, drawdown chart, win rate,
   risk score, best/worst asset — visualized clearly, not just a stat wall;
   at least one chart (e.g., equity curve / daily P/L over time, drawdown
   chart).
3. Trade Journal screen (PRD 13.2.6): today's entry prominently displayed,
   paginated history below, manual "generate now" action wired to the
   rate-limited endpoint from Phase 6 with appropriate disabled/cooldown UI
   state.

Testing requirement for this phase:
- Component tests for analytics chart data transformation logic (not the
  chart rendering itself, but the data-shaping functions feeding it).
- Playwright E2E: view analytics after executing trades from Phase 10's test,
  confirm numbers are non-zero and sensible; view a generated journal entry.

When done, STOP and report back:
- Test results.
- Any deviations and why.
Do not proceed to Phase 12 until I confirm.
```

---

## Phase 12 — Organization / Admin Screens (Enterprise Features)

```
Read AGENTS.md and PRD.md before starting (PRD section 7 is authoritative for
roles and permissions — enforce the same RBAC on the frontend as a UX layer,
never as the actual security boundary, which already lives server-side).

Scope:
1. Organization screen: for ORG_ADMIN/INSTRUCTOR — member list, leaderboard
   (from Phase 7 backend), audit log view (ORG_ADMIN only).
2. Org creation/join flow for regular users.
3. Role-conditional navigation (a TRADER never sees admin nav items — but
   remember the backend already enforces this; this is purely UX).
4. Leaderboard opt-out toggle in user settings, wired to the backend
   visibility preference from Phase 7.

Testing requirement for this phase:
- Component tests: role-conditional rendering logic.
- Playwright E2E: as an org admin test user, view the leaderboard and member
  list; as a trader test user, confirm admin nav/routes are inaccessible.

When done, STOP and report back:
- Test results.
- Any deviations and why.
Do not proceed to Phase 13 until I confirm.
```

---

## Phase 13 — Hardening: Security, Rate Limiting, Observability, Deploy Readiness

```
Read AGENTS.md and PRD.md before starting (PRD section 9 is authoritative).
This phase does not add product features — it makes what exists production-
ready.

Scope:
1. Rate limiting on auth endpoints and trade execution (per user/IP), backed
   by Redis counters.
2. Structured JSON logging with correlation/request IDs across all requests
   and RabbitMQ consumers.
3. Metrics exposed via Micrometer (Prometheus-compatible) for: trade
   execution latency, WebSocket message throughput, RabbitMQ consumer lag,
   cache hit/miss rates.
4. Security review pass: confirm no secrets committed, confirm CORS
   configuration is explicit (not wildcard) for production, confirm all
   privileged endpoints have both an authorization check AND an integration
   test proving it.
5. Full audit log review: confirm every privileged cross-user data access
   (e.g., ORG_ADMIN viewing a member's portfolio) is logged.
6. Production Dockerfiles (multi-stage builds) for backend and frontend, and
   a production-oriented docker-compose or deployment manifest (document
   assumptions — this doesn't need to target a specific cloud provider unless
   you tell the agent to).
7. Final pass on OpenAPI docs completeness.

Testing requirement for this phase:
- Integration tests for rate limiting behavior (requests beyond threshold are
  rejected with the correct status).
- A full test-suite run across the entire backend and frontend, confirming
  nothing regressed across all 13 phases.
- Manual security checklist walkthrough, documented in the report.

When done, STOP and report back:
- Full test suite results (all phases).
- The security checklist with pass/fail per item.
- Any remaining known gaps or deferred items (per PRD section 16, roadmap
  items are expected to be absent — confirm they are cleanly absent, not
  half-built).
This is the final phase — no further auto-proceeding.
```

---

## Notes on running this sequence

- **Do not let the agent skip ahead** even if it thinks it can — the stop-and-report checkpoints exist so you can catch drift early, especially around the financial-correctness pieces (Phase 3 concurrency, Phase 5 formulas).
- If a phase's report reveals a deviation you don't like, correct it **before** starting the next phase — later phases build on earlier ones, so compounding drift gets expensive to unwind.
- Phases 8–12 (frontend) can technically start once their corresponding backend phase is done, without waiting for all backend phases — but building backend 0-7 fully first, then frontend 8-12, keeps the AI agent's context focused and reduces cross-phase confusion. Reorder only if you're comfortable managing that complexity yourself.
