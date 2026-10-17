# Auditoría de arte — Nexus Battles VI (fase 2)

Fecha: 14-oct-2026 · Base: `develop` @ `f01fb1b95` y AWS DEV (`https://nexusbattlesvi.com`).
Continúa la fase de fondos (`shared/ui-kit/fondos/`, HU-UX-002), que no se toca.

## Cómo se auditó

| Fuente | Qué se miró |
|---|---|
| Repositorio | Todos los `png/webp/jpg/svg` de `frontend/app-web` y `shared/ui-kit`; cada lectura de `imagen`, `imagenUrl`, `miniaturaUrl`, `retratoUrl` y cada símbolo de reserva (`icono('bandera')`, `icono('espada')`, símbolo por tipo). |
| Catálogo | `productos/.../semilla/catalogo-inicial.json`, `misiones/.../semilla/misiones-*.json` y la guarda `tests/contratos/catalogo-oficial.py`, que compara la semilla con `contracts/esquemas/catalogo-oficial.yaml` (copia del documento del cliente): **en verde**. |
| AWS DEV, API pública | `GET /api/v1/vitrina?size=50`, `GET /api/v1/subastas` (todas las páginas), `GET /api/v1/subastas/{id}`, `GET /api/v1/productos/{id}`, `GET /api/v1/banners/vigentes`. |
| AWS DEV, navegador | Portada pública, login, registro, recuperar, restablecer y verificar con Playwright (DOM y capturas). |
| Capturas de Simón | Misiones (tablón y tarjetas), Subastas, Héroes del Nexo y ficha del héroe. Las vistas con sesión se auditaron por código, API y estas capturas: esta sesión no entra en DEV con una cuenta de jugador. |

## La causa raíz (una sola, y explica casi todo)

`MapeadorDelCatalogo.imagen(tipo)` siembra **todos** los productos del documento con una «figura
provisional»: un SVG embebido (`data:image/svg+xml,…`) que es un recuadro azul con el nombre del tipo
escrito en blanco («Héroe», «Arma», «Armadura», «Ítem», «Épica»). Como el campo `imagen` no está vacío,
cada vista lo trata como una imagen real y lo pinta:

- en la tienda y la portada, un recuadro estirado con la palabra «Arma» o «Armadura»;
- en Héroes del Nexo, el círculo con «Héroe»;
- en la ficha del héroe, el recuadro gigante con «Héroe»;
- en una subasta de un producto oficial, la misma figura (ms-subastas copia `imagen` en `miniaturaUrl` al publicar).

Los componentes ya saben hacerlo bien sin imagen: `retratoDeHeroe` pinta el símbolo del prototipo
(Tabla 6) y la tienda y la subasta pintan el símbolo del tipo cuando la imagen falta o falla. La
figura provisional se lo impide. Las misiones tienen `imagen: null` y caen en la bandera genérica.

## A. Assets actuales

| Asset | Archivos | Peso | Estado |
|---|---|---|---|
| Fondos ilustrados «La Ciudadela del Nexo» | 11 escenas × 2 tamaños (`shared/ui-kit/fondos/*.webp`) | 2,2 MB | REAL Y CORRECTO (no se toca) |
| Logotipo y emblema | `marca/nexus-battles-vi.{png,webp}`, `marca/emblema.{png,webp}` | 536 KB | REAL Y CORRECTO |
| Iconos de interfaz | `iconos/sprite.svg` (42 símbolos) | — | REAL Y CORRECTO (iconografía vectorial) |
| Emblemas de equipo de torneo | 10 JPG 1024 × 1024 en `cuentas/avatares/` | 2,2 MB | REAL, de otro estilo (caricatura chibi) y sin optimizar |
| Capturas de evidencia | 13 PNG en `src/contenido/inventario/evidencia/` | 804 KB | NO NECESITA IMAGEN: ninguna vista las usa; sobran en `src/` |

## B. Placeholders encontrados

