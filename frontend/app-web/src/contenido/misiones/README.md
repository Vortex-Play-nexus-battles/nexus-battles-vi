# Misiones — interfaz (UXC-5, con servicio desde B9)

Vista del módulo de misiones (§7.8 del documento del curso, M11; RF-MIS-001 a
RF-MIS-017). Desde B9 tiene servicio (`services/contenido/misiones`) y contrato
(`contracts/openapi/misiones.yaml` 1.0.0), escrito para servir al puerto que
esta interfaz ya consumía.

## Qué hay

| Pieza                                                                                                                 | Archivo                      | Requisito              |
| --------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ---------------------- |
| Tablón (MissionBoard): pestañas Historia/Desafío/Exploración, filtros de dificultad, estado y duración, 16 por página | `tablon-misiones.js`         | RF-MIS-001, RF-MIS-002 |
| Tarjeta (MissionCard) con los seis estados de §7.8.7                                                                  | `tablon-misiones.js`         | RF-MIS-001, RF-MIS-009 |
| Banner rotativo (MissionBanner), también en Mi inventario                                                             | `banner-misiones.js` (+ kit) | RF-MIS-001, RF-INV-003 |
| Detalle (MissionDetail): narrativa, objetivos, enemigos, jefe, Máster y épica, recompensas                            | `detalle-mision.js`          | RF-MIS-003, RF-MIS-008 |
| Configurador de estrategia (RotationBuilder), con la estrategia guardada de cada héroe                                | `constructor-estrategia.js`  | RF-MIS-004, RF-MIS-005 |
| Misiones en curso (ActiveMissionPanel)                                                                                | `en-curso.js`                | RF-MIS-009, RF-MIS-012 |
| Reporte e historial (MissionReport, MissionHistory)                                                                   | `reporte-mision.js`          | RF-MIS-010, RF-MIS-011 |
| Adaptador HTTP del servicio de misiones (el puerto)                                                                   | `fuente-misiones.js`         | todo lo anterior       |

## De dónde salen los datos

- **Misiones:** `fuente-misiones.js` es un adaptador HTTP contra `misiones.yaml`
  (tablón, destacadas, detalle, matrícula con `Idempotency-Key`, en curso,
  cancelación, reporte, historial, favoritas y estrategias guardadas). Antes de
  pintar nada pregunta al servicio (la consulta de destacadas, que el banner
  reutiliza); si no contesta, devuelve `disponible: false` y la vista pinta el
  estado honesto sin hacer ni una petición más. En el inventario el banner se
  oculta, como pide la excepción de RF-INV-003.
- **Estrategia:** la valida el servicio de héroes (`POST /api/v1/estrategias/validacion`
  y `GET /api/v1/heroes/{nombre}/niveles/{nivel}`, `heroes.yaml`) y la guarda
  el de misiones (§7.8.12, `GET`/`PUT /api/v1/misiones/estrategias/{heroeId}`).
  Al elegir un héroe se carga la suya y el nivel de partida es el que trae el
  inventario (1.6.0). Solo se ofrece guardar la del nivel del héroe: el
  servidor la vuelve a validar con ese nivel. Matricular también la guarda.
- **Reporte:** además de lo de §7.8.8, dice el nivel alcanzado, lo que sigue
  entregándose en segundo plano y lo que el documento promete pero no existe
  en ningún catálogo (`sinEntregar`), sin inventarlo.

## Laboratorio

El laboratorio visual sirve `tests/visual/laboratorio/fuente-misiones.js` en
lugar de la fuente real (escenarios `misiones-*` de `escenarios-poblados.js`).
«El Templo Olvidado» es el ejemplo del documento (§7.8.14); el resto son datos
de laboratorio, y cada captura lo avisa. Nada de eso llega al producto.
