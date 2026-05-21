# Deployment Guide & CI/CD

The BidMart Auction Service is automatically deployed using GitHub Actions. We package the application into a Docker Image and save it in the **GitHub Container Registry (GHCR)**.

## 1. CI/CD Pipeline (`.github/workflows`)
Our pipeline has two main parts:

### A. Continuous Integration (`ci.yml`)
This runs automatically every time someone pushes code or opens a Pull Request. It runs `gradle test`, checks the Code Coverage (must be at least 80%), and runs a code quality check using SonarCloud to make sure no bugs or messy code get into the main project.

### B. Continuous Deployment (`cd.yml`)
This runs automatically, but only when code is pushed or merged into the `main` branch. It builds the application into a `.jar` file, puts it inside a Docker Container, and uploads (pushes) that container to GHCR (`ghcr.io`).

## 2. Docker Compose (Local & Production)
If you want to run the service along with its monitoring tools, use this command:

```bash
docker-compose up -d
```
The services that will start are:
1. `prometheus` (Port 9090) - Pulls metrics and data from the application.
2. `grafana` (Port 3000) - Displays beautiful graphs showing CPU, RAM, and Bidding activity.

## 3. Environment Variables
For production (like when running on an AWS EC2 server), make sure these variables are set up in your `.env` file:

| Variable | Description |
|---|---|
| `JDBC_DATABASE_URL` | The PostgreSQL connection link (for example: Neon Database) |
| `JDBC_DATABASE_USERNAME` | Database username |
| `JDBC_DATABASE_PASSWORD` | Database password |
| `REDIS_URL` | Upstash Redis connection (must start with `rediss://` for security) |
| `RABBITMQ_HOST` | CloudAMQP Host link |
| `RABBITMQ_USERNAME` | RabbitMQ Username / Vhost |
| `RABBITMQ_PASSWORD` | RabbitMQ Password |
| `WALLET_SERVICE_URL` | The internal link to the Wallet Service |
