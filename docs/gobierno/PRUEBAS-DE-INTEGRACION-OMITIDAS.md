# Observación: pruebas de integración que se omiten en silencio

**Para:** Néstor Pinto Roa (HU-CICD-001, HU-CICD-002) y los dueños de `heroes` e `inventario`
**De:** Grupo 6 · salas-partidas
**Fecha:** 30 de agosto de 2026
**Estado:** observación, sin cambios aplicados. Requiere coordinación antes de tocar nada.

## Qué pasó

Al escribir las pruebas de integración de `salas-partidas` contra PostgreSQL copiamos el
patrón que ya usaban `heroes` e `inventario`:

```java
@Testcontainers(disabledWithoutDocker = true)
```

Ese parámetro hace que, **si Docker no está disponible, las pruebas no fallen: se omitan**.
El resultado es un `BUILD SUCCESSFUL` que no ejecutó ni una sola prueba de integración.

Nos pasó a nosotros y no lo vimos hasta mirar los informes XML uno por uno. La salida de
Gradle decía:

```
BUILD SUCCESSFUL
```

Y el informe real decía:

```
RepositorioSalasJpaIT   tests="5" skipped="5"
```

## Por qué importa

- La cobertura declarada incluye esas pruebas, pero no se ejecutaron.
- Un fallo de mapeo entre la migración de Flyway y la entidad JPA no se detecta.
- La compuerta de calidad de la **regla 11** da por buena una rama que nadie verificó.
- En un portátil sin Docker, o en un runner de CI sin el servicio levantado, el efecto es
  silencioso: nadie recibe una señal.

## Qué hicimos en `salas-partidas`

Dos cambios, **solo en nuestro servicio**:

1. Quitamos `disabledWithoutDocker`. Sin Docker, la prueba falla en vez de saltarse.
2. Añadimos una guarda en `build.gradle` que tumba el build si la tarea `test` omite
   alguna prueba, con un mensaje que explica el motivo.

## Qué proponemos discutir

No tocamos `heroes` ni `inventario`: son de otro equipo y el cambio los pondría en rojo
hasta que su entorno tenga Docker. Es una decisión suya.

Lo que sí conviene decidir entre los tres equipos:

- **¿Se separan las pruebas de integración de las unitarias?** Hoy los `*IT` corren dentro
  de la tarea `test` normal. Separarlas permitiría que `test` siga siendo rápido y sin
  Docker, y que `integrationTest` sea obligatorio en CI. Eso toca
  `nexus.spring-conventions`, que aplican los 11 módulos declarados.
- **¿El runner de GitHub Actions tiene Docker?** Si lo tiene, quitar `disabledWithoutDocker`
  en todos los servicios es seguro y recomendable.
- **¿La compuerta de calidad debería rechazar builds con pruebas omitidas?** Es la forma
  más barata de que esto no vuelva a pasar.

## Cómo comprobarlo en cualquier servicio

```bash
grep -o 'tests="[0-9]*" skipped="[0-9]*"' \
  services/<grupo>/<servicio>/build/test-results/test/*.xml
```

Si `skipped` no es cero, hay pruebas que nadie ejecutó.
