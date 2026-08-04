FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build
# Pre-compress so nginx can serve with a fixed Content-Length (gzip_static) instead
# of on-the-fly gzip, which requires Transfer-Encoding: chunked - some networks
# mangle chunked responses in a way that leaves the browser waiting forever.
RUN find dist -type f \( -name '*.js' -o -name '*.css' -o -name '*.svg' -o -name '*.json' -o -name '*.html' \) \
    -exec sh -c 'gzip -9 -c "$1" > "$1.gz"' _ {} \;

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
