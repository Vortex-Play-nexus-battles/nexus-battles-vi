# Imagen del servicio de misiones. Se construye desde la RAIZ del monorepo,
# como el resto de contenido:
#   docker build -f services/contenido/misiones/Dockerfile -t servicio-misiones .
# Regla 9 de plataforma: definicion de contenedor versionada junto al servicio.
# Compila dentro de la imagen (mismo patron que inventario y motor-combate):
# asi la construyen igual el flujo de despliegue y el banco E2E, sin depender de
# un jar compilado antes fuera.
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /repo

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
# settings.gradle incluye las librerias de shared/: Gradle exige que exista la
# carpeta de cada proyecto incluido aunque este servicio no las use.
COPY shared shared
COPY services services

RUN sh gradlew --no-daemon :services:contenido:misiones:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=construccion /repo/services/contenido/misiones/build/libs/*.jar servicio-misiones.jar

# La red propia de IA de combate (HU-SIM-008): el modelo entrenado con partidas
# reales (modelo.onnx y su modelo.json) viaja en la imagen, en /app/ia/, que es
# la ruta por omision de misiones.ia.modelo.ruta. Al publicar una version nueva
# se cambia la carpeta de origen (ia/modelos/<version>/); ver ia/README.md.
COPY --from=construccion /repo/services/contenido/misiones/ia/modelos/1.0.0/ /app/ia/

# Regla 10: la configuracion entra por variable de entorno, nunca versionada
# (plantilla en .env.example). Limite de memoria conforme a la seccion 9.4.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Escucha en 8080 dentro del contenedor, como todo contenido; en el host de
# contenido se publica en el 8105 (docker-compose.contenido.yml).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "servicio-misiones.jar"]
