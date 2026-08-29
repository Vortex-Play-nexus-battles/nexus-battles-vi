FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /app/build/libs/*.jar servicio-inventario.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "servicio-inventario.jar"]
