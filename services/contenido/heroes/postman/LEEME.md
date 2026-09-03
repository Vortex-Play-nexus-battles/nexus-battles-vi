# Validacion con Postman del servicio de heroes

Coleccion con **aserciones** para las historias HU-HER-001 a HU-HER-010: cada
peticion verifica la regla del cliente que le corresponde (Tablas 5, 6, 7 y 20
del documento; RC-01 del acta del 2026-07-29; correcciones del acta del
2026-08-13). Sirve para validar el servicio antes de un push a `develop`, y
es la misma validacion que puede correr el CI.

## Archivos

| Archivo | Que es |
|---|---|
| `heroes.postman_collection.json` | La coleccion: 21 peticiones, 68 aserciones, agrupadas por historia |
| `local.postman_environment.json` | Entorno local: `baseUrl = http://localhost:8080` |

## Con la app de Postman

1. Levantar el servicio: `gradlew.bat bootRun` (queda en `http://localhost:8080`).
2. En Postman: **Import** y arrastrar los dos archivos.
3. Elegir el entorno `heroes - local` (arriba a la derecha).
4. Clic derecho sobre la coleccion → **Run collection** → **Run**. Todo debe
   quedar en verde; cada asercion dice que regla verifica.

## Desde consola (Newman), sin abrir Postman

Con el servicio levantado:

```bash
npx --yes newman run postman/heroes.postman_collection.json -e postman/local.postman_environment.json
```

Sale el mismo reporte que la app, con el resumen de peticiones y aserciones.
Contra otro ambiente, cambiar `baseUrl` en el entorno o pasar
`--env-var baseUrl=http://host:puerto`.

## Que cubre

| Grupo | Peticiones |
|---|---|
| 0. Plataforma | Salud y metricas de Actuator (regla 3) |
| 1. HU-HER-001 | Catalogo: los prototipos de la Tabla 5, sin fijar la cantidad; dos sanadores |
| 2. HU-HER-002 | Ficha del Tanque (ejemplo de clase), Chaman y Medico sin ataque, busqueda tolerante, error 404 legible sin codigos |
| 3. HU-HER-003/006/007/008/009/010 | Vista por nivel: escalado del ejemplo textual, desbloqueo 1/4/8, multiplicador, epica afin con recarga, tope del nivel 8, 400 fuera de rango |
| 4. HU-HER-003/004 | Tabla de niveles, progresion con sobrante y tope, experiencia por enemigo, errores 400 |

Cuando cambie el contrato (`contracts/openapi/heroes.yaml`), actualizar aqui
la peticion afectada en el mismo cambio: la coleccion es parte de la evidencia
de aceptacion del lote.
