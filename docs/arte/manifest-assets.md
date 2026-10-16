# Manifiesto de assets — fase 2 de arte

> Generado por `docs/arte/generar-documentos.py` desde `manifest-assets.json` (fuente única).
> No editar a mano: editar el JSON y volver a generar.

Actualizado: 2026-10-16 · Raíz de los archivos finales: `shared/ui-kit/arte/` (se sirve en
`/shared/ui-kit/arte/…`, mismo origen). Estados: PENDIENTE → GENERADO → APROBADO → OPTIMIZADO → INTEGRADO.

## Cómo llega cada imagen a la pantalla

Ninguna vista construye una ruta a partir del nombre. Cada asset entra por el campo que ya
existe en su contrato:

| Entidad | Campo | Dónde se declara |
|---|---|---|
| Producto (héroe, arma, armadura, ítem, épica) | `imagen` de `productos.yaml` | `services/contenido/productos/.../semilla/catalogo-inicial.json` (nuevo campo `imagen` por entrada) |
| Misión | `imagen` de la misión (`Respuestas.Resumen`) | `services/contenido/misiones/.../semilla/misiones-*.json` (el campo ya existe, hoy `null`) |
| Subasta | `miniaturaUrl` (copia de `imagen` del producto al publicar) | ms-subastas, sin cambios |
| Retrato en combate y sala | `retratoUrl` si llega; si no, el retrato del prototipo (Tabla 6) | `comun/ui/juego/prototipos.js` |

## Especificación por tipo

| Tipo | Ratio | Generar (master) | Exportar | Peso objetivo |
|---|---|---|---|---|
| Héroe (retrato) | 1:1 | 1024 × 1024 PNG | WebP 640 × 640, calidad 82 | ≤ 90 KB |
| Misión (escena) | 16:9 | 1536 × 1024 PNG (se recorta a 16:9) | WebP 960 × 540, calidad 76 | ≤ 110 KB |
| Arma (objeto) | 1:1 | 1024 × 1024 PNG con fondo transparente | WebP 512 × 512 con alfa, calidad 80 | ≤ 45 KB |
| Armadura (objeto) | 1:1 | 1024 × 1024 PNG con fondo transparente | WebP 512 × 512 con alfa, calidad 80 | ≤ 45 KB |
| Ítem (objeto) | 1:1 | 1024 × 1024 PNG con fondo transparente | WebP 512 × 512 con alfa, calidad 80 | ≤ 45 KB |
| Épica (poder) | 1:1 | 1024 × 1024 PNG | WebP 512 × 512, calidad 82 | ≤ 70 KB |
| Otro (objeto) | 1:1 | 1024 × 1024 PNG con fondo transparente | WebP 512 × 512 con alfa, calidad 80 | ≤ 45 KB |
| Emblema de equipo | 1:1 | 1024 × 1024 PNG | WebP 256 × 256, calidad 80 | ≤ 25 KB |

Los masters (PNG del generador) no se suben al repositorio: solo el WebP final.

## Lotes

| Lote | Contenido | Assets | Nota |
|---|---|---|---|
| 0 | Prueba de dirección artística | 3 | Un héroe, un arma y una misión, juntos, antes de producir el resto. Las tres aprobadas son la referencia de su categoría. |
| 1 | Héroes y misiones (P0) | 14 | Los siete héroes y las siete misiones que faltan tras la prueba. |
| 2 | Armas | 15 | Las quince armas que faltan tras la prueba (Tablas 8-19 del documento). |
| 3 | Armaduras | 16 | Las dieciséis armaduras, una por parte del cuerpo indicada en el documento. |
| 4 | Ítems y épicas | 17 | Ocho ítems y nueve épicas (ocho de la Tabla 20 y la de la misión del Templo). |
| 5 | Otros (opcional, decisión del PO) | 11 | No bloquean el cierre: el Cofre de Bronce (recompensa del Templo, sin producto en el catálogo) y los diez emblemas de equipo de torneo, que hoy son ilustraciones caricaturescas de otro estilo. |

## Manifiesto

