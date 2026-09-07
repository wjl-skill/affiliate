FROM eclipse-temurin:21-jre
WORKDIR /app
COPY platform-api/target/platform-api-0.2.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
