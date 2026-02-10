# PKX Docker Infrastructure

This directory contains Docker Compose configuration for running PKX backend infrastructure locally.

## Services

- **PostgreSQL** (port 5432): Main database
- **Redis** (port 6379): Cache and refresh token storage
- **MinIO** (ports 9000, 9001): S3-compatible object storage for video files

## Quick Start

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# Stop and remove volumes (⚠️ deletes all data)
docker-compose down -v

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f postgres
```

## Access URLs

- **MinIO Console**: http://localhost:9001
  - Username: `minioadmin`
  - Password: `minioadmin`

- **PostgreSQL**: `localhost:5432`
  - Database: `pkx_db`
  - User: `pkx_user`
  - Password: `pkx_pass`

- **Redis**: `localhost:6379`

## Bucket Configuration

The MinIO bucket `pkx-videos` is automatically created on startup and configured for public download access.

## Health Checks

All services have health checks configured. Check status:

```bash
docker-compose ps
```
