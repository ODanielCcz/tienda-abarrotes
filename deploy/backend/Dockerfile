# Verified locally on 2026-08-18. Keep the digest to prevent silent tag drift.
FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0

WORKDIR /app

RUN apk upgrade --no-cache \
    && addgroup -S tienda \
    && adduser -S tienda -G tienda

COPY apps/backend/build/libs/*.jar /app/tienda-abarrotes-backend.jar

USER tienda

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/tienda-abarrotes-backend.jar"]
