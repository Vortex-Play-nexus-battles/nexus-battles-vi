# La imagen se construye desde la raiz del monorepo:
# docker build -f services/contenido/inventario/Dockerfile .
FROM eclipse-temurin:21-jdk AS construccion

WORKDIR /repo

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
# settings.gradle incluye las librerias de shared/ desde el 13-sep (#321): Gradle
# exige que exista la carpeta de cada proyecto incluido aunque este servicio no
# las use, si no falla al configurar (corrida de CD 35552724237, 21-sep).
COPY shared shared
COPY services services

RUN sh gradlew --no-daemon :services:contenido:inventario:bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=construccion /repo/services/contenido/inventario/build/libs/*.jar servicio-inventario.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "servicio-inventario.jar"]
