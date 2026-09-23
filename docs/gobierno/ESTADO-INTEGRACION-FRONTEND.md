# Estado de la integracion frontend-backend — FRONTEND-INTEGRATION

Clasificacion de cada flujo tras los bloques FI-R0 a FI-R14. No hay `PARCIAL`:
cada linea dice **COMPLETO**, **FUERA DE ALCANCE** (con el RF y el grupo que lo
tiene), **BLOQUEADO POR BACKEND EXTERNO** (el servicio no existe y no nos toca
construirlo) o **BLOQUEADO POR PO** (hace falta una decision de producto).

El reparto de requisitos esta en `MAPA-RESPONSABILIDAD-RF.md`, extraido del
campo «Responsabilidad organizacional» de `REQUERIMIENTOS_EMPRESA_A_DIVISION.pdf`.

## Regla que se ha seguido

| Situacion | Que se hizo |
|---|---|
| RF de Grupo de Simon | se implementa entero, backend incluido |
| RF de otro grupo, backend ausente | el frontend deja de aparentar; se registra la deuda con RF y grupo; **no** se implementa su servicio |
| Defecto del frontend compartido | se arregla siempre, sea de quien sea el RF |

La segunda fila es la que evita pisar el Sprint de otro grupo, que es una
restriccion explicita del programa.

## Flujos COMPLETOS

| Flujo | RF | Grupo | Evidencia |
|---|---|---|---|
| Crear sala de batalla | RF-JUE-001 | Simon | `sala-de-batalla.e2e.spec.js` |
| Entrar a una sala publica | RF-JUE-002 | Simon | `sala-de-batalla.e2e.spec.js` |
| Entrar a una sala **privada** con codigo | RF-JUE-002 | Simon | `sala-privada-con-codigo.e2e.spec.js`, 7 casos |
| El anfitrion ve, copia y comparte su codigo | RF-JUE-002 | Simon | idem, con portapapeles concedido y leido |
| Exigencia de personaje equipado, dentro del flujo | RF-JUE-003 | Simon | `verificacion-heroe-en-el-flujo.e2e.spec.js`, 7 casos |
| Confirmar heroe y coste antes de comprometer creditos | RF-JUE-003 + RF-JUE-014 | Simon | idem |
| Salir y cancelar sala | RF-JUE-001 | Simon | `salir-y-cancelar-sala.e2e.spec.js` |
| Combate: turnos, accion, vida persistida | RF-JUE-005..011 | Thomas (backend) | `sala-de-batalla.e2e.spec.js` |
| Buscar usuario en administracion | RF-USR-008/009/010 | Simon | 16 pruebas de conducta |
| Sanciones: advertir, suspender, banear, reactivar | RF-USR-004..007 | Simon | `sanciones.e2e.spec.js` |
| Comentarios y su moderacion | RF-COM-001..009 | Simon | `comentarios.e2e.spec.js`, `moderacion-comentarios.e2e.spec.js` |
| Torneos | RF-TOR-001..008 | Simon | `torneos.e2e.spec.js` |
| Parametros del sistema | RF-ADM-001 | Simon | `parametros.e2e.spec.js` |
| Notificaciones en tiempo real, con degradacion | RF-NOT-006 | Simon | `bandeja.js` con reconexion y estados |
| Subastas: listado, puja, compra inmediata, automatica | RF-SUB-* | Santiago (backend) | el frontend consume todo lo publicado |
| Tiempo real de subastas, con degradacion y reconexion | RF-NOT-003 | Simon | 13 pruebas nuevas; **el canal estaba apagado y ahora funciona** |
| Equipamiento del heroe alcanzando todo el inventario | RF-INV-009 | Thomas (RF) | defecto del frontend, arreglado; 7 pruebas |
| Precio de la tienda | RF-CAR-001 | Santiago (RF) | defecto del frontend, arreglado; 19 pruebas |

## FUERA DE ALCANCE — requisito de otro grupo, sin backend

Ninguno de estos se ha simulado en el frontend. Donde habia una simulacion, se
quito; donde habia un CTA que prometia la funcion, esta apagado con su motivo.

### Pago del carrito

- **RF-CAR-010** «Resumen de compra y formulario de pago» y **RF-PAG-001**
  «Integracion con pasarela de pagos simulada». Los dos confirmados, prioridad
  Alta, Sprint 2, **de Grupo de Santiago**.
- Estado real: `CarritoController` expone `GET /carrito`, `POST /carrito/items` y
  `DELETE /carrito/items/{itemId}`. Nada mas. `ecommerce-carrito.yaml` declara
  esas mismas tres rutas y ninguna de pago.
- Que se hizo: el boton «Pagar» estaba **habilitado y sin ningun manejador**.
  Queda apagado, con el motivo escrito al lado y en la pantalla, sin citar
  identificadores de requisito a quien compra.

### Promociones y distintivos de la vitrina

