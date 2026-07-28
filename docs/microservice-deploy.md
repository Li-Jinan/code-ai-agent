# Microservice Deployment

This deployment runs the public demo as microservices:

- `frontend`: Nginx + Vue static files, public entry
- `user-service`: user APIs, port `8124`
- `app-service`: app, chat, generation and deploy APIs, port `8125`
- `screenshot-service`: internal screenshot Dubbo provider, port `8127`
- `mysql`, `redis`, `nacos`: infrastructure

## Server Steps

1. Copy the example env file and fill in real secrets.

```bash
cp deploy.microservice.env.example .env
vim .env
```

2. Build and start all services.

```bash
docker compose -f docker-compose.microservice.yml --env-file .env up -d --build
```

3. Check logs.

```bash
docker compose -f docker-compose.microservice.yml --env-file .env logs -f app-service
docker compose -f docker-compose.microservice.yml --env-file .env logs -f user-service
```

4. Open the public URL from `PUBLIC_BASE_URL`.

## Routing

Nginx exposes one public origin:

- `/` serves the Vue frontend
- `/api/user/**` proxies to `user-service`
- `/api/app/**`, `/api/chatHistory/**`, `/api/static/**` proxy to `app-service`
- `/{deployKey}/**` serves generated deployed apps from the shared deploy volume

## Notes

- `PUBLIC_BASE_URL` should be the final browser-visible URL, for example `https://agent.example.com`.
- If another Nginx or a cloud load balancer already owns ports `80` and `443`, set `HTTP_PUBLIC_PORT` to an internal port such as `18080` and proxy your domain to that port.
- Do not expose MySQL, Redis, Nacos, or service ports publicly in production security groups unless you need remote debugging.

