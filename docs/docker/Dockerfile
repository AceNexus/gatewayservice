FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY gatewayservice.jar /app/gatewayservice.jar

RUN chown -R app:app /app

USER app

CMD ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/gatewayservice.jar"]
