# The web app, served by nginx, which also passes /api to the API. Build: docker build -t motor-movers-web frontend
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
# Where the API is reachable from this container.
ENV API_UPSTREAM=http://api:8080
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://localhost/healthz || exit 1
