FROM node:22-alpine AS build
WORKDIR /workspace

ARG VITE_API_BASE_URL=/api
ARG VITE_DEPLOY_DOMAIN=http://localhost
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DEPLOY_DOMAIN=${VITE_DEPLOY_DOMAIN}

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build-only

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
RUN mkdir -p /usr/share/nginx/deploy
EXPOSE 80

