# Deployment Guide & CI/CD

The BidMart Auction Service is automatically deployed using GitHub Actions. We package the application into a Docker Image and save it in the **GitHub Container Registry (GHCR)**.

## 1. CI/CD Pipeline (`.github/workflows`)
Our pipeline has two main parts:

### A. Continuous Integration (`ci.yml`)
This runs automatically every time someone pushes code or opens a Pull Request. It runs `gradle test`, checks the Code Coverage (must be at least 80%), and runs a code quality check using SonarCloud to make sure no bugs or messy code get into the main project.

### B. Continuous Deployment (`deploy-blue-green.yml`)
This runs automatically on push to `main`. It implements a **Blue-Green Deployment** architecture:
1. **Smart Deploy**: Automatically reads the `ACTIVE_ENV` variable to determine the active server. It builds the application into a Docker Container, pushes to GHCR (`ghcr.io`), and deploys it to the **idle/inactive server** (Green or Blue).
2. **Manual Testing Phase**: The deployment halts after pushing to the idle server. This allows the QA team to manually test the new release on the non-production IP without affecting live users.

### C. Traffic Switching (`switch-traffic-gateway.yml`)
This is a **manually triggered** workflow. Once the idle server passes testing, a developer manually runs this workflow. It sends a `repository_dispatch` signal to the API Gateway to instantly route 100% of production traffic to the new server (**Zero-Downtime**) and automatically updates the `ACTIVE_ENV` variable for the next release.

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
