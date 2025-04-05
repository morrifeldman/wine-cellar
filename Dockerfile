# Stage 1: Build the ClojureScript frontend
FROM clojure:temurin-21-tools-deps-alpine AS frontend-builder

WORKDIR /app

# Copy dependency files
COPY deps.edn shadow-cljs.edn package.json package-lock.json ./

# Install npm dependencies and pre-download Clojure dependencies
RUN apk add --no-cache nodejs npm && npm install

# Copy source code
COPY src/ src/
COPY public/ public/
COPY resources/ resources/

# Build the frontend
RUN npx shadow-cljs release app

# Stage 2: Final runtime image with Clojure
FROM clojure:temurin-21-tools-deps-alpine

WORKDIR /app

# Copy dependency files
COPY deps.edn ./
COPY src/ src/
COPY resources/ resources/

# Pre-download dependencies
RUN clojure -P

# Copy the compiled frontend from the frontend builder
COPY --from=frontend-builder /app/public public/

# Expose the port the app runs on
EXPOSE 3000

# Set environment variables
ENV PORT=3000
ENV CLOJURE_ENV=production

# Run the application directly with Clojure
CMD ["clojure", "-M", "-m", "wine-cellar.server"]
