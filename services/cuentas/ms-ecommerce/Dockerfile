# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Imagen de ms-ecommerce: JRE recortado con jlink + capas de Spring Boot + usuario sin
# privilegios. El jar lo compila antes el flujo (Maven); aqui solo se empaqueta,
# asi la imagen no lleva ni el JDK, ni el codigo fuente, ni la cache de Maven.
# Contexto de construccion: esta carpeta (cd.yml: docker build services/cuentas/ms-ecommerce).
# ---------------------------------------------------------------------------

# --- Etapa 1: separar el jar en capas y fabricar un JRE a la medida --------
FROM eclipse-temurin:21-jdk-alpine AS construccion
WORKDIR /construccion
COPY target/*.jar app.jar

# Capas de Spring Boot: dependencias (lib/) y codigo propio (app.jar delgado)
# por separado, para que un cambio de codigo no invalide la capa de
# dependencias (~60 MB). Se conserva app.jar porque los compose de despliegue
# arrancan con `exec java -jar app.jar` (arranque escalonado, R16.5b).
RUN java -Djarmode=tools -jar app.jar extract --layers --destination capas

# Modulos del JDK que la aplicacion usa de verdad (jdeps), mas los que se
# cargan por reflexion o ServiceLoader y jdeps no ve: criptografia EC (TLS y
# JWT), configuracion regional es/en, JMX de Actuator, DNS y zipfs.
RUN mkdir /tmp/jar && cd /tmp/jar && jar xf /construccion/app.jar  && jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --recursive -q       --class-path 'BOOT-INF/lib/*' BOOT-INF/classes > /tmp/modulos  && jlink --add-modules "$(cat /tmp/modulos),jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.management,java.instrument,jdk.naming.dns,jdk.zipfs,java.security.jgss"       --include-locales=es,en --strip-debug --no-man-pages --no-header-files --compress=zip-6       --output /jre

# --- Etapa 2: imagen final minima -------------------------------------------
FROM alpine:3.20
ENV JAVA_HOME=/opt/jre     PATH="/opt/jre/bin:$PATH"     JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

# Usuario sin privilegios (UID fijo para que los volumenes tengan dueno estable).
RUN addgroup -S -g 10001 nexus && adduser -S -D -H -u 10001 -G nexus nexus
COPY --from=construccion /jre /opt/jre
WORKDIR /app
COPY --from=construccion --chown=nexus:nexus /construccion/capas/dependencies/ ./
COPY --from=construccion --chown=nexus:nexus /construccion/capas/spring-boot-loader/ ./
COPY --from=construccion --chown=nexus:nexus /construccion/capas/snapshot-dependencies/ ./
COPY --from=construccion --chown=nexus:nexus /construccion/capas/application/ ./
USER nexus

EXPOSE 8090
# Docker (y Compose con depends_on: service_healthy) sabe cuando el servicio
# esta listo de verdad; la ruta es la de Actuator con el context-path del servicio.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5   CMD wget -q -O /dev/null http://127.0.0.1:8090/ecommerce/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
