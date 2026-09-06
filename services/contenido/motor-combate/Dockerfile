# Dockerfile de services/contenido/motor-combate
#
# motor-combate es un microservicio desplegable: lo confirmo @SimonP0607 el
# 2026-09-01, porque 'shared/libs/' es la unica excepcion al modelo de un
# despliegue por modulo y prohibe logica de negocio. Cumple la regla 9 de
# plataforma con un contenedor que arranca de verdad, no solo un jar empacado.
#
# El servicio expone hoy salud y metricas de Actuator. Las operaciones de
# negocio llegan cuando se acuerde el contrato del motor.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY buildSrc buildSrc
COPY services services
RUN sh gradlew --no-daemon :services:contenido:motor-combate:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion \
     /app/services/contenido/motor-combate/build/libs/*.jar servicio-motor-combate.jar

# Regla 10: la configuracion entra por variable de entorno, nunca versionada.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# Sonda de contenedor contra el Actuator que exige la regla 3.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "servicio-motor-combate.jar"]
