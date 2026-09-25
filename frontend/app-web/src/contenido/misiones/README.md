# Misiones — interfaz (UXC-5)

Vista del módulo de misiones (§7.8 del documento del curso, M11). El módulo es
del **Grupo de Thomas** (RF-MIS-001 a RF-MIS-017) y, a la fecha de este cambio,
**no tiene servicio desplegado ni contrato publicado**: `services/contenido/misiones/`
es un README vacío y `contracts/openapi/` no trae `misiones.yaml`.

## Qué hay

| Pieza | Archivo | Requisito |
|---|---|---|
| Tablón (MissionBoard): pestañas Historia/Desafío/Exploración, filtros de dificultad, estado y duración, 16 por página | `tablon-misiones.js` | RF-MIS-001, RF-MIS-002 |
| Tarjeta (MissionCard) con los seis estados de §7.8.7 | `tablon-misiones.js` | RF-MIS-001, RF-MIS-009 |
| Banner rotativo (MissionBanner), también en Mi inventario | `banner-misiones.js` (+ kit) | RF-MIS-001, RF-INV-003 |
| Detalle (MissionDetail): narrativa, objetivos, enemigos, jefe, Máster y épica, recompensas | `detalle-mision.js` | RF-MIS-003, RF-MIS-008 |
| Configurador de estrategia (RotationBuilder) | `constructor-estrategia.js` | RF-MIS-004, RF-MIS-005 |
| Misiones en curso (ActiveMissionPanel) | `en-curso.js` | RF-MIS-009, RF-MIS-012 |
| Reporte e historial (MissionReport, MissionHistory) | `reporte-mision.js` | RF-MIS-010, RF-MIS-011 |

## Qué funciona hoy y qué no

- **Funciona de verdad:** el configurador de estrategia. Lista los héroes del
  inventario del jugador, pide al servicio de héroes las habilidades que cada
  prototipo tiene en un nivel (`POST /api/v1/estrategias/validacion` sin
  rotaciones) y su vista previa (`GET /api/v1/heroes/{nombre}/niveles/{nivel}`),
  y valida las rotaciones contra la regla del servidor. Todo en `heroes.yaml`.
- **No se guarda:** la estrategia. El contrato de héroes dice que quien la
  guarda es el módulo de misiones; la pantalla lo dice.
- **No hay misiones:** la fuente de misiones (`fuente-misiones.js`) devuelve
  `disponible: false` sin tocar la red. La vista pinta el estado honesto —qué
  pasa, por qué y qué se puede hacer ya— y el inventario oculta su banner, como
  pide la excepción de RF-INV-003. No se inventa ninguna ruta.

## El día que exista el servicio

`fuente-misiones.js` es un **puerto**: sus tipos (JSDoc) describen lo que la
interfaz necesita para pintar §7.8.9. Se sustituye `fuenteDeMisiones()` por un
adaptador HTTP que cumpla esa forma contra el contrato que publique el dueño
del módulo; el resto de la vista no cambia. Esos tipos son un insumo para ese
contrato, no una propuesta de rutas.

## Laboratorio

El laboratorio visual sirve `tests/visual/laboratorio/fuente-misiones.js` en
lugar de la fuente real (escenarios `misiones-*` de `escenarios-poblados.js`).
«El Templo Olvidado» es el ejemplo del documento (§7.8.14); el resto son datos
de laboratorio, y cada captura lo avisa. Nada de eso llega al producto.
