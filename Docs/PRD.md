# Apex — Product Requirements Document (PRD)

**Version:** 1.0
**Status:** Approved for build
**Owner:** You (Product/Tech Lead)
**Audience:** Engineering team + AI coding agents

---

## 1. Executive Summary

Apex is a **real-time market simulation and portfolio intelligence platform**. It lets users trade with live market data and virtual money, then tells them *why* they're winning or losing — through a real analytics engine, not a balance counter.

Apex is explicitly **not** a "crypto simulator." Crypto is the v1 market data provider; the architecture must treat any asset class (crypto, equities, forex, commodities) as a pluggable provider. This decision is a hard architectural constraint, not a suggestion.

Apex is designed and built as a **multi-tenant enterprise system** — individuals, trading communities, universities, bootcamps, and (later) investment firms are all first-class tenants, not an afterthought bolted on later.

---

## 2. Problem Statement

Two broken extremes exist today:

| Real trading platforms (Binance, Coinbase, Bybit) | Demo/paper trading apps |
|---|---|
| Real money, real fear, no room to fail | No real order execution, no analytics, no risk model |
| Not built for learning | Built for engagement, not skill development |

**Apex's one-sentence mission:** Bridge the gap between beginner trading apps and professional trading platforms by providing a realistic simulation environment with live market data, portfolio management, performance analytics, and risk insight — so users can learn, test strategies, and improve without financial risk.

---

## 3. Goals and Non-Goals

### Goals (v1)
- Realistic trade execution against live market prices (crypto).
- Full portfolio + trade lifecycle management.
- A genuine analytics engine (Sharpe ratio, drawdown, win rate, holding period, risk score).
- Real-time price and portfolio updates via WebSocket.
- Multi-tenant support: individual users, organizations (cohorts/classes/firms), instructor/admin visibility into member performance.
- AI-generated daily/weekly Trade Journal narrative (behavioral analysis, not trade execution).
- Production-grade non-functional posture: security, observability, auditability, horizontal scalability.

### Non-Goals (v1)
- Real money movement of any kind (no payments, no withdrawals).
- Automated/algorithmic trading or AI executing trades on a user's behalf.
- Multi-asset-class support (stocks/forex) — the *architecture* supports it, the *product* does not ship it in v1.
- Native mobile apps.
- Social/copy-trading features (candidate for v2).

---

## 4. Target Users

**Primary personas**
1. **Beginner Trader (Aria, 24)** — new to markets, wants to learn without losing money.
2. **Finance Student (Sam, 20)** — using Apex as part of coursework, cares about metrics that map to what they're taught (Sharpe ratio, drawdown).
3. **Strategy Tester (Vikram, 31)** — has real trading experience, uses Apex to backtest new strategies risk-free before going live elsewhere.

**Secondary personas**
4. **Instructor / Bootcamp Lead (Maria)** — needs to see her cohort's performance, assign challenges, view a leaderboard.
5. **Org Admin (investment firm ops, future)** — onboards new analysts, monitors practice performance before real capital access.

This drives the multi-tenant model in §7.

---

## 5. Product Scope — MVP (v1)

Three core modules, everything else supports them.

```
                         Apex
            ┌─────────────┼─────────────┐
       Market Data     Trading Engine   Analytics Engine
```

### 5.1 Module 1 — Market Data Service
**Purpose:** single source of truth for prices.

- Live price ticks (WebSocket ingestion from provider, e.g. Binance stream, or polling fallback via CoinGecko REST).
- Market overview: top gainers, top losers, trending assets.
- Historical OHLCV data for candlestick charts.
- Symbol metadata (name, precision, min trade size).
- Abstracted behind a `MarketDataProvider` interface — Binance/CoinGecko are implementations, not the contract.

### 5.2 Module 2 — Trading Engine
**Purpose:** own the truth of what a user owns and what they did.

- Instant (market) buy/sell against live price.
- Portfolio: cash balance, holdings, unrealized/realized P/L.
- Trade history (immutable, append-only ledger).
- Order model designed to extend to Limit / Stop-Loss / Take-Profit in v1.1 without schema rewrite (orders table exists in v1 even if only MARKET type is active).
- Idempotent trade execution (client-supplied idempotency key) — a real trading system requirement, not optional.
- Optimistic locking on portfolio balance to prevent race conditions under concurrent trades.

### 5.3 Module 3 — Analytics Engine
**Purpose:** answer "how well am I actually doing," not just "what's my balance."