| Placeholder | Dónde se ve | Entidades |
|---|---|---|
| Figura provisional SVG con el nombre del tipo | Portada, tienda, ficha de producto, inventario, equipo, Héroes del Nexo, ficha del héroe, lista de deseos, carrito, subasta de un producto oficial | 57 productos del documento (8 héroes, 16 armas, 16 armaduras, 8 ítems, 9 épicas) |
| Bandera genérica (`imagen: null`) | Tablón, banner «Nueva», detalle, en curso | 8 misiones de historia |
| Símbolo del prototipo / inicial | Sala, combate y validación del héroe (`retratoUrl` nulo) | 8 prototipos |

En la portada pública, seis de las ocho imágenes de la vitrina son la figura provisional («Arma»,
«Armadura» en letra blanca); las otras dos son el símbolo vectorial de reserva.

## C. Imágenes rotas

| Entidad | Valor de `imagen` | Efecto |
|---|---|---|
| Producto «Espada corta» (DEV, creado el 21-sep, no está en el documento) | `espada.png` (ruta relativa que no existe) | La tienda y la subasta caen al símbolo de espada |
| Producto «Casco de edicion limitada» (DEV, no está en el documento) | `casco.png` | Cae al símbolo de escudo |

No aparece ninguna imagen rota a la vista: el respaldo vectorial funciona. Las rutas sí están rotas.

## D. Assets genéricos y repetidos

| Asset | Repetido en | Causa |
|---|---|---|
| Recuadro «Arma» / «Armadura» / «Ítem» / «Héroe» / «Épica» | 57 productos | Causa raíz de arriba |
| Bandera | 8 misiones | `imagen: null` en la semilla de misiones |
| Espada genérica en Subastas | Las 80 subastas activas de DEV | Datos: **todas** son de «Espada corta», con `miniaturaUrl = espada.png`. El enlace subasta → producto → imagen funciona; el producto subastado no tiene imagen válida y no es del catálogo oficial |

## E. Héroes

Confirmados contra el documento (Tabla 6, guarda `catalogo-oficial.py` en verde): **8** —
Guerrero Tanque, Guerrero Armas, Mago Fuego, Mago Hielo, Pícaro Veneno, Pícaro Machete, Chamán y
Médico. Coinciden con los de la captura. Ninguno tiene retrato: los 8 llevan la figura provisional
«Héroe». Hacen falta **8 retratos** (HER-01…08). Un solo master cuadrado sirve para tarjeta (círculo),
ficha (franja ancha), selector, sala, combate y resultado.

Las 24 acciones (Tabla 7, tres por héroe) no son productos y se pintan como texto con iconos de
interfaz: NO NECESITAN imagen.

## F. Productos

Del documento: 16 armas, 16 armaduras, 8 ítems y 9 épicas (8 de la Tabla 20 y «Velo de Sombras», de
la misión del Templo, §7.8.14). La tienda de DEV vende 50: los 48 oficiales a la venta (las épicas no se
venden) y los 2 que no son del documento («Espada corta», «Casco de edicion limitada»).

### Armas

16, todas con la figura provisional. Dos no son espadas ni bastones y hay que dibujarlas como lo que
son: «Escudo de dragón» es un **arma** en el documento (se dibuja escudo) y «Piedra de afilar» es una
piedra. Cuatro no tienen forma descrita en el documento («Fuego fatuo», «Venas heladas», «Visión
borrosa» y el foco de «Raíz china»): el manifiesto propone una forma y la marca para aprobar con el PO.
Los nombres con grafía del documento se conservan tal cual («Machete vendito», «Cierra sangrienta»).

### Armaduras

16, cada una con su parte del cuerpo según el documento: 6 de pecho, 3 cascos, 2 pares de guantes,
2 pares de brazaletes, 2 pantalones y 1 par de zapatos. Todas con la figura provisional. Cada pieza
lleva el color de acento del héroe al que pertenece.

### Ítems

8 (uno por héroe): accesorios y consumibles. Todos con la figura provisional. Se dibujan más pequeños
y con brillo para no confundirse con armas o armaduras. «Mancuerna yugular» y «Benditas» llevan
interpretación a aprobar.

