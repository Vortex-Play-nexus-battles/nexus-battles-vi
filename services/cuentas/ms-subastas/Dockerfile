FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# build.gradle fija la carpeta de build en build.nosync; con build/libs este
# COPY fallaba con "no such file" y la imagen nunca se pudo construir.
COPY build.nosync/libs/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
