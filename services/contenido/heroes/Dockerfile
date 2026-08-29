# Multietapa, conforme a la regla de plataforma: definicion de contenedor
# versionada junto al servicio. Desde la unificacion del monorepo en Gradle
# el wrapper vive en la raiz, asi que la imagen se construye desde la RAIZ:
#   docker build -f services/contenido/heroes/Dockerfile .
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /repo
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY services services
RUN sh gradlew --no-daemon :services:contenido:heroes:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /repo/services/contenido/heroes/build/libs/*.jar servicio-heroes.jar
# Limite de memoria conforme al dimensionamiento de la pila (seccion 9.4).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "servicio-heroes.jar"]