### Épicas

9. Son poderes, no objetos: se ilustran como emblemas de energía con anillo de runas y halo, para que
se lean como especiales. No se venden; aparecen en el inventario, como recompensa de un Master y en el
combate.

## G. Misiones

8, todas de categoría Historia: «El Templo Olvidado» (documento, §7.8.14) y las siete de progresión
del cambio autorizado n.º 2 (nivel 1 a 7). Desafío y Exploración no tienen misiones hoy (estado vacío).
Las 8 usan la bandera genérica. Se crea **una escena 16:9 por misión** a partir de su narrativa,
enemigos y jefe de la semilla, sin añadir nada que la contradiga, y con la luz según la dificultad:
fácil, atardecer; normal, noche azul; difícil, noche violeta o volcánica.

La misión técnica `[PROVISIONAL DE DEV] Prueba de humo` solo se carga en el banco: NO NECESITA imagen.

## H. Torneos

Los torneos son dinámicos (los crea la administración) y ya tienen su fondo (`torneo`). No hace falta
arte por torneo. Lo que sí desentona son los **10 emblemas de equipo** (chibi caricaturesco, contra la
dirección «sin caricatura»), que además pesan 2,2 MB en JPG. Se proponen 10 emblemas heráldicos en el
lote 5, opcional y por decisión del PO, porque hoy están en uso.

## I. Subastas

- **El código está bien.** Al publicar, ms-subastas copia en `miniaturaUrl` la `imagen` del producto
  (`PublicarSubastaApplicationService`) y la vitrina la pinta con su respaldo por tipo.
- **Los datos de DEV no.** Las 80 subastas activas son de «Espada corta», que no está en el documento
  y tiene `imagen = espada.png`. Por eso todas muestran la misma espada.
- **Al llegar el arte**, cada subasta nueva copia la imagen real de su producto. Las que ya existen
  conservan su copia hasta cerrar (duran 24 h a 72 h). No hace falta cambiar ms-subastas.
- **Mejora opcional** (contrato de otro equipo): añadir `productoId` al listado para resolver la imagen
  al pintar, como hace el inventario (`retratos.js`). No bloquea.

## J. Combate

La arena ya tiene fondo (`arena.webp`). Los retratos de sala, validación y combate se pintan con
`retratoDeHeroe(retratoUrl)`. Sin `retratoUrl`, se pinta el símbolo del prototipo. Con los 8 retratos,
el respaldo pasa a ser el retrato del prototipo (los prototipos son fijos, Tabla 6), así que el
combate gana arte sin cambiar salas-partidas. Los efectos y acciones siguen como iconos y
animaciones (sistema de movimiento). No se inicia un motor de sprites.

## K. Otros

| Elemento | Estado | Decisión |
|---|---|---|
| Avatar de cuenta | Lo sube el jugador al registrarse (recorte 512 px); si no, inicial | NO NECESITA arte; no se usan retratos de héroe como avatar |
| Banners rotativos | 0 vigentes en DEV; los sube la administración con su recurso gráfico | NO NECESITA arte generado |
| Cofres | Símbolo `cofre` | Lote 5 opcional: «Cofre de Bronce», la recompensa garantizada del Templo |
| Recompensas del Templo sin producto | «Fragmento del Sello Antiguo», «Piel del Guardián», «Espada del Templo», título «Explorador del Templo» | DRIFT: el documento las nombra y el catálogo no las tiene (`productoId: null`). No se dibujan productos que no existen |
| Capturas en `src/contenido/inventario/evidencia/` | 804 KB servidos sin uso | Mover a `docs/evidencia/` en ART-7 |

## L. Assets que NO necesitan reemplazo

Los fondos, la arena, el logotipo, el emblema y los 42 iconos del sprite. Tampoco las acciones, los
estados, los filtros, los botones ni los avatares de cuenta. La consola de administración no lleva
imágenes decorativas (decisión del PO de la fase de fondos).