| ID | Tipo | Nombre | Archivo | Ratio | Uso | Estado | Fuente |
|---|---|---|---|---|---|---|---|
| HER-01 | Héroe (retrato) | Guerrero Tanque | `shared/ui-kit/arte/heroes/guerrero-tanque.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-guerrero-tanque` (catalogo-inicial.json) |
| HER-02 | Héroe (retrato) | Guerrero Armas | `shared/ui-kit/arte/heroes/guerrero-armas.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-guerrero-armas` (catalogo-inicial.json) |
| HER-03 | Héroe (retrato) | Mago Fuego | `shared/ui-kit/arte/heroes/mago-fuego.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-mago-fuego` (catalogo-inicial.json) |
| HER-04 | Héroe (retrato) | Mago Hielo | `shared/ui-kit/arte/heroes/mago-hielo.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-mago-hielo` (catalogo-inicial.json) |
| HER-05 | Héroe (retrato) | Pícaro Veneno | `shared/ui-kit/arte/heroes/picaro-veneno.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-picaro-veneno` (catalogo-inicial.json) |
| HER-06 | Héroe (retrato) | Pícaro Machete | `shared/ui-kit/arte/heroes/picaro-machete.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-picaro-machete` (catalogo-inicial.json) |
| HER-07 | Héroe (retrato) | Chamán | `shared/ui-kit/arte/heroes/chaman.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-chaman` (catalogo-inicial.json) |
| HER-08 | Héroe (retrato) | Médico | `shared/ui-kit/arte/heroes/medico.webp` | 1:1 | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión | INTEGRADO | Tabla 6 · `heroe-medico` (catalogo-inicial.json) |
| MIS-01 | Misión (escena) | El Templo Olvidado | `shared/ui-kit/arte/misiones/templo-olvidado.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Documento §7.8.14 · `templo-olvidado` |
| MIS-02 | Misión (escena) | El Sendero de los Aprendices | `shared/ui-kit/arte/misiones/sendero-de-los-aprendices.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión (cambio autorizado n.º 2) · `sendero-de-los-aprendices` |
| MIS-03 | Misión (escena) | La Mina Abandonada | `shared/ui-kit/arte/misiones/mina-abandonada.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `mina-abandonada` |
| MIS-04 | Misión (escena) | El Pantano de los Susurros | `shared/ui-kit/arte/misiones/pantano-de-los-susurros.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `pantano-de-los-susurros` |
| MIS-05 | Misión (escena) | La Fortaleza Quebrada | `shared/ui-kit/arte/misiones/fortaleza-quebrada.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `fortaleza-quebrada` |
| MIS-06 | Misión (escena) | Las Cumbres Heladas | `shared/ui-kit/arte/misiones/cumbres-heladas.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `cumbres-heladas` |
| MIS-07 | Misión (escena) | El Volcán Dormido | `shared/ui-kit/arte/misiones/volcan-dormido.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `volcan-dormido` |
| MIS-08 | Misión (escena) | La Ciudadela de las Sombras | `shared/ui-kit/arte/misiones/ciudadela-de-las-sombras.webp` | 16:9 | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte | INTEGRADO | Progresión · `ciudadela-de-las-sombras` |
| ARM-01 | Arma (objeto) | Espada de una mano | `shared/ui-kit/arte/productos/armas/espada-de-una-mano.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Guerrero Tanque |
| ARM-02 | Arma (objeto) | Espada de dos manos | `shared/ui-kit/arte/productos/armas/espada-de-dos-manos.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Guerrero Armas |
| ARM-03 | Arma (objeto) | Escudo de dragón | `shared/ui-kit/arte/productos/armas/escudo-de-dragon.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Guerrero Tanque |
| ARM-04 | Arma (objeto) | Piedra de afilar | `shared/ui-kit/arte/productos/armas/piedra-de-afilar.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Guerrero Armas |
| ARM-05 | Arma (objeto) | Orbe de manos ardientes | `shared/ui-kit/arte/productos/armas/orbe-de-manos-ardientes.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Mago Fuego |
| ARM-06 | Arma (objeto) | Báculo de Permafrost | `shared/ui-kit/arte/productos/armas/baculo-de-permafrost.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Mago Hielo |
| ARM-07 | Arma (objeto) | Fuego fatuo | `shared/ui-kit/arte/productos/armas/fuego-fatuo.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Mago Fuego |
| ARM-08 | Arma (objeto) | Venas heladas | `shared/ui-kit/arte/productos/armas/venas-heladas.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Mago Hielo |
| ARM-09 | Arma (objeto) | Daga purulenta | `shared/ui-kit/arte/productos/armas/daga-purulenta.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Pícaro Veneno |
| ARM-10 | Arma (objeto) | Machete vendito | `shared/ui-kit/arte/productos/armas/machete-vendito.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Pícaro Machete |
| ARM-11 | Arma (objeto) | Visión borrosa | `shared/ui-kit/arte/productos/armas/vision-borrosa.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Pícaro Veneno |
| ARM-12 | Arma (objeto) | Cierra sangrienta | `shared/ui-kit/arte/productos/armas/cierra-sangrienta.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Pícaro Machete |
| ARM-13 | Arma (objeto) | Raíz china | `shared/ui-kit/arte/productos/armas/raiz-china.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Chamán |
| ARM-14 | Arma (objeto) | Kit de urgencias | `shared/ui-kit/arte/productos/armas/kit-de-urgencias.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Médico |
| ARM-15 | Arma (objeto) | Yerbabuena | `shared/ui-kit/arte/productos/armas/yerbabuena.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Chamán |
| ARM-16 | Arma (objeto) | Reanimador | `shared/ui-kit/arte/productos/armas/reanimador.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · arma de Médico |
| ARD-01 | Armadura (objeto) | Defensa del enfurecido | `shared/ui-kit/arte/productos/armaduras/defensa-del-enfurecido.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Guerrero Tanque |
| ARD-02 | Armadura (objeto) | Puño lúcido | `shared/ui-kit/arte/productos/armaduras/puno-lucido.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Guantes de Guerrero Armas |
| ARD-03 | Armadura (objeto) | Magma Ardiente | `shared/ui-kit/arte/productos/armaduras/magma-ardiente.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Casco de Guerrero Tanque |
| ARD-04 | Armadura (objeto) | Puños en llamas | `shared/ui-kit/arte/productos/armaduras/punos-en-llamas.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Brazaletes de Guerrero Armas |
| ARD-05 | Armadura (objeto) | Túnica arcana | `shared/ui-kit/arte/productos/armaduras/tunica-arcana.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Mago Fuego |
| ARD-06 | Armadura (objeto) | Corona de hielo | `shared/ui-kit/arte/productos/armaduras/corona-de-hielo.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Casco de Mago Hielo |
| ARD-07 | Armadura (objeto) | Caída de fuego | `shared/ui-kit/arte/productos/armaduras/caida-de-fuego.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pantalón de Mago Fuego |
| ARD-08 | Armadura (objeto) | Ventisca | `shared/ui-kit/arte/productos/armaduras/ventisca.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Mago Hielo |
| ARD-09 | Armadura (objeto) | Mano del desterrado | `shared/ui-kit/arte/productos/armaduras/mano-del-desterrado.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Guantes de Pícaro Veneno |
| ARD-10 | Armadura (objeto) | Pie de atleta | `shared/ui-kit/arte/productos/armaduras/pie-de-atleta.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Zapatos de Pícaro Machete |
| ARD-11 | Armadura (objeto) | Atadura carmesí | `shared/ui-kit/arte/productos/armaduras/atadura-carmesi.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Pícaro Veneno |
| ARD-12 | Armadura (objeto) | Sangre cruel | `shared/ui-kit/arte/productos/armaduras/sangre-cruel.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Brazaletes de Pícaro Machete |
| ARD-13 | Armadura (objeto) | Piel de Caminante del Bosque | `shared/ui-kit/arte/productos/armaduras/piel-de-caminante-del-bosque.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Chamán |
| ARD-14 | Armadura (objeto) | Bata de Cirujano | `shared/ui-kit/arte/productos/armaduras/bata-de-cirujano.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pecho de Médico |
| ARD-15 | Armadura (objeto) | Casco de Ecos Ancestrales | `shared/ui-kit/arte/productos/armaduras/casco-de-ecos-ancestrales.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Casco de Chamán |
| ARD-16 | Armadura (objeto) | Pantalón de Expedición Médica | `shared/ui-kit/arte/productos/armaduras/pantalon-de-expedicion-medica.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · Pantalón de Médico |
| ITM-01 | Ítem (objeto) | Pinchos de escudo | `shared/ui-kit/arte/productos/items/pinchos-de-escudo.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Guerrero Tanque |
| ITM-02 | Ítem (objeto) | Empuñadura de Furia | `shared/ui-kit/arte/productos/items/empunadura-de-furia.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Guerrero Armas |
| ITM-03 | Ítem (objeto) | Anillo para Piro-explosión | `shared/ui-kit/arte/productos/items/anillo-para-piro-explosion.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Mago Fuego |
| ITM-04 | Ítem (objeto) | Libro de la ventisca helada | `shared/ui-kit/arte/productos/items/libro-de-la-ventisca-helada.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Mago Hielo |
| ITM-05 | Ítem (objeto) | Veneno lacerante | `shared/ui-kit/arte/productos/items/veneno-lacerante.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Pícaro Veneno |
| ITM-06 | Ítem (objeto) | Mancuerna yugular | `shared/ui-kit/arte/productos/items/mancuerna-yugular.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Pícaro Machete |
| ITM-07 | Ítem (objeto) | Pluma sanadora | `shared/ui-kit/arte/productos/items/pluma-sanadora.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Chamán |
| ITM-08 | Ítem (objeto) | Benditas | `shared/ui-kit/arte/productos/items/benditas.webp` | 1:1 | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle | INTEGRADO | Tablas 8-19 · ítem de Médico |
| EPI-01 | Épica (poder) | Golpe de defensa | `shared/ui-kit/arte/productos/epicas/golpe-de-defensa.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Guerrero Tanque |
| EPI-02 | Épica (poder) | Segundo impulso | `shared/ui-kit/arte/productos/epicas/segundo-impulso.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Guerrero Armas |
| EPI-03 | Épica (poder) | Luz cegadora | `shared/ui-kit/arte/productos/epicas/luz-cegadora.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Mago Fuego |
| EPI-04 | Épica (poder) | Frio concentrado | `shared/ui-kit/arte/productos/epicas/frio-concentrado.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Mago Hielo |
| EPI-05 | Épica (poder) | Toma y lleva | `shared/ui-kit/arte/productos/epicas/toma-y-lleva.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Pícaro Veneno |
| EPI-06 | Épica (poder) | Intimidación sangrienta | `shared/ui-kit/arte/productos/epicas/intimidacion-sangrienta.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Pícaro Machete |
| EPI-07 | Épica (poder) | Té changua | `shared/ui-kit/arte/productos/epicas/te-changua.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Chamán |
| EPI-08 | Épica (poder) | Reanimador 3000 | `shared/ui-kit/arte/productos/epicas/reanimador-3000.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Tabla 20 · épica de Médico |
| EPI-09 | Épica (poder) | Velo de Sombras | `shared/ui-kit/arte/productos/epicas/velo-de-sombras.webp` | 1:1 | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) | INTEGRADO | Documento §7.8.14 (Master «Sombra del Olvido») · épica de Pícaro Veneno |
| OTR-01 | Otro (objeto) | Cofre de Bronce | `shared/ui-kit/arte/otros/cofre-de-bronce.webp` | 1:1 | Mis cofres, recompensa de misión | PENDIENTE | Documento §7.8.14 (recompensa garantizada; sin producto en el catálogo) |
| EMB-01 | Emblema de equipo | Emblema «Guerrero» | `shared/ui-kit/arte/emblemas/emblema-01.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-01` (hoy `cuentas/avatares/*.jpg`) |
| EMB-02 | Emblema de equipo | Emblema «Escudo» | `shared/ui-kit/arte/emblemas/emblema-02.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-02` (hoy `cuentas/avatares/*.jpg`) |
| EMB-03 | Emblema de equipo | Emblema «Fuego» | `shared/ui-kit/arte/emblemas/emblema-03.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-03` (hoy `cuentas/avatares/*.jpg`) |
| EMB-04 | Emblema de equipo | Emblema «Hielo» | `shared/ui-kit/arte/emblemas/emblema-04.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-04` (hoy `cuentas/avatares/*.jpg`) |
| EMB-05 | Emblema de equipo | Emblema «Sabio» | `shared/ui-kit/arte/emblemas/emblema-05.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-05` (hoy `cuentas/avatares/*.jpg`) |
| EMB-06 | Emblema de equipo | Emblema «Sombra» | `shared/ui-kit/arte/emblemas/emblema-06.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-06` (hoy `cuentas/avatares/*.jpg`) |
| EMB-07 | Emblema de equipo | Emblema «Arco» | `shared/ui-kit/arte/emblemas/emblema-07.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-07` (hoy `cuentas/avatares/*.jpg`) |
| EMB-08 | Emblema de equipo | Emblema «Sanación» | `shared/ui-kit/arte/emblemas/emblema-08.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-08` (hoy `cuentas/avatares/*.jpg`) |
| EMB-09 | Emblema de equipo | Emblema «Alquimia» | `shared/ui-kit/arte/emblemas/emblema-09.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-09` (hoy `cuentas/avatares/*.jpg`) |
| EMB-10 | Emblema de equipo | Emblema «Comandante» | `shared/ui-kit/arte/emblemas/emblema-10.webp` | 1:1 | crear equipo (torneos), cuadro del torneo | PENDIENTE | `plataforma/torneos/equipo-formulario.js` · `emblema-10` (hoy `cuentas/avatares/*.jpg`) |