Computed metrics (v1):
- Total return / return % (daily, weekly, all-time)
- Win rate
- Average win / average loss
- Largest gain / largest loss
- Max drawdown
- Sharpe ratio (using risk-free rate = 0 or configurable)
- Average holding period
- Best-performing asset / worst-performing asset
- Portfolio allocation breakdown
- Daily P/L time series
- A composite "Risk Score" (0–100) derived from concentration, volatility of held assets, and drawdown history

Metrics are computed via scheduled aggregation jobs (not on every request) and cached — see §9.4.

### 5.4 Supporting: AI Trade Journal
Not AI trading — AI *reflection*.

At end-of-day (and on-demand), Apex generates a natural-language behavioral summary from the day's trade data, e.g. identifying patterns like "you consistently lose on trades entered within 5 minutes of a >3% price spike." This is a read-only insight layer built on top of the Analytics Engine's structured output — the LLM never sees raw un-aggregated data it has to interpret from scratch; it receives a structured metrics payload and narrates it. This keeps output deterministic-ish and cheap.

### 5.5 Supporting: Auth & Multi-Tenancy
See §7 — this is core scope, not "later."

### 5.6 Supporting: Notifications
- In-app real-time notifications (trade executed, price alert triggered, daily journal ready) via WebSocket.
- Notification history/read-state persisted.

---

## 6. Out of Scope for v1 (explicitly deferred)
- Limit/Stop/Take-Profit order *execution* (schema exists, execution logic does not)
- Multi-asset classes beyond crypto
- Payments / real money
- Mobile apps
- Social feed, copy trading, public profiles
- Email/SMS notifications (in-app only for v1)

---

## 7. Multi-Tenancy & Enterprise Model

Apex is built so an individual signing up alone and a 40-person bootcamp cohort are the *same* underlying model, not two code paths.

### Entities
- **Organization** — optional. A user can belong to zero or more organizations. `null` org = individual user, fully functional.
- **Membership** — join table: `user_id`, `org_id`, `role`.

### Roles (RBAC)
| Role | Scope | Capability |
|---|---|---|
| `SUPER_ADMIN` | Platform | Full system access, support tooling |
| `ORG_ADMIN` | Org | Manage org members, view all org member performance, configure org settings |
| `INSTRUCTOR` | Org | View (read-only) assigned cohort's performance & leaderboard, cannot trade on their behalf |
| `TRADER` | Self | Default role — trades, sees own data only |