## M. Conteo

```text
Assets correctos:   13 assets ráster (11 fondos + logotipo + emblema) y el sprite de 42 iconos
Assets faltantes:   65 (8 héroes, 8 misiones, 16 armas, 16 armaduras, 8 ítems, 9 épicas)
Placeholders:       65 entidades (57 productos con la figura provisional + 8 misiones con bandera)
Rotos:              2 productos de DEV fuera del documento (espada.png, casco.png) y las 80 subastas que heredan espada.png
Reutilizables:      fondos y arena; símbolos vectoriales como respaldo; ranuras de imagen ya hechas en
                    tienda, ficha, inventario, misiones (tarjeta, banner, detalle) y retratoDeHeroe
Nuevos a crear:     65 obligatorios + 11 opcionales del lote 5 (Cofre de Bronce y 10 emblemas) = 76 como máximo
```

## Drift a registrar antes de generar arte

| # | Drift | Acción |
|---|---|---|
| 1 | DEV tiene 2 productos que no están en el documento («Espada corta», «Casco de edicion limitada»), con imagen rota. Todas las subastas activas son de «Espada corta» | Decisión del PO. Si no son del juego, la administración los **suspende** con motivo: no se borran, se conservan en los inventarios y dejan de venderse. No se les hace arte |
| 2 | Recompensas del Templo sin producto en el catálogo | Decisión del PO: crear los productos o dejarlas como texto. Sin producto no hay arte |
| 3 | `MapeadorDelCatalogo.ponerAlDia` reescribe `imagen` cada vez que sube la versión de la semilla | Si la administración cambió la imagen de un producto, la semilla no la pisa: solo reemplaza la figura provisional o una ruta anterior de `/shared/ui-kit/arte/` (ART-4) |
| 4 | Los nombres con grafía del documento («Machete vendito», «Cierra sangrienta», «Frio concentrado») | Se conservan en los datos; el arte dibuja el significado evidente (vendas y bendición, sierra) |

## Matriz de assets por pantalla

| Asset | Pantalla | Estado | Fuente de datos | Repetido | Necesita arte | Prioridad |
|---|---|---|---|---|---|---|
| Retrato de héroe | Héroes del Nexo | PLACEHOLDER | `producto.imagen` (semilla) | Sí, 8 iguales | Sí | P0 |
| Retrato de héroe | Ficha del héroe | PLACEHOLDER (recuadro gigante «Héroe») | `producto.imagen` | Sí | Sí | P0 |
| Retrato de héroe | Inventario, equipo, estrategia | PLACEHOLDER | `producto.imagen` vía `retratos.js` | Sí | Sí | P0 |
| Retrato de héroe | Sala, validación, combate | GENÉRICO (símbolo del prototipo) | `retratoUrl` (salas-partidas) | No | Sí (reutiliza el retrato) | P1 |
| Escena de misión | Tablón, banner, detalle, en curso | GENÉRICO (bandera) | `mision.imagen` = `null` | Sí, 8 iguales | Sí | P0 |
| Imagen de producto | Portada y tienda | PLACEHOLDER | `vitrina.imagenUrl` | Sí, por tipo | Sí | P0 |
| Imagen de producto | Ficha de producto | PLACEHOLDER | `producto.imagen` | Sí | Sí | P1 |
| Imagen de producto | Inventario (vitrina) | PLACEHOLDER | `producto.imagen` vía `retratos.js` | Sí | Sí | P1 |
| Miniatura de subasta | Subastas, detalle, pujas | ROTO (datos: «Espada corta») / PLACEHOLDER (oficiales) | `miniaturaUrl` | Sí | No: hereda la del producto | P1 |
| Épica | Inventario, recompensa, combate | PLACEHOLDER | `producto.imagen` | Sí | Sí | P2 |
| Cofre | Mis cofres | GENÉRICO (símbolo) | — | Sí | Opcional | P3 |
| Emblema de equipo | Torneos | REAL, de otro estilo | `EMBLEMAS` (frontend) | No | Opcional (PO) | P3 |
| Fondos, arena, logotipo | Todas | REAL Y CORRECTO | `fondos.css`, `marca/` | No | No | — |
| Iconos de acción, estado y filtro | Todas | NO NECESITA IMAGEN | `sprite.svg` | — | No | — |

