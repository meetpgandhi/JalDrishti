# ─────────────────────────────────────────────────────────────
# Stage 1: Build Vue 3 TypeScript Console
# ─────────────────────────────────────────────────────────────
FROM node:20-alpine AS frontend-builder
WORKDIR /app/console

COPY console/package*.json ./
RUN npm ci

COPY console/ ./
# Build frontend directly to local dist directory
RUN npx vite build --outDir dist

# ─────────────────────────────────────────────────────────────
# Stage 2: Build Maven Multi-Module Java Application (Temurin 21)
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app

# Copy POM definitions for dependency caching
COPY pom.xml ./
COPY jd-domain/pom.xml ./jd-domain/
COPY jd-application/pom.xml ./jd-application/
COPY jd-adapter-pubsub/pom.xml ./jd-adapter-pubsub/
COPY jd-adapter-rest/pom.xml ./jd-adapter-rest/
COPY jd-adapter-persistence/pom.xml ./jd-adapter-persistence/
COPY jd-adapter-gemini/pom.xml ./jd-adapter-gemini/
COPY jd-adapter-channels/pom.xml ./jd-adapter-channels/
COPY jd-adapter-cap/pom.xml ./jd-adapter-cap/
COPY jd-sim/pom.xml ./jd-sim/
COPY jd-boot/pom.xml ./jd-boot/

# Copy source code across all modules
COPY jd-domain/src ./jd-domain/src
COPY jd-application/src ./jd-application/src
COPY jd-adapter-pubsub/src ./jd-adapter-pubsub/src
COPY jd-adapter-rest/src ./jd-adapter-rest/src
COPY jd-adapter-persistence/src ./jd-adapter-persistence/src
COPY jd-adapter-gemini/src ./jd-adapter-gemini/src
COPY jd-adapter-channels/src ./jd-adapter-channels/src
COPY jd-adapter-cap/src ./jd-adapter-cap/src
COPY jd-sim/src ./jd-sim/src
COPY jd-boot/src ./jd-boot/src

# Inject built static frontend into Spring Boot resources
COPY --from=frontend-builder /app/console/dist ./jd-boot/src/main/resources/static

# Build production executable uber-jar without running or compiling tests during image packaging
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# ─────────────────────────────────────────────────────────────
# Stage 3: Minimal Production Runtime
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged system user for cloud security compliance
RUN addgroup -S jaldrishti && adduser -S jaldrishti -G jaldrishti

# Copy explicit boot jar (avoiding wildcard issues)
COPY --from=backend-builder --chown=jaldrishti:jaldrishti /app/jd-boot/target/jd-boot-0.1.0-SNAPSHOT.jar /app/app.jar

USER jaldrishti

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=sim
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