### Enterprise requirements this implies
- Every trading/portfolio/analytics query must be scoped by `(user_id, org_id)` — never assume single-tenant.
- Org-level leaderboard (opt-in, privacy-respecting — user can hide from leaderboard).
- Audit log for privileged actions (an `ORG_ADMIN` viewing a member's portfolio is a loggable event).
- Rate limiting and quotas configurable per org (future: paid orgs get higher API/AI-journal quotas).

This is a genuine architectural decision, not scope creep — it's why the data model in §10 includes `organization_id` as a nullable FK from day one instead of retrofitting it later.

---

## 8. System Architecture

```
                        React Frontend (SPA)
                                │
                        Spring Boot API (REST + WS)
                                │
        ┌───────────────┬───────────────┬────────────────┐
   Authentication   Trading Engine   Analytics Engine   Notifications
                                │
                       Market Data Service
                                │
                     External Market Provider(s)
                                │
        ┌────────────────┬────────────────┬────────────────┐
    PostgreSQL          Redis           RabbitMQ       WebSocket Gateway
```

### Architectural principles (binding)
1. **Layered + hexagonal-lite**: controllers → services → repositories, with external integrations (market data provider, AI provider) behind interfaces so they're swappable and mockable.
2. **Event-driven for side effects**: a trade execution publishes a `TradeExecuted` event to RabbitMQ. Analytics recalculation, notification dispatch, and leaderboard updates are *consumers* of that event, not inline synchronous calls. This keeps the trade execution path fast and lets modules evolve independently.
3. **CQRS-lite for analytics**: writes (trades) go to the transactional ledger; analytics are read from precomputed/cached aggregates, not calculated live on every dashboard load.
4. **Everything money-related uses `BigDecimal`**, never `float`/`double`. Everything time-related is stored and processed in UTC.
5. **API is versioned from day one**: `/api/v1/...`.
6. **Multi-tenant scoping is enforced at the service layer**, not trusted from the client.

### Event flow example
```
Trade Executed
   → publish TradeExecutedEvent (RabbitMQ)
       → Analytics Consumer: recompute portfolio snapshot
       → Notification Consumer: push WS notification "Trade executed"
       → Leaderboard Consumer: update org leaderboard cache (if applicable)
```

---

## 9. Non-Functional Requirements

### 9.1 Performance
- Price tick → WebSocket delivery to client: < 500ms p95.
- Trade execution API: < 300ms p95 (excluding external market data latency).
- Dashboard load (cached analytics): < 200ms p95.

### 9.2 Scalability
- Stateless API layer — horizontally scalable behind a load balancer.
- WebSocket sessions tracked via Redis (pub/sub) so any API instance can broadcast, not just the instance holding the socket — required for multi-instance deployment.
- Market data ingestion is a single dedicated consumer process (avoid duplicate external API subscriptions per instance).

### 9.3 Security
- JWT (short-lived access token + refresh token), stored appropriately (httpOnly cookie preferred over localStorage — document trade-off in AGENTS.md).
- Passwords: bcrypt/Argon2, never reversible.
- RBAC enforced via method-level Spring Security annotations, not just route guards.
- Input validation on every DTO (Jakarta Validation).
- Rate limiting per user/IP on auth and trade-execution endpoints (prevent brute force / trade spam).
- All secrets via environment variables / secret manager — never committed, never hardcoded.
- Audit log for privileged/cross-user data access.

### 9.4 Caching Strategy (Redis)
- Live prices: cached with short TTL, updated by the market data ingestion consumer.
- Precomputed analytics snapshots per user (recomputed async on `TradeExecuted`, not on read).
- Leaderboards (sorted sets).
- Session/rate-limit counters.

### 9.5 Observability
- Structured JSON logging with correlation/request IDs.
- Health check endpoints (`/actuator/health`).
- Metrics exposed for scraping (Micrometer → Prometheus-compatible).
- Every RabbitMQ consumer must be idempotent and have dead-letter handling.

### 9.6 Data Integrity
- Trade ledger is **append-only** — trades are never updated or deleted, only reversed/annotated (auditability requirement, standard in finance systems).
- Portfolio balance updates use optimistic locking (`@Version`) to prevent lost updates under concurrent requests.
- Idempotency keys required on trade execution to prevent duplicate execution on client retry.

---

## 10. Core Data Model (v1)

```
User (id, email, password_hash, display_name, role, organization_id[nullable], created_at)
Organization (id, name, type[INDIVIDUAL/BOOTCAMP/UNIVERSITY/FIRM], created_at)
Membership (id, user_id, organization_id, role)
Portfolio (id, user_id, cash_balance, version, created_at)
Asset (id, symbol, name, precision, provider_source)
Holding (id, portfolio_id, asset_id, quantity, avg_entry_price)
Trade (id, portfolio_id, asset_id, side[BUY/SELL], quantity, price, fee, idempotency_key, executed_at)  -- append-only
Order (id, portfolio_id, asset_id, type[MARKET/LIMIT/STOP_LOSS/TAKE_PROFIT], status, params, created_at) -- schema-ready, only MARKET active in v1
PerformanceSnapshot (id, portfolio_id, date, return_pct, win_rate, sharpe_ratio, max_drawdown, risk_score, ...) -- precomputed, async
Watchlist (id, user_id, asset_id)
TradeJournalEntry (id, user_id, date, narrative_text, generated_at)
Notification (id, user_id, type, payload, read_at, created_at)
AuditLog (id, actor_user_id, action, target_user_id, metadata, created_at)
```

---

## 11. API Design Principles

- REST, versioned: `/api/v1/...`
- Errors follow RFC 7807 (`application/problem+json`): `type`, `title`, `status`, `detail`, `instance`.
- Pagination: cursor or page/size query params on all list endpoints — never return unbounded lists.
- All mutating endpoints (`POST`/`PUT`/`DELETE`) require auth; trade execution requires `Idempotency-Key` header.
- OpenAPI/Swagger auto-generated and kept in sync — no hand-maintained API docs.

---

## 12. Tech Stack (final)

| Layer | Technology | Notes |
|---|---|---|
| Backend language | Java 21 | LTS |
| Framework | Spring Boot 4.x | Jackson 3 ships by default; annotations in `com.fasterxml.jackson.annotation`, core databind in `tools.jackson.databind` — verify imports when writing new code |
| Security | Spring Security + JWT | access + refresh token |
| ORM | Spring Data JPA / Hibernate | |
| Migrations | **Flyway** | schema is version-controlled, never `ddl-auto=update` in real environments |
| Validation | Jakarta Validation | |
| Database | PostgreSQL 16 | |
| Cache | Redis 7 | prices, analytics snapshots, leaderboards, rate limits |
| Messaging | RabbitMQ | async event processing |
| Real-time | WebSocket (STOMP over SockJS or raw) + Redis pub/sub for multi-instance fan-out | |
| Frontend | React 18 + TypeScript | TypeScript is mandatory, not optional |
| Styling | Tailwind CSS + a defined design token system | see §13 |
| Charts | Recharts / lightweight-charts (for candlesticks) | |
| State/data | TanStack Query (server state) + Zustand (UI state) | avoid prop-drilling and avoid over-using global state for server data |
| API docs | springdoc-openapi (Swagger UI) | |
| Build | Maven | |
| Containers | Docker | |
| Orchestration (local) | Docker Compose | one command boots Postgres, Redis, RabbitMQ, API |
| CI | GitHub Actions | lint + unit + integration tests on every PR |
| Testing (backend) | JUnit 5, Mockito, **Testcontainers** (real Postgres/Redis/RabbitMQ in integration tests, not mocks) | |
| Testing (frontend) | Vitest + React Testing Library, Playwright for critical E2E flows | |

---

## 13. Frontend Product Requirements

Apex's frontend must **not** look like a generic AI-generated dashboard. It should read as a professional trading terminal: dense but organized, calm, data-first.

### 13.1 Design direction
- **Dark-mode-first** (professional trading tools default to dark; light mode as secondary theme, not primary).
- A restrained, deliberate color system:
  - Neutral base (near-black backgrounds, layered surface elevation via subtle grays, not pure black/white).
  - One accent color for brand/primary actions.
  - Semantic colors reserved *only* for financial meaning: a green for gains, a red for losses, used consistently and nowhere else in the UI (don't use "success green" for unrelated UI success states — in a trading app, green/red are financial signals and must stay unambiguous).
- Typography: a technical/geometric sans for UI (e.g., Inter) + a monospace font for all numeric/price/ticker data — numbers in trading UIs are read faster in tabular monospace figures.
- Data density done right: real trading terminals (Bloomberg, TradingView, institutional dashboards) are information-dense but *grid-aligned and rhythmic* — not cluttered. Consistent spacing scale, consistent card/panel system.
- Motion is functional, not decorative: price ticks flash briefly on update, panels don't bounce or over-animate.

### 13.2 Core screens (v1)
1. **Dashboard** — portfolio value, daily P/L, allocation chart, watchlist ticker, recent trades.
2. **Market** — live price table, gainers/losers, search, candlestick chart on asset click.
3. **Trade** — asset detail + order panel (market buy/sell), live order book/price if available.
4. **Portfolio** — holdings table, trade history, realized/unrealized P/L.
5. **Analytics** — Sharpe ratio, drawdown chart, win rate, risk score, best/worst assets.
6. **Trade Journal** — AI-generated daily narrative + historical entries.
7. **Org/Leaderboard** (if member of an org) — cohort performance view.
8. **Admin** (org admin/instructor only) — member management, audit log.

### 13.3 Non-negotiable UI standards
- Fully responsive, but the design *target* is desktop-first (this is a terminal-style tool; mobile is a secondary, simplified view, not the primary design surface).
- Loading and empty states designed intentionally for every screen — never a bare spinner with no context.
- Accessible: proper contrast ratios even in dark mode, keyboard navigable, semantic HTML.
- A real design system with tokens (spacing, radius, color, typography scale) defined once and consumed everywhere — no ad hoc inline magic numbers.

---

## 14. Success Metrics (Product KPIs)

- Activation: % of signups that execute a first trade within 24h.
- Retention: % of users with a trade in the last 7 days / 30 days.
- Engagement depth: average number of Analytics/Trade Journal views per active user per week (signals the product is being used for *learning*, not just trading).
- Org adoption: number of orgs created, average org size, instructor engagement (leaderboard/admin views).

---

## 15. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| External market data provider rate limits / outages | Abstract behind `MarketDataProvider` interface; cache last-known price; support provider fallback |
| Race conditions on concurrent trades corrupting balance | Optimistic locking + idempotency keys + append-only ledger |
| Analytics computation becomes a bottleneck at scale | Async precomputation via RabbitMQ, cached snapshots, never computed synchronously on request |
| AI Trade Journal hallucinating incorrect numbers | LLM only narrates a structured, pre-validated metrics payload — it never invents figures itself |
| Multi-tenant data leakage | Service-layer scoping enforced everywhere + integration tests specifically asserting cross-tenant isolation |

---

## 16. Roadmap Beyond v1 (not built now, but architecture must not block these)

- Limit/Stop-Loss/Take-Profit order execution
- Additional market providers (equities, forex)
- Public/social leaderboards, copy-trading
- Paid org tiers with quota management
- Email/push notifications

---

*This PRD is the source of truth for scope. The companion `AGENTS.md` defines how the AI coding agent must build it, and `IMPLEMENTATION_PLAN.md` defines the order and testable checkpoints.*
