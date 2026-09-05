# La imagen se construye desde la raíz del monorepo:
# docker build -f services/contenido/productos/Dockerfile .
FROM eclipse-temurin:21-jdk AS construccion

WORKDIR /repo

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY services services
COPY frontend/app-web/src/comun frontend/app-web/src/comun
COPY frontend/app-web/src/contenido/productos frontend/app-web/src/contenido/productos

RUN sh gradlew --no-daemon :services:contenido:productos:bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=construccion /repo/services/contenido/productos/build/libs/*.jar servicio-productos.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "servicio-productos.jar"]
