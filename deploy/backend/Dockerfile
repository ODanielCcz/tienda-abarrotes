FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S tienda && adduser -S tienda -G tienda

COPY apps/backend/build/libs/*.jar /app/tienda-abarrotes-backend.jar

USER tienda

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/tienda-abarrotes-backend.jar"]
