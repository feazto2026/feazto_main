# Admin Web image (React + Vite control plane).
# Build context is the REPO ROOT (see docker-compose.yml).
# Canonical frontend source: apps/admin-web (package.json + vite.config.*).
#
# Local dev note: most admin dev runs `npm run dev` inside apps/admin-web and
# points VITE_API_BASE_URL at the locally running API. This image produces the
# static production build served by nginx for `docker compose up --build` parity.

# ---------- build stage ----------
FROM node:20-alpine AS build
WORKDIR /workspace/apps/admin-web

ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Install deps first for layer caching.
COPY apps/admin-web/package.json ./apps/admin-web/package-lock.json* ./apps/admin-web/pnpm-lock.yaml* ./apps/admin-web/yarn.lock* ./
RUN if [ -f package-lock.json ]; then npm ci || npm install; \
    elif [ -f pnpm-lock.yaml ]; then corepack enable && pnpm install --frozen-lockfile || pnpm install; \
    elif [ -f yarn.lock ]; then yarn install --frozen-lockfile || yarn install; \
    else npm install; fi

# Copy the rest of the admin-web source and build.
COPY apps/admin-web ./
RUN npm run build || yarn build || pnpm build

# ---------- runtime stage ----------
FROM nginx:1.27-alpine AS runtime

# SPA fallback: all non-file routes serve index.html (client-side routing).
COPY infra/docker/admin.nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /workspace/apps/admin-web/dist /usr/share/nginx/html

EXPOSE 80

# wget exists in nginx:alpine and is used by the compose healthcheck.
HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=20s \
  CMD wget -qO- http://localhost:80/ >/dev/null 2>&1 || exit 1
