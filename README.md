<div align="center">

  <img src="apex_logo.png" alt="Apex Logo" width="500" height="500" />
  
  <h1>Apex Trading Intelligence</h1>
  
  <p><b>An Enterprise-Grade, Real-Time Market Simulation & Portfolio Intelligence Platform</b></p>

  <p>
    <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" /></a>
    <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" /></a>
    <a href="https://react.dev/"><img src="https://img.shields.io/badge/React_19-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="React 19" /></a>
    <a href="https://www.typescriptlang.org/"><img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript" /></a>
    <a href="https://www.postgresql.org/"><img src="https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" /></a>
    <a href="https://redis.io/"><img src="https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" /></a>
    <a href="https://www.rabbitmq.com/"><img src="https://img.shields.io/badge/RabbitMQ-3.13-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white" alt="RabbitMQ" /></a>
    <a href="https://www.docker.com/"><img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" /></a>
  </p>

  <p>
    <em>Apex bridges the gap between novice trading applications and professional institutional platforms.<br/>It provides a highly realistic, zero-risk financial simulation environment packed with enterprise software patterns, AI-driven behavioral insights, and real-time data streaming.</em>
  </p>


  ![Apex architecture](architecture.png)
  
  <p>
    <a href="#-why-apex-the-recruiters-tldr"><strong>Value Proposition</strong></a> &middot; 
    <a href="#-technical-showcase"><strong>Technical Showcase</strong></a> &middot; 
    <a href="#-system-architecture"><strong>Architecture</strong></a> &middot; 
    <a href="#-getting-started"><strong>Quick Start</strong></a>
  </p>
</div>

---

## Why Apex? (The Recruiter's TL;DR)

Apex was engineered from the ground up to demonstrate proficiency in **modern full-stack enterprise development**, focusing on the rigorous demands of FinTech systems: **scalability, data integrity, low latency, and complex system integrations.**

While many portfolio projects are simple CRUD apps, **Apex tackles real-world distributed system challenges**:

- **Event-Driven Architecture:** Uses **RabbitMQ** to decouple heavy analytics processing and notifications from the main trading thread, ensuring instantaneous order execution.
- **Enterprise Data Integrity & ACID Compliance:** Enforces **idempotent** API designs (preventing duplicate trades on network retries), optimistic locking for concurrent portfolio updates, and strict multi-tenant isolation at the query level.
- **High-Performance Caching:** Leverages **Redis** to cache aggressive global market data polling (via CoinGecko), protecting external API rate limits and guaranteeing `<10ms` data retrieval.
- **Real-Time Data Streaming:** Utilizes **WebSockets (STOMP)** to push live price ticks, portfolio valuation updates, and executed trade notifications to connected React clients with zero polling overhead.
- **Generative AI Integration:** Uses the **Gemini 2.5 Flash Lite** API to analyze a trader's daily performance metrics and generate personalized, behavioral trading psychology feedback.
- **Production-Ready Testing:** Backed by **140 automated tests** (JUnit 5, Mockito, Testcontainers, React Testing Library) ensuring robust, refactor-safe code.

---

## Platform Interface

<p align="center">
  <img src="Apex.png" alt="Apex Dashboard" width="100%" />
</p>
<p align="center">
  <em>Dark-mode optimized, responsive dashboard powered by React 19, Tailwind CSS, and TradingView Lightweight Charts.</em>
</p>

<p align="center">
  <img src="analytic.png" alt="Analytics Page" width="100%" />
</p>
<p align="center">
  <em>Advanced portfolio analytics with Sharpe Ratio, Max Drawdown, Win Rate, and FIFO-matched P/L.</em>
</p>

<p align="center">
  <img src="Ai_general.png" alt="AI Trading Journal" width="100%" />
</p>
<p align="center">
  <em>AI-powered trading journal generating behavioral narratives via Gemini 2.5 Flash Lite.</em>
</p>

---

<h2 id="-technical-showcase">Technology Stack & Justification</h2>

Every technology in Apex was chosen to solve a specific engineering problem:

| Layer | Technologies | Engineering Justification |
|-------|--------------|---------------------------|
| **Backend Core** | Java 21, Spring Boot 4.x, Spring Security (JWT) | Robust, strictly typed foundation with built-in dependency injection and MVC architecture. |
| **Primary Database** | PostgreSQL 16, Spring Data JPA, Flyway | ACID compliance for financial transactions and strict relational data integrity. Flyway manages schema migrations. |
| **Caching Layer** | Redis 7 | Drastically reduces read latency for frequently accessed market data and user sessions. |
| **Message Broker** | RabbitMQ | Asynchronous, event-driven architecture, decoupling trade execution from heavy post-trade analytics. |
| **Real-Time Data** | WebSockets (STOMP/SockJS), CoinGecko REST | Pushes live market ticks to clients without the overhead of HTTP polling. |
| **AI / ML** | Gemini 2.5 Flash Lite | Ultra-fast Generative AI for behavioral trading analysis via Google's Gemini API. |
| **Frontend Core** | React 19, TypeScript, Vite, Tailwind CSS | Lightning-fast, type-safe SPA with a modern, responsive UI. |
| **State & API** | TanStack Query, Zustand | Manages complex server-state caching and global client-state seamlessly. |
| **DevOps** | Docker, Docker Compose, Nginx | Fully containerized environment ensures consistent deployments everywhere. |

---

## System Architecture

Apex utilizes a modular, event-driven monolith design that is structurally prepared for future microservice extraction.

```
React + Vite SPA
    |
    |--- REST (JSON) ---> Spring Boot Controller
    |--- WebSocket (STOMP) ---> WebSocket Handler
                                  |
                          JWT Security Filter
                                  |
                          Business Logic Layer
                            /     |       \
                  Cache Miss   Read/Write   Publish Event
                     |            |              |
               CoinGecko API  PostgreSQL    RabbitMQ
                     |            |              |
                  Redis      Write Metrics   Analytics Engine
                                    |              |
                              Write Metrics  <--  |
                                    |
                          Send Metrics
                              |
                     Gemini 2.5 Flash Lite
                              |
                       Return Narrative
```

### Core Design Principles

- **Layered Clean Architecture:** Strict separation of concerns (Controllers -> Services -> Repositories). No business logic leaks into the transport layer.
- **Idempotency:** Trade execution endpoints require an `Idempotency-Key` header. The ledger is append-only, ensuring financial data cannot be corrupted by network retries.
- **Multi-Tenancy:** First-class support for Organizations and Cohorts. Every database query is strictly scoped server-side using the authenticated principal's context.

---

## Standout Features

- **Live Global Market Search:** Search the entire CoinGecko database live and instantly add any global asset (e.g., Solana, NVIDIA) to the PostgreSQL database for real-time tracking.
- **Advanced Portfolio Analytics:** Real-time calculation of professional metrics including Sharpe Ratio, Maximum Drawdown, Win Rate, and FIFO-matched Profit/Loss.
- **AI Trading Journal:** Daily behavioral narratives generated by Gemini 2.5 Flash Lite, summarizing the trader's psychological performance based on their mathematical metrics.
- **Role-Based Access Control (RBAC):** Distinct permissions and granular access controls for Super Admins, Organization Admins, Instructors, and Traders.
- **Interactive Data Visualization:** Lightweight, high-performance financial charts (OHLCV) powered by TradingView's Lightweight Charts library.

---

## Getting Started

Apex is fully containerized. You can spin up the entire enterprise stack locally with a single command, without worrying about dependency conflicts.

### Prerequisites

- [Docker](https://www.docker.com/) and Docker Compose
- Node.js 20+ (optional, for local frontend development)
- Java 21 (optional, for local backend development)

### One-Click Deployment

1. **Clone the repository:**
   ```bash
   git clone https://github.com/abdul-rafy2005/Apex.git
   cd Apex
   ```

2. **Configure Environment Variables:**
   ```bash
   cp .env.example .env
    # Open .env and add your Gemini API Key (free at aistudio.google.com) and a secure JWT Secret
   ```

3. **Launch the Infrastructure:**
   ```bash
   docker compose up -d --build
   ```
   *This single command builds the Java backend, compiles the React frontend, and spins up PostgreSQL, Redis, RabbitMQ, and Nginx reverse proxy.*

4. **Access the Platform:**
   - **Frontend UI:** `http://localhost` (Docker) or `http://localhost:5173` (local dev)
   - **Backend API:** `http://localhost:8080/api/v1`
   - **Swagger/OpenAPI Documentation:** `http://localhost:8080/api/v1/swagger-ui.html`

---

## Testing Strategy

Apex treats testing as a first-class citizen, demonstrating production-ready engineering standards.

- **Backend (140 Tests):** Mockito unit tests for isolated business logic, Testcontainers for integration tests against real PostgreSQL, Redis, and RabbitMQ. Validates concurrency (optimistic locking), idempotency, and cross-tenant security.
  ```bash
  cd Backend && ./mvnw verify
  ```
- **Frontend (100+ Tests):** Vitest and React Testing Library ensure component behavior and hook logic remain stable.
  ```bash
  cd frontend && npm test
  ```

---

<div align="center">
  <b>Built with precision. Designed for scale.</b><br><br>
  <a href="https://github.com/abdul-rafy2005">GitHub Profile</a>
</div>