## Plan de producción

| Bloque | Qué | Depende de |
|---|---|---|
| **ART-0** (antes de que lleguen las imágenes) | Una sola regla `esImagenProvisional()`: la figura provisional de la semilla y una ruta relativa suelta (`espada.png`) cuentan como «sin imagen». Así se ve el respaldo vectorial (prototipo o tipo) en lugar de un recuadro con texto. Arregla ya, sin arte, todas las vistas de B. Jest de cada vista | — |
| **ART-1** Dirección artística | Lote 0: HER-01, ARM-01 y MIS-01 juntos y comparados con los fondos. Se aprueban o se ajusta el prompt | Imágenes del lote 0 |
| **ART-2** Héroes | 8 retratos en `arte/heroes/`; la semilla les pone `imagen`; `prototipos.js` gana `retrato` para sala y combate | ART-1 |
| **ART-3** Misiones | 8 escenas en `arte/misiones/`; `imagen` en las semillas de misiones; la tarjeta, el banner y el detalle ya las pintan | ART-1 |
| **ART-4** Productos | 49 imágenes (armas, armaduras, ítems, épicas); `imagen` en `catalogo-inicial.json`, versión de la semilla +1, y el mapeador no pisa una imagen puesta por la administración | ART-1 |
| **ART-5** Tienda, inventario, subastas | Verificar que tienda, ficha, vitrina, equipo, lista de deseos, carrito y subasta usan la misma ruta (`arte/productos/…`, una sola fuente); encaje `object-fit: contain` sobre la ranura oscura | ART-4 |
| **ART-6** Otros módulos | Combate y sala con el retrato del prototipo; lote 5 si el PO lo aprueba | ART-2 |
| **ART-7** Optimización | WebP con los tamaños y pesos del manifiesto, `width`/`height` y `aspect-ratio` en cada `<img>`, `loading="lazy"` salvo el LCP; mover las capturas de `src/` a `docs/`; emblemas a WebP de 256 px | ART-2…5 |
| **ART-8** Responsive y accesibilidad | 1440, 1366, 1024, 768 y 375 en tarjeta, modal, detalle y móvil. Alt: el retrato con el nombre del héroe cuando informa, `alt=""` cuando el nombre ya está al lado (el marco ya lleva `role="img"` y `aria-label`), decorativas `alt=""`. axe en los escenarios poblados | ART-7 |
| **ART-9** Regresión visual y guardián | `tests/contratos/arte-del-catalogo.py`: cada asset INTEGRADO del manifiesto existe, no pesa 0 bytes, es WebP con sus dimensiones y no supera su peso, y la semilla no apunta a archivos inexistentes ni conserva la figura provisional en una entidad con arte integrado. Laboratorio visual con capturas por lote. CI, PR, despliegue y comprobación en DEV | ART-8 |

Este trabajo se cuelga de las HU funcionales que ya existen (catálogo e inventario, misiones,
subastas, héroes) como tareas y bugs de UX. No se crea una HU por imagen.

## Criterio ART DONE

```text
[ ] héroes sin placeholders                    [ ] fallbacks correctos (ART-0)
[ ] cards de héroe con retratos reales         [ ] no imágenes rotas
[ ] detalle de héroe con arte real             [ ] naming consistente (manifiesto)
[ ] misiones con identidad visual              [ ] WebP optimizado
[ ] subastas usando imagen real del producto   [ ] lazy loading
[ ] tienda sin placeholders injustificados     [ ] responsive
[ ] inventario sin placeholders injustificados [ ] alt correcto
[ ] productos importantes con assets           [ ] tests verdes
[ ] regresión visual verde                     [ ] DEV verificado
```
