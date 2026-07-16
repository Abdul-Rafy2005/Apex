<div align="center">

# Apex

**Real-Time Market Simulation & Portfolio Intelligence Platform**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React_19-61DAFB?style=flat-square&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

Trade with live market data and virtual money. Get AI-powered insights on your performance.

[Getting Started](#getting-started) · [API Documentation](#api-documentation) · [Architecture](#architecture) · [Contributing](#contributing)

</div>

---

## Overview

Apex bridges the gap between beginner trading apps and professional platforms. It provides a realistic simulation environment with live market data, portfolio management, performance analytics, and risk insights — so users can learn, test strategies, and improve without financial risk.

**Key differentiators:**

- **Real analytics, not toy metrics** — Sharpe ratio, max drawdown, win rate, risk scoring, FIFO-matched P/L
- **Multi-tenant by design** — organizations, cohorts, and firms are first-class tenants
- **AI trade journal** — behavioral narratives generated from your daily performance
- **Real-time updates** — live price ticks and portfolio changes via WebSocket
- **Production-grade** — idempotent trade execution, cross-tenant isolation, append-only ledger

## Features

| Module | Capabilities |
|--------|-------------|
| **Auth & RBAC** | JWT authentication, role-based access (Super Admin, Org Admin, Instructor, Trader), multi-tenant organizations |
| **Market Data** | Live prices via CoinGecko, Redis-cached with fallback, historical charts, asset catalog |
| **Trading Engine** | Idempotent market orders, optimistic locking, append-only ledger, fee calculation |
| **Portfolio** | Real-time holdings, unrealized P/L, cash balance management |
| **Analytics** | Sharpe ratio, max drawdown, win rate, risk score, performance snapshots (async via RabbitMQ) |
| **AI Journal** | Daily behavioral narratives powered by Claude, rate-limited generation |
| **Notifications** | Trade execution alerts, journal ready events, per-user read tracking |
| **Leaderboard** | Org-level rankings, opt-out support, visibility toggle |
| **Audit Log** | Organization-level audit trail with pagination |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 4.x, Spring Security, Spring Data JPA, Flyway |
| **Data** | PostgreSQL 16, Redis 7, RabbitMQ |
| **Real-time** | WebSocket (STOMP/SockJS), Redis pub/sub fan-out |
| **Frontend** | React 19, TypeScript (strict), Tailwind CSS, TanStack Query, Zustand |
| **Testing** | JUnit 5, Mockito, Testcontainers / Vitest, React Testing Library |
| **Infrastructure** | Docker, Docker Compose, GitHub Actions CI |

## Getting Started

### Prerequisites

- Java 21 (JDK)
- Node.js 20+ and npm
- Docker and Docker Compose
- Maven (or use the included `mvnw` wrapper)

### Quick Start

**1. Start infrastructure**

```bash
docker compose up -d postgres redis rabbitmq
```

Wait for health checks to pass (~10 seconds).

**2. Configure environment**

```bash
cd Backend
cp .env.example .env
# Edit .env with your settings (JWT_SECRET is required)
```

**3. Run the backend**

```bash
./mvnw spring-boot:run
```

API available at `http://localhost:8080` · Swagger UI at `http://localhost:8080/api/v1/swagger-ui.html`

**4. Run the frontend**

```bash
cd frontend
npm install
npm run dev
```

App available at `http://localhost:5173`

**5. Full stack with Docker**

```bash
docker compose up --build
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `apex` | Database name |
| `DB_USER` | `apex` | Database user |
| `DB_PASSWORD` | `apex` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `JWT_SECRET` | — | **Required.** Secret key for JWT signing (min 32 chars) |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Allowed CORS origin |
| `ANTHROPIC_API_KEY` | — | Optional. Enables AI journal generation |

## API Documentation

### Base URL

```
http://localhost:8080/api/v1
```

### Authentication

All protected endpoints require a Bearer token:

```
Authorization: Bearer <access_token>
```

Obtain tokens via `/auth/register` or `/auth/login`. Refresh tokens are delivered as httpOnly cookies.

### Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/auth/register` | Create a new account | No |
| `POST` | `/auth/login` | Sign in | No |
| `POST` | `/auth/refresh` | Refresh access token | Cookie |
| `GET` | `/users/me` | Get current user profile | Yes |
| `POST` | `/organizations` | Create an organization | Yes |
| `POST` | `/organizations/{id}/join` | Join an organization | Yes |
| `GET` | `/organizations` | List my organizations | Yes |
| `GET` | `/organizations/{id}` | Get organization details | Yes |
| `GET` | `/organizations/{id}/members` | List members (Admin+) | Yes |
| `PUT` | `/organizations/{id}/members/{userId}/role` | Update member role | Yes |
| `GET` | `/market/assets` | List tradable assets | No |
| `GET` | `/market/prices?symbols=BTC,ETH` | Get live prices | No |
| `GET` | `/market/overview` | Market overview (gainers/losers) | No |
| `GET` | `/market/{symbol}/history?days=30` | Historical price data | No |
| `POST` | `/trading/execute` | Execute a trade | Yes |
| `GET` | `/trading/portfolio` | Get portfolio with holdings | Yes |
| `GET` | `/trading/trades` | Get trade history (paginated) | Yes |
| `GET` | `/analytics/summary` | Performance summary | Yes |
| `GET` | `/analytics/history` | Historical analytics | Yes |
| `POST` | `/journal/generate` | Generate AI journal entry | Yes |
| `GET` | `/journal` | Get journal entries (paginated) | Yes |
| `GET` | `/notifications` | Get notifications (paginated) | Yes |
| `GET` | `/notifications/unread-count` | Unread notification count | Yes |
| `PATCH` | `/notifications/{id}/read` | Mark notification as read | Yes |
| `GET` | `/organizations/{id}/leaderboard` | Org leaderboard | Yes |

### Error Responses

All errors follow [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) (`application/problem+json`):

```json
{
  "type": "https://api.apex.com/errors/not-found",
  "title": "not-found",
  "status": 404,
  "detail": "Asset not found: BTC",
  "instance": "/error",
  "timestamp": "2026-01-15T10:30:00Z"
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Frontend (React)                       │
│              TanStack Query · Zustand · Tailwind             │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST + WebSocket
┌──────────────────────────▼──────────────────────────────────┐
│                     Backend (Spring Boot)                    │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌──────────────┐ │
│  │  Auth    │  │ Trading │  │ Analytics │  │   Journal    │ │
│  │  RBAC    │  │ Engine  │  │  Engine   │  │   (Claude)   │ │
│  └────┬────┘  └────┬────┘  └─────┬────┘  └──────┬───────┘ │
│       │            │             │               │          │
│  ┌────▼────────────▼─────────────▼───────────────▼──────┐  │
│  │              Service Layer (Business Logic)           │  │
│  └────┬────────────┬─────────────┬───────────────┬──────┘  │
│       │            │             │               │          │
│  ┌────▼────┐  ┌────▼────┐  ┌────▼─────┐  ┌─────▼──────┐  │
│  │ Postgres│  │  Redis  │  │ RabbitMQ │  │ CoinGecko  │  │
│  │   (JPA) │  │ (Cache) │  │ (Events) │  │   (API)    │  │
│  └─────────┘  └─────────┘  └──────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Design Principles

- **Layered architecture** — Controller → Service → Repository. No business logic in controllers.
- **Event-driven side effects** — Analytics recompute, notifications, and leaderboard updates happen asynchronously via RabbitMQ.
- **Multi-tenant isolation** — Every query is scoped server-side from the authenticated principal. Never trust client-supplied IDs.
- **Idempotent operations** — Trade execution requires an `Idempotency-Key` header. The ledger is append-only.
- **Pluggable providers** — Market data and AI journal generation are behind interfaces. Swap CoinGecko for any provider without touching core logic.

## Project Structure

```
Apex/
├── Backend/                    Spring Boot application
│   └── src/main/java/com/abdulrafy/backend/
│       ├── auth/               Authentication, JWT, user management
│       ├── organization/       Multi-tenant orgs, memberships, RBAC
│       ├── market/             Asset catalog, price fetching, caching
│       ├── trading/            Trade execution, portfolio, holdings
│       ├── analytics/          Performance calculations, snapshots
│       ├── journal/            AI-powered trade journal
│       ├── notification/       Alerts, events, read tracking
│       └── common/             Security config, filters, error handling
├── frontend/                   React application
│   └── src/
│       ├── app/                Routing, layout shells
│       ├── features/           Feature modules (market, trading, etc.)
│       ├── components/ui/      Design system primitives
│       ├── design-system/      Tokens (colors, spacing, typography)
│       ├── lib/                API client, utilities
│       └── store/              Zustand stores (UI state only)
├── Docs/
│   ├── PRD.md                  Product requirements
│   ├── AGENTS.md               Build constraints & conventions
│   └── IMPLEMENTATION_PLAN.md  Development phases
├── docker-compose.yml          Local infrastructure
└── .env                        Environment variables (gitignored)
```

## Testing

### Backend (131 tests)

```bash
cd Backend
./mvnw test                    # Run all tests
./mvnw verify                  # Unit + integration tests
```

**Test coverage includes:**
- Unit tests for all service methods (Mockito)
- Integration tests with real PostgreSQL, Redis, RabbitMQ (Testcontainers)
- Cross-tenant isolation tests (User A cannot access User B's data)
- Idempotency tests (duplicate trade keys produce single trade)
- Concurrency tests (optimistic lock conflict handling)
- Analytics formula verification (Sharpe ratio, drawdown, win rate)

### Frontend (100 tests)

```bash
cd frontend
npm test                       # Run once
npm run test:watch             # Watch mode
npm run lint                   # Lint
npx tsc --noEmit               # Type-check
```

### E2E API Tests

```bash
# Full endpoint coverage (requires running backend)
BASE_URL=http://localhost:8080 ./test_all_endpoints.sh
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/amazing-feature`)
3. Commit with conventional format (`feat(trading): add limit orders`)
4. Push to the branch (`git push origin feat/amazing-feature`)
5. Open a Pull Request

### Commit Convention

```
feat(trading): add market buy endpoint
fix(analytics): correct sharpe ratio denominator
test(auth): add cross-tenant isolation test
docs(api): update trading endpoint examples
```

## License

MIT License. See [LICENSE](LICENSE) for details.

---

<div align="center">

**Built with precision. Designed for learning.**

</div>
