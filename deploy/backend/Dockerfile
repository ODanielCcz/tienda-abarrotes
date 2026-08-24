# Verified locally on 2026-08-18. Keep the digest to prevent silent tag drift.
FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f

WORKDIR /app

RUN apk upgrade --no-cache \
    && addgroup -S tienda \
    && adduser -S tienda -G tienda

COPY apps/backend/build/libs/*.jar /app/tienda-abarrotes-backend.jar

USER tienda

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/tienda-abarrotes-backend.jar"]
