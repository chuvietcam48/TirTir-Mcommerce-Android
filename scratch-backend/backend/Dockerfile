FROM node:20-alpine AS deps

WORKDIR /app

# Install dumb-init for proper signal handling (PID 1 problem)
RUN apk add --no-cache dumb-init

# Copy package files first for layer caching
COPY package*.json ./

# Install production dependencies only
RUN npm ci --omit=dev

# ─── Production Stage ─────────────────────────────────────────────────────────
FROM node:20-alpine AS production

WORKDIR /app

# Copy dumb-init from deps stage
COPY --from=deps /usr/bin/dumb-init /usr/bin/dumb-init

# Security: Run as non-root user
# Node Alpine already has a 'node' user (uid 1000)
RUN mkdir -p /app/logs /app/uploads && chown -R node:node /app

# Copy node_modules from deps stage
COPY --from=deps /app/node_modules ./node_modules

# Copy application source
COPY --chown=node:node . .

# Switch to non-root user
USER node

EXPOSE 5001

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:5001/api/v1/health || exit 1

ENV NODE_ENV=production

# Use dumb-init to handle PID 1 and forward signals properly
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "server.js"]
