# Fondos ilustrados — «La Ciudadela del Nexo»

Once escenas de un mismo mundo detrás de las vistas del jugador (HU-UX-002, #925). La consola de
administración no lleva fondo: es una decisión del PO (Simón Pérez Gómez, oct 2026), no un
olvido.

- **Cómo se pintan, el velo y por qué**: `../css/fondos.css`.
- **Prueba de contraste sobre la imagen**: `tests/visual/contraste-fondos.spec.js` (corre en el
  laboratorio visual de cada PR). Evidencia con capturas y cifras: `docs/evidencia/fondos/`.
- **Guardián de catálogo** (atributo válido, imagen presente, la administración sin fondo):
  `frontend/app-web/src/comun/fondos.test.js`.

## Catálogo

Cada escena existe en dos tamaños: `<nombre>.webp` (1536 × 1024, calidad 80) y
`<nombre>-movil.webp` (960 × 640, calidad 72), que es el que se descarga por debajo de 900 px.

| `data-fondo` | Escena | Vistas | Composición |
|---|---|---|---|
| `entrada` | Login | `cuentas/login.html`, `cuentas/portada.html` | portal |
| `recuperar` | Recuperar contraseña | `cuentas/restablecer-solicitar.html`, `cuentas/restablecer-confirmar.html` | portal |
| `registro` | Crear cuenta | `cuentas/registro.html`, `cuentas/verificar-cuenta.html`, `cuentas/preparando.html` | portal |
| `inicio` | Inicio | `cuentas/index.html` | juego |
| `jugar` | Jugar online | `plataforma/salas-partidas/batallas.html`, `crear-sala.html`, `validacion-heroe.html`, `chat.html`, `sala-batalla.html` (sala de espera) | juego |
| — (`arena.webp`) | Sala de batalla | no es un valor de `data-fondo`: va dentro de `.combate__campo` de `plataforma/salas-partidas/sala-batalla.html` en cuanto la vista tiene fondo | campo |
| `torneo` | Torneo | `plataforma/torneos/torneos.html` | juego |
| `misiones` | Misiones | `contenido/misiones/misiones.html` | juego |
| `inventario` | Inventario | `contenido/inventario/inventario.html` (velo ancho), `cuentas/mis-cofres.html` | juego |
| `mercado` | Mercado | `cuentas/tienda.html`, `cuentas/subastas.html` (velo ancho), `cuentas/pujas.html`, `cuentas/publicar-subasta.html`, `cuentas/historial-transacciones.html` | juego |
| `cuenta` | Mi cuenta y comunidad | `cuentas/perfil.html`, `plataforma/notificaciones/notificaciones.html`, `plataforma/comentarios/publicar-comentario.html`, `plataforma/moderacion-sanciones/mis-sanciones.html` | juego |

Composición *portal*: tarjeta del formulario a la derecha y logotipo y lema a la izquierda.
*Juego*: contenido a todo lo ancho (máximo ≈ 1320 px), así que la escena se luce en los márgenes y
abajo. *Campo*: la imagen es el suelo del combate, con un bando a cada lado.

## Cómo se generaron

Las imágenes las generó Simón Pérez Gómez con ChatGPT (oct 2026), en una misma conversación y
adjuntando siempre la del login como referencia de estilo. Cada prompt se compone de bloques fijos
y una escena; los bloques fijos se pegan sin cambios para que todo salga del mismo mundo. Formato
horizontal 3:2 (1536 × 1024).

**Tema (fijo)**

> Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG, con pincelada detallada y acabado cinematográfico. El mundo es la Ciudadela del Nexo: una fortaleza gótica de piedra oscura, de noche, con torres afiladas, arcos y puentes. Paleta: índigo y azul marino profundos, violeta y energía cian eléctrica en runas talladas, cristales en forma de rombo y grietas de luz, con pequeños acentos cálidos de antorchas ámbar. Iluminación: luz de luna fría y azulada, neblina azul a ras de suelo y partículas de energía flotando. Emblema recurrente: un rombo de cristal cian rodeado de picos plateados, en estandartes azules y en la piedra. Mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta.

**Composición portal (fijo en login, recuperar y registro)**

> Formato horizontal 3:2. El 40 % izquierdo debe ser oscuro, en sombra y con poco detalle (roca, niebla, penumbra), porque ahí van el logotipo y texto blanco. El punto focal va en el centro-derecha (entre el 55 % y el 80 % del ancho, a media altura): una tarjeta blanca lo tapará en parte, así que su resplandor debe asomar alrededor. Franja superior oscura, sin luna ni brillos fuertes en la esquina superior derecha. Franja inferior en penumbra.

**Composición juego (fijo desde Inicio)**

> Composición: formato horizontal 3:2. Encima irá una página llena de paneles y tarjetas que tapan casi todo el centro, así que el centro debe ser tranquilo y oscuro, con poco detalle (niebla, penumbra). Lo que tiene que lucirse va en los bordes izquierdo y derecho (el 20 % de cada lado) y en la franja inferior, que es lo que queda a la vista. La esquina superior izquierda debe ser oscura porque ahí van títulos en blanco. Iluminación más tenue y pareja que en las pantallas de entrada, sin zonas muy brillantes.

**Composición campo (solo la arena)**

> Composición: formato horizontal 3:2, vista frontal ligeramente desde arriba para que el suelo de la arena ocupe casi toda la imagen. Encima se colocan las fichas redondas de los héroes con su nombre en blanco: tu equipo en la mitad izquierda y los rivales en la mitad derecha, a media altura. Esas dos zonas deben ser suelo amplio, plano, oscuro y despejado, sin objetos ni brillos fuertes. El lado izquierdo con un halo de luz azul y el derecho con un halo violeta, como dos bandos. En el centro exacto, una línea vertical de runas cian separa los dos bandos. Gradas, estandartes y braseros solo en la franja superior, y la franja inferior en penumbra, porque arriba y abajo van barras de la interfaz y la imagen se recorta.

**Evitar (fijo)**

> Sin texto, letras, números, logotipos, marcas de agua, firmas, elementos de interfaz, personajes ni rostros.

## Escenas

### `entrada` — Login

> (descrita a partir de la imagen aprobada) la gran puerta de la ciudadela de noche: un portón de piedra con la cresta del rombo cian sobre el arco, torres a los lados, estandartes azules, antorchas ámbar, una escalinata con niebla que sube hacia la puerta y la luna sobre las agujas.

### `recuperar` — Recuperar contraseña

> un patio apartado y silencioso de la misma ciudadela, al pie de una puerta lateral más pequeña que el gran portón. Frente a la puerta flota un candado rúnico circular: anillos concéntricos de runas cian que giran alrededor del rombo de cristal, como una cerradura mágica que se está volviendo a abrir. Fragmentos de runas luminosas vuelan hacia el candado y se encajan como si formaran una llave. Ambiente más calmado y misterioso que la entrada principal: más niebla, solo dos faroles ámbar a los lados de la puerta y unos pocos escalones que suben hacia ella.

### `registro` — Crear cuenta

> la llegada a la Ciudadela del Nexo. Un largo puente de piedra con almenas cruza un abismo cubierto de niebla: arranca abajo, en el centro de la imagen, y avanza en diagonal hacia la derecha hasta la ciudadela, que se alza al fondo en el centro-derecha con sus torres afiladas, sus ventanas de luz cian y el gran portón brillando a lo lejos. A lo largo del puente hay estandartes azules con el rombo y faroles de llama cian, y dos faroles ámbar en el inicio del puente. Sensación de comienzo y de camino por recorrer: amplitud, profundidad y niebla en el abismo. En esta pantalla la tarjeta del formulario es más ancha y más alta, así que la ciudadela debe ser grande y su silueta debe asomar por encima y a la derecha de la tarjeta.

### `inicio` — Inicio

> el corazón de la ciudadela, una gran plaza interior en penumbra, con suelo de losas, niebla baja y galerías de arcos góticos alrededor. En el borde derecho, en el tercio superior, flota el Cristal del Nexo: un enorme rombo de cristal cian con anillos plateados que da una luz suave. En el borde izquierdo se abre un arco que lleva a una arena con estandartes azules; en el borde derecho, abajo, brillan los faroles ámbar de un mercado. Ambiente de volver a casa: sereno y acogedor dentro de la fantasía oscura, con partículas de energía flotando.

### `jugar` — Jugar online

> la Sala de los Desafíos, un gran salón de piedra donde se buscan rivales. A lo largo de las paredes izquierda y derecha se abren varios arcos-portal, cada uno con un velo de energía cian: unos encendidos y brillantes, otros apagados y en penumbra, como salas abiertas y cerradas. Entre los arcos, braseros ámbar y estandartes azules con el rombo. En el suelo, en la parte baja de la imagen, un gran círculo de runas grabado en la piedra que brilla débilmente. El centro del salón queda en penumbra, con niebla baja y partículas de energía. Ambiente de expectación antes del combate.

### `arena` — Sala de batalla

> la Arena del Nexo, un gran ruedo de combate de piedra oscura dentro de la ciudadela. Alrededor, gradas en penumbra sin figuras visibles, estandartes azules y violetas con el rombo y braseros ámbar en el borde superior. El suelo es de grandes losas de piedra con grietas finas de luz cian, como huellas de combates anteriores, y una neblina baja que se arrastra por el ruedo. Ambiente de tensión justo antes del duelo.

### `torneo` — Torneo

> el Salón de los Campeones, junto al coliseo de la ciudadela, donde se celebran los torneos. En el borde derecho, sobre un pedestal alto, descansa el trofeo del torneo: una copa de plata y oro coronada por el rombo de cristal cian, iluminada desde arriba por un haz suave. En el borde izquierdo, a media altura, un gran muro de piedra con un árbol de torneo tallado en relieve: escudos unidos por líneas de luz cian que convergen hacia arriba, como un cuadro de eliminatorias. De las galerías superiores cuelgan ocho estandartes de equipos en tonos azules, violetas y plata, cada uno con un emblema geométrico distinto. Abajo, la tribuna de piedra y la niebla del coliseo, con braseros ámbar. Ambiente de gloria y rivalidad, solemne.

### `misiones` — Misiones

> la Sala de Misiones, una cámara alta de la ciudadela donde se planean las expediciones. En el borde izquierdo, un gran tablón de piedra con pergaminos enrollados, atados con cintas azules y sellos de cera, colgados de clavos de hierro. En el borde derecho, una enorme brújula rúnica de bronce y cristal que flota y gira despacio, con el rombo cian en el centro, frente a un ventanal gótico que deja ver montañas lejanas bajo la luna. En la parte baja, una mesa de guerra de madera oscura con un mapa en relieve recorrido por líneas de runas cian, velas ámbar y pequeños marcadores de piedra en forma de rombo. Ambiente de aventura y estrategia, sereno.

### `inventario` — Inventario

> la Armería del Nexo, una cámara abovedada donde cada guerrero guarda su equipo. En el borde izquierdo, un armero de madera oscura y hierro con espadas, hachas y lanzas colgadas, con runas cian grabadas en las hojas. En el borde derecho, una armadura completa montada en un soporte de madera, con el yelmo colgado aparte, y un escudo con el rombo, junto a estantes con pociones de vidrio que brillan en azul y violeta. En la parte baja, varios cofres reforzados con herrajes de plata y cerraduras de cristal cian; uno de ellos, entreabierto, deja escapar una luz dorada suave. Ambiente de tesoro y preparación para la batalla, cálido dentro de la penumbra.

### `mercado` — Mercado

> el Gran Bazar del Nexo, un mercado nocturno bajo arcos de piedra dentro de la ciudadela. En el borde izquierdo, puestos de comerciantes con toldos violetas y azules, faroles ámbar colgando y mercancía a la vista: armas, cristales en forma de rombo y frascos brillantes, sin carteles ni letreros. En el borde derecho, el estrado de subastas: una tarima de piedra con un atril tallado, una campana de bronce y una vitrina de cristal con un objeto épico que brilla en cian. En la parte baja, el empedrado mojado refleja los faroles, con cajas, sacos y una balanza de bronce con cristales en sus platillos. El bazar justo antes de abrir, sin gente: cálido y misterioso.

### `cuenta` — Mi cuenta y comunidad

> los aposentos del héroe en lo alto de una torre de la ciudadela, su espacio personal. En el borde izquierdo, un perchero de madera con una capa azul con el rombo bordado y un estandarte personal, junto a un gran ventanal gótico con vistas a las torres bajo la luna. En el borde derecho, un escritorio de madera oscura con cartas lacradas con sello de cera azul y sin escritura visible, plumas, un tintero y una lámpara de aceite ámbar; sobre el escritorio, un cuervo mensajero de cristal cian con las alas plegadas. En la parte baja, una alfombra con el rombo bordado y una chimenea de piedra con brasas que dan una luz cálida. Ambiente íntimo, cálido y tranquilo dentro de la penumbra.

## Añadir o cambiar una escena

1. Generar con los bloques fijos de arriba y la escena nueva, adjuntando el login como referencia.
2. Exportar `<nombre>.webp` (1536 × 1024, calidad 80) y `<nombre>-movil.webp` (960 × 640,
   calidad 72) en esta carpeta.
3. Declararla en el catálogo de `fondos.css` (las dos reglas: escritorio y móvil).
4. En cada vista: enlazar `shared/ui-kit/css/fondos.css` después de `componentes.css` y poner
   `data-fondo="<nombre>"` en el `<body>`. Nunca en una vista con armazón de administración.
5. Correr el laboratorio visual: `contraste-fondos.spec.js` tiene que quedar en verde y deja en
   `docs/evidencia/fondos/<nombre>/` las capturas (`<vista>-<ancho>.jpg`) y un informe por vista
   (`<vista>.md`).
