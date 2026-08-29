# Multietapa, conforme a la regla de plataforma: definicion de contenedor
# versionada junto al servicio. Imagen final recortada con jre de Temurin.
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /app/build/libs/*.jar servicio-heroes.jar
# Limite de memoria conforme al dimensionamiento de la pila (seccion 9.4).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "servicio-heroes.jar"]
