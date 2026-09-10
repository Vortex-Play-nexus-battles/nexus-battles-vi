# Imagen del servicio de heroes, con el patron del flujo de despliegue de la
# empresa (cd.yml, job "construir-y-publicar"): el jar ejecutable lo compila
# el flujo desde la raiz (./gradlew :services:contenido:heroes:bootJar) y
# esta imagen SOLO lo copia. Como todos los servicios de contenido, se
# construye desde la RAIZ del monorepo, con -f:
#   ./gradlew :services:contenido:heroes:bootJar
#   docker build -f services/contenido/heroes/Dockerfile -t servicio-heroes .
# Regla 9 de plataforma: definicion de contenedor versionada junto al servicio.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/contenido/heroes/build/libs/*.jar app.jar
# Limite de memoria conforme al dimensionamiento de la pila (seccion 9.4).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# El servicio escucha en 8080 dentro del contenedor (lo que esperan sus
# consumidores, p. ej. el motor de combate). En despliegue se publica en el
# 8101 del host: ver puerto_de() en cd.yml y docker-compose.contenido.yml.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
