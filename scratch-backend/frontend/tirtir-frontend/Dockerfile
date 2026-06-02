FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ─── Production Stage: Nginx ──────────────────────────────
FROM nginx:alpine

# Copy Angular build output to Nginx
COPY --from=build /app/dist/tirtir-frontend/browser /usr/share/nginx/html

# Custom Nginx config for Angular SPA routing
RUN echo 'server { \
    listen 80; \
    root /usr/share/nginx/html; \
    index index.html; \
    location / { \
    try_files $uri $uri/ /index.html; \
    } \
    location /api/ { \
    proxy_pass http://backend:5001; \
    } \
    }' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
