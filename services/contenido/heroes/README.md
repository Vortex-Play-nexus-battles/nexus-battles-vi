# Servicio de héroes

Catálogo de prototipos de héroe y reglas de progresión de THE NEXUS BATTLES VI. Java 21 + Spring Boot 4.1, dominio puro separado de la capa web.

## Alcance implementado

| Historia / regla | Qué cubre |
|---|---|
| HU-HER-001 | Catálogo de prototipos (sin cantidad fija: los ocho de la Tabla 5 son datos, no un límite) y ficha con nombre, tipo, descripción y acciones. `GET /api/v1/heroes` y `GET /api/v1/heroes/{nombre}` |
| HU-HER-002 | Estadísticas base de nivel 1 exactas de la Tabla 6; sanadores sin capacidad ofensiva |
| HU-HER-003 | Subida de nivel: `Experiencia = 100 × 1,2^(N−1)`, sobrante conservado, tope en nivel 8 |
| HU-HER-005 | Poder: +2 por turno, recuperación instantánea al concluir, y sin poder suficiente el ataque cae al valor base |
| HU-HER-006 | Las 24 acciones de la Tabla 7 con costo y efecto exactos; tres acciones por prototipo como invariante |
| RC-01 (RG-021) | Desbloqueo de acciones en niveles 1, 4 y 8 — regla dictada por el cliente en la sesión del 2026-07-29, ausente del PDF |

Cada prueba cita en comentario la regla del cliente que verifica (Tablas 5/6/7 del documento, actas de clase).

## Cómo correr

```
./gradlew test        # 83 pruebas; genera cobertura JaCoCo en build/reports/jacoco
./gradlew bootRun     # servicio en http://localhost:8080/api/v1/heroes
```

Requiere JDK 21. El contrato REST está en `contracts/openapi/heroes.yaml` (pendiente de moverse al `contracts/` raíz del monorepo cuando se acuerde con plataforma). Errores en formato de detalles de problema (RFC 9457) con mensaje apto para el usuario final.

## Pendiente

- Persistencia en MongoDB cuando plataforma aprovisione los motores (el bean `Catalogo` en memoria se sustituye por el repositorio documental sin tocar dominio ni controlador).
- Adaptarse a los complementos de convención de Gradle cuando `shared/config/` los publique.
- El catálogo es de lectura: la administración de héroes como productos pertenece al servicio de productos (el héroe es un tipo de producto, sección 7.2.1).
