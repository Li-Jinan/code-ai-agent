FROM node:22-alpine AS build
WORKDIR /workspace

ARG VITE_API_BASE_URL=/api
ARG VITE_DEPLOY_DOMAIN=http://localhost
ARG VITE_DEMO_ACCOUNT_ENABLED=true
ARG VITE_DEMO_ACCOUNT_USERNAME=demo
ARG VITE_DEMO_ACCOUNT_PASSWORD=demo123456
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DEPLOY_DOMAIN=${VITE_DEPLOY_DOMAIN}
ENV VITE_DEMO_ACCOUNT_ENABLED=${VITE_DEMO_ACCOUNT_ENABLED}
ENV VITE_DEMO_ACCOUNT_USERNAME=${VITE_DEMO_ACCOUNT_USERNAME}
ENV VITE_DEMO_ACCOUNT_PASSWORD=${VITE_DEMO_ACCOUNT_PASSWORD}

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build-only

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN mkdir -p /usr/share/nginx/deploy
EXPOSE 80 8080
