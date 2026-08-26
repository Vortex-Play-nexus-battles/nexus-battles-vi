# Vitrina del inventario

Vista de `HU-INV-001`. Consume la consulta paginada del servicio de inventario
(`SCRUM-318`): `GET /api/v1/inventarios/{propietarioId}/elementos?pagina=N`.

## Alcance actual

Incluido en `SCRUM-319`:

- rejilla de 4 x 4 con los 16 productos de la pagina a 1360 x 768;
- pagina corta sin relleno de huecos;
- nombre propio del jugador escrito como texto, nunca como marcado;
- rechazo ruidoso de una pagina que exceda los 16 elementos acordados;
- cliente HTTP de la consulta paginada, con `fetch` inyectable.

Pendiente en otras subtareas: la reorganizacion en resoluciones inferiores y el
estado vacio son `SCRUM-320`; las pruebas de aceptacion en navegador real, con
la medida de "sin desplazamiento horizontal y todo legible", son `SCRUM-321`.

## Medidas verificadas a 1360 x 768

| Escenario | Tarjetas | Filas | Alto | Scroll horizontal |
|---|---|---|---|---|
| 16 productos | 16 | 4 | 768 px | no |
| 7 productos | 7 | 2 | 768 px | no |
| nombre de 300 caracteres | 16 | 4 | 877 px | no |

El tercer caso desborda a lo alto pero nunca a lo ancho: el nombre se parte con
`overflow-wrap`. El criterio de aceptacion solo prohibe el desplazamiento
horizontal.

## Identidad

El `propietarioId` viaja hoy como parametro. La validacion contra la identidad
autenticada llega con `HU-INF-009`; sin ella el endpoint no debe exponerse
fuera del entorno interno.
