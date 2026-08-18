# Verified locally on 2026-08-18. Keep the digest to prevent silent tag drift.
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

WORKDIR /app

RUN apk upgrade --no-cache \
    && addgroup -S tienda \
    && adduser -S tienda -G tienda

COPY apps/backend/build/libs/*.jar /app/tienda-abarrotes-backend.jar

USER tienda

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/tienda-abarrotes-backend.jar"]
