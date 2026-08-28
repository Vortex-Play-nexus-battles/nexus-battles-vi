# Construccion multietapa con Java 21 y Gradle Wrapper.
FROM eclipse-temurin:21-jdk AS construccion

WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle

# El repositorio conserva gradlew como 100644; el contenedor habilita su ejecucion.
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar

# Imagen final reducida: solo contiene el JRE y el servicio compilado.
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=construccion /app/build/libs/*.jar servicio-productos.jar

# Limite de memoria definido por la pila tecnologica.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "servicio-productos.jar"]