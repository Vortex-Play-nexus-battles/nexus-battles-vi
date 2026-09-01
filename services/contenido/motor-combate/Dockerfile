# Dockerfile de services/contenido/motor-combate
#
# motor-combate es logica de dominio pura (no usa Spring Boot, sin
# servidor/puerto propio) — evita el plugin nexus.spring-conventions
# a proposito, ver comentario en build.gradle. Este Dockerfile solo
# compila y empaqueta el jar, no define CMD/ENTRYPOINT de ejecucion,
# ya que el modulo no arranca como aplicacion independiente.
#
# Cumple la regla de plataforma #9 (CLAUDE.md, doce reglas): "Definicion
# de contenedor versionada junto al servicio, sin excepciones".
# Pendiente confirmar con Chifu si el modulo necesita CMD/ENTRYPOINT
# una vez se defina como se consume (via jar como dependencia de otro
# servicio, o de alguna otra forma).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY services services
RUN sh gradlew --no-daemon :services:contenido:motor-combate:jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/services/contenido/motor-combate/build/libs/*.jar app.jar
