# =========================================================================
# Stage 1: Build the React / Vite application
# =========================================================================
FROM node:22-alpine AS builder

WORKDIR /app

# Install dependencies (cached unless package files change)
COPY package.json package-lock.json ./
RUN npm ci

# Copy configuration and source files
COPY tsconfig*.json vite.config.ts index.html ./
COPY src ./src

# Build static bundle for production
RUN npm run build

# =========================================================================
# Stage 2: Serve with lightweight Nginx web server
# =========================================================================
FROM nginx:alpine AS runner

# Remove default nginx welcome page
RUN rm -rf /usr/share/nginx/html/*

# Copy built assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration with SPA support and API reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
