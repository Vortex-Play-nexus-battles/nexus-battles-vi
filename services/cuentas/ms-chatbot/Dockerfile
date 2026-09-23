FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY build.nosync/libs/*.jar app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "app.jar"]