- **RF-CAR-003** (promociones) y **RF-CAR-005** (distincion de propios y de lista
  de deseos), de Grupo de Santiago.
- Estado real: `VitrinaService.convertirADto` pone
  `precioFinal = precioOriginal = precioBaseCop`, asi que el porcentaje de
  descuento del producto **nunca llega al precio**; y escribe `esPropio` y
  `enListaDeseos` siempre en `false`.
- Que se hizo: el distintivo «-N%» solo sale cuando los dos precios difieren de
  verdad. Un «-30%» junto a un precio sin rebajar es una promesa que el carrito
  no cumple. El realce de propio y deseado existe en el CSS y no se encendera
  hasta que el servicio los calcule.

### Progresion del heroe y experiencia

- **RF-HER-003** «Progresion de nivel del heroe» y **RF-HER-004** «Otorgamiento de
  experiencia», de Grupo de Thomas.
- Estado real: `services/contenido/progresion-jugador/` y
  `services/contenido/misiones/` contienen **un unico README de 0 bytes** cada
  uno, y estan comentados en `settings.gradle` bajo «Pendientes de incorporar:
  sin build.gradle todavia».
- Que se hizo: **nada de UI**. No se construye una pantalla de progresion sobre
  un servicio que no existe, ni se ensena un nivel o una experiencia que nadie
  persiste. En subastas, el `nivel` que el cliente rellenaba con cero se quito
  (FI-R1).

### Transferencia de propiedad al adjudicar una subasta

- Cruza **RF-SUB-*** (Santiago) y **RF-INV-*** (Thomas).
- Estado real: `InventarioClientHttp` llama a
  `POST /api/v1/inventario/elementos/{id}/transferencias`. Esa ruta **no existe
  en el contrato de inventario ni en su controlador**: `inventario.yaml` declara
  el bloqueo de subasta, su liberacion y la consulta por id, y nada mas. El
  propio cliente lo dice en su mensaje de error: «o el elemento no existe, o
  —mas probable hoy— ms-inventario aun no expone esta ruta».
- Consecuencia real: **una subasta adjudicada puede no transferir la propiedad**.
  El ganador paga y el objeto no cambia de inventario.
- Por que no se implementa aqui: la ruta pertenece al servicio de inventario, de
  Grupo de Thomas, y el que la llama es de Grupo de Santiago. Anadirla seria
  decidir por los dos la forma de una operacion con dinero detras.
- Lo que hace falta de producto: idempotencia de la transferencia (un reintento
  no puede duplicar la entrega), y que hacer si el cobro entro y la
  transferencia no.

### Suspender y reactivar productos por REST

- **RF-PRD-005** «Suspension logica y reactivacion de productos», de Grupo de
  Thomas.
- Estado real: `productos.yaml` declara seis rutas; `ProductosController` expone
  **tres** (`GET /estadisticas`, `POST /`, `GET /{id}`). Faltan
  `/{id}/adquisiciones`, `/{id}/suspender` y `/{id}/reactivar`. El dominio si las
  tiene: `CatalogoProductos.suspender` y `.reactivar` existen y funcionan, sin
  nadie que las exponga.
- O sea que hay **una API fantasma**: el contrato promete tres operaciones que
  ningun cliente puede llamar. Eso es peor que no declararlas, porque un
  consumidor que lea el contrato las dara por disponibles.
- Que hace falta: o se exponen (controlador + seguridad + prueba de contrato +
  UI), o se corrige el contrato. Las dos decisiones son de Grupo de Thomas.

## Deuda del frontend que queda abierta

| Que | Donde | Por que sigue abierto |
|---|---|---|
| Objetivo tactil de «Estado del catalogo» a 375 px | `productos` | 151x38, por debajo de los 44 px. Unico hallazgo del barrido de 170 combinaciones |
| El guardian de CTA no ve las vistas que fallan cerradas sin backend | `tests/visual/cta-con-accion.spec.js` | Necesita bancos poblados. Comprobado y escrito en el propio fichero |
| `pujas.js` sigue armando su marcado con `innerHTML` | `cuentas/pujas.js` | Deuda conocida y anotada en el guardian: 2.297 lineas de plantilla, saneadas con `esc()` |

## Lo que este informe NO demuestra

- Que los servicios esten desplegados en AWS. Los E2E corren contra
  `tests/e2e/compose.yml`; el host de dev no levanta `inventario`, y por eso el
  flujo de la puerta de heroe no se puede correr alli (#435).
- Que la accesibilidad este bien. axe encuentra lo decidible mirando el DOM y
  los colores calculados; el orden de lectura y la utilidad de un texto
  alternativo siguen siendo trabajo de persona.
- Que un CTA con manejador haga lo que dice. El guardian de FI-R14 corta el caso
  del boton que nadie engancho; que la accion sea la correcta lo afirman las
  pruebas de conducta de cada vista, una por una.
