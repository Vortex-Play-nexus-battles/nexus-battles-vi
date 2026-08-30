# Gestión y vitrina del inventario

Vista de `HU-INV-001`. Consume la consulta paginada del servicio de inventario
`GET /api/v1/inventario/elementos?pagina=N`, con la identidad en la cabecera
`X-User-Name`.

La integración de `HU-INV-003` añade la creación por `POST` y la edición del
nombre por `PATCH`. Después de cada escritura, la vista vuelve a consultar el
inventario persistido y muestra el resultado en la cuadrícula.

## Alcance actual

**Cuadricula de referencia**:

- rejilla de 4 x 4 con los 16 productos de la pagina a 1360 x 768;
- pagina corta sin relleno de huecos;
- nombre propio del jugador escrito como texto, nunca como marcado;
- rechazo ruidoso de una pagina que exceda los 16 elementos acordados;
- cliente HTTP de la consulta paginada, con `fetch` inyectable.
- formulario para crear elementos y editar el nombre de elementos propios;
- actualización de la vitrina después de guardar, incluida la página donde
  queda el elemento nuevo cuando el inventario supera los 16 elementos.

**Resoluciones inferiores y estados**:

- reorganizacion de la rejilla en resoluciones inferiores, sin encoger el texto;
- los cuatro estados de `RNF-USA-003` —carga, error, exito y vacio— en
  `estados-vista.js`, con `pagina-inventario.js` decidiendo cual mostrar;
- estado vacio explicativo que no parece un error;
- ningun codigo del protocolo a la vista del jugador: el detalle tecnico va a
  la consola. Regla del cliente del 2026-08-13, *"uno como usuario jamas
  deberia ver un status de HTML"*.

**Pruebas de aceptacion**:

- los cuatro escenarios de `HU-INV-001-vitrina-del-inventario.feature`
  traducidos uno a uno a Playwright sobre Chromium, con el esquema del
  criterio 2 expandido a sus tres resoluciones;
- el servicio de SCRUM-318 respondido por intercepcion de red, para que la
  prueba mida la vista y no la disponibilidad del backend;
- "sin desplazamiento horizontal" medido como `scrollWidth > clientWidth`
  sobre el documento;
- "todo texto permanece legible" medido como fuente por encima de 12 px y
  ningun texto recortado por su caja.

El `.feature` vive junto a la prueba a proposito: los criterios de aceptacion
**son** las pruebas, y tenerlos al lado hace visible cualquier divergencia.

## Medidas verificadas a 1360 x 768

| Escenario | Tarjetas | Filas | Alto | Scroll horizontal |
|---|---|---|---|---|
| 16 productos | 16 | 4 | 768 px | no |
| 7 productos | 7 | 2 | 768 px | no |
| nombre de 300 caracteres | 16 | 4 | 877 px | no |

## Medidas verificadas en resoluciones inferiores

| Ventana | Columnas | Ancho de tarjeta | Tamano del texto | Scroll horizontal |
|---|---|---|---|---|
| 1360 x 768 | 4 | 313 px | 15,2 px | no |
| 1024 x 768 | 3 | 307 px | 15,2 px | no |
| 768 x 1024 | 2 | 353 px | 15,2 px | no |
| 375 x 812 | 1 | ancho completo | 15,2 px | no |

**El tamano del texto no cambia en ningun corte.** Encoger la letra para que
quepa seria exactamente lo que el criterio 2 prohibe: lo que baja es el numero
de columnas y los margenes, nunca la legibilidad.

El tercer caso desborda a lo alto pero nunca a lo ancho: el nombre se parte con
`overflow-wrap`. El criterio de aceptacion solo prohibe el desplazamiento
horizontal.

## Deudas contra la pila aprobada

- La tarjeta y la paginacion de dieciseis son **componentes de `shared/ui-kit`**
  segun la pila; ese directorio esta vacio, asi que viven aqui provisionalmente.
- El tema de `vitrina.css` es provisional: la paleta y las tipografias
  autoalojadas (Rajdhani e Inter) salen de la propuesta de diseno, que aun no
  esta en el repositorio.
- Los cuatro estados de `RNF-USA-003` ya existen en `estados-vista.js`, pero la
  pila los quiere **centralizados para los veinte modulos**. Su hogar es
  `shared/ui-kit`; se mudan sin cambiar la interfaz.

## Pruebas

```bash
npm install
npm test              # unitarias, sobre jsdom
npm run test:aceptacion   # aceptacion, sobre Chromium real
```

Las de aceptacion levantan su propio servidor estatico; no hace falta el
backend, porque la respuesta del servicio se intercepta.

## Peticiones

Las peticiones `GET`, `POST` y `PATCH` salen por `fetchWithHttpErrorInterceptor`, el envoltorio comun de
`src/comun/interceptors/`, y no por `fetch` pelado: asi el manejo de Problem
Details (RFC 7807) es el mismo en los veinte modulos. El envoltorio sigue siendo
inyectable en `consultarPagina`, para que las pruebas no dependan de la red.

## Identidad

El jugador se identifica por la cabecera `X-User-Name`, la misma que usan la
creacion y la modificacion. **La identidad nunca viaja en la ruta**, asi que
nadie puede pedir el inventario ajeno cambiando la URL. Cuando llegue el
contrato de identidad (`HU-INF-009`), esa cabecera la pondra la sesion.
