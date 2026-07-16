# Apex

Real-time market simulation and portfolio intelligence platform. Trade with live market data and virtual money, then get AI-powered insights on your performance.

## Prerequisites

- Java 21 (JDK)
- Node.js 20+ and npm
- Docker and Docker Compose
- Maven (or use the included `mvnw` wrapper)

## Quick Start

### 1. Start infrastructure services

```bash
docker compose up -d postgres redis rabbitmq
```

This starts PostgreSQL 16, Redis 7, and RabbitMQ. Wait for health checks to pass (~10 seconds).

### 2. Run the backend

```bash
cd Backend
cp .env.example .env   # or set environment variables
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. Swagger UI at `http://localhost:8080/api/v1/swagger-ui.html`.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app is available at `http://localhost:5173`.

### 4. Full stack with Docker Compose

```bash
docker compose up --build
```

## Running Tests

### Backend

```bash
cd Backend
./mvnw test              # unit tests
./mvnw verify            # unit + integration tests (requires Docker for Testcontainers)
```


### Frontend

```bash
cd frontend
npm test                 # run once
npm run test:watch       # watch mode
npm run lint             # lint
npx tsc --noEmit         # type-check
```

## Project Structure

```
Apex/
├── Backend/              Spring Boot project (Java 21, Maven)
│   └── src/main/java/com/abdulrafy/backend/
│       ├── auth/
│       ├── organization/
│       ├── market/
│       ├── trading/
│       ├── analytics/
│       ├── journal/
│       ├── notification/
│       └── common/
├── frontend/             React project (TypeScript, Vite)
│   └── src/
│       ├── app/
│       ├── features/
│       ├── components/ui/
│       ├── design-system/
│       ├── lib/
│       └── store/
├── docker-compose.yml
├── Docs/
│   ├── PRD.md
│   ├── AGENTS.md
│   └── IMPLEMENTATION_PLAN.md
└── README.md
```

## Environment Variables

See `Backend/.env.example` for the full list. Key variables:

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

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, RabbitMQ
- **Frontend:** React 18, TypeScript, Tailwind CSS, TanStack Query, Zustand, Vite
- **Testing:** JUnit 5, Mockito, Testcontainers (backend) / Vitest, React Testing Library (frontend)
- **Infra:** Docker, Docker Compose, GitHub Actions CI
