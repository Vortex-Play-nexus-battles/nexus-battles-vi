# Prompts de generación — Nexus Battles VI, fase 2 de arte

> Generado por `docs/arte/generar-documentos.py` desde `manifest-assets.json`.
> Cada bloque «Prompt principal» está completo: se copia tal cual en el generador.

## Cómo generar (el mismo flujo que los fondos)

1. Una sola conversación del generador por lote, adjuntando siempre como referencia de estilo
   `shared/ui-kit/fondos/entrada.webp` (la del login, igual que en los fondos).
2. **Lote 0 primero**: genera solo HER-01 (Guerrero Tanque), ARM-01 (Espada de una mano) y MIS-01 (El Templo Olvidado), ponlas una al lado de la otra y comprueba que parecen del mismo juego que los fondos. Si una desentona, se ajusta su prompt antes de seguir; las tres aprobadas son la referencia de su categoría para el resto.
3. Desde el lote 1, adjunta además la imagen aprobada de su misma categoría (el héroe aprobado para
   los héroes, el arma aprobada para los objetos, la misión aprobada para las misiones) y pide
   «mismo estilo, encuadre e iluminación que la referencia».
4. Si el generador permite pegar un prompt negativo, pega el bloque «Negative constraints»; si no,
   añádelo al final del prompt con «Evitar: …».
5. Descarga cada imagen en PNG al tamaño indicado y nómbrala con su ID (`HER-01.png`,
   `MIS-03.png`…). Entrégamelas en el chat o en `C:\dev\nexus-battles-vi\.motion-transfer\arte\lote-N\`.
   Yo las valido, recorto, convierto a WebP, las coloco en su ruta final y las integro.

## Bloques fijos

**Estilo (en todos los prompts)**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.
```

**Evitar (en todos los prompts)**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna
```

## Lote 0 — Prueba de dirección artística

Un héroe, un arma y una misión, juntos, antes de producir el resto. Las tres aprobadas son la referencia de su categoría.

### HER-01 · Guerrero Tanque

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-guerrero-tanque` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-01.png` |
| Archivo final | `shared/ui-kit/arte/heroes/guerrero-tanque.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: el Guerrero Tanque, defensor de primera línea del Nexo: armadura pesada de placas de acero oscuro con remaches de plata y un rombo cian grabado en el peto, hombreras anchas, capa corta azul cobalto gastada. Sostiene delante del pecho un gran escudo con relieve de cabeza de dragón en plata y una espada recta de una mano. Rostro curtido y sereno, barba corta, cicatriz en la ceja. Halo azul cobalto detrás. Silueta ancha y sólida, la más pesada de los ocho.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### MIS-01 · El Templo Olvidado

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Documento §7.8.14 · `templo-olvidado` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-01.png` |
| Archivo final | `shared/ui-kit/arte/misiones/templo-olvidado.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: El Templo Olvidado, de noche, en las profundidades del Bosque Sombrío: un templo antiguo de piedra gris cubierto de raíces y musgo, con escalinata y columnas rotas, dedicado a los Dioses Olvidados. En el centro, su gran portal sellado brilla con runas cian tenues entre árboles retorcidos y niebla densa. A los lados de la escalinata, dos estatuas de guardianes de piedra; entre los árboles, siluetas lejanas de sombras corrompidas. Sensación de presencia arcana milenaria y de peligro. Dificultad normal: noche azul profunda.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### ARM-01 · Espada de una mano

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Guerrero Tanque |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-01.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/espada-de-una-mano.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Espada de una mano del Guerrero Tanque: espada recta de una mano, hoja de acero oscuro con filo pulido, guarda corta en forma de rombo con un pequeño cristal cian en el centro, empuñadura de cuero azul cobalto y pomo de plata. Robusta y sobria, de defensor.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

## Lote 1 — Héroes y misiones (P0)

Los siete héroes y las siete misiones que faltan tras la prueba.

### HER-02 · Guerrero Armas

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-guerrero-armas` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-02.png` |
| Archivo final | `shared/ui-kit/arte/heroes/guerrero-armas.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: la Guerrera Armas, maestra del mandoble: armadura media de acero y cuero, brazaletes de bronce con grabados de llama y guanteletes articulados, sin escudo. Apoya sobre el hombro un mandoble de hoja ancha con runas cian a lo largo del vaceo. Cabello oscuro recogido en trenza, mirada decidida y agresiva, una pintura de guerra ámbar en la mejilla. Halo rojo ámbar de brasa detrás. Silueta atlética y diagonal por la espada.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-03 · Mago Fuego

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-mago-fuego` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-03.png` |
| Archivo final | `shared/ui-kit/arte/heroes/mago-fuego.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: el Mago Fuego: túnica arcana índigo gruesa con bordados dorados y runas color ascua, bajos chamuscados. En la mano izquierda, a la altura del pecho, un orbe de cristal ahumado sujeto por garras de bronce con fuego naranja girando dentro; en el dedo, un anillo de oro oscuro con una gema anaranjada. Rostro anguloso de mediana edad, ojos que reflejan las llamas, chispas flotando. Halo naranja fuego detrás.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-04 · Mago Hielo

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-mago-hielo` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-04.png` |
| Archivo final | `shared/ui-kit/arte/heroes/mago-hielo.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: la Maga Hielo: manto de pieles blancas y placas de plata escarchada, una corona de cristales de hielo en forma de rombo sobre el cabello plateado. Sostiene en diagonal un báculo de madera petrificada por la escarcha coronado por un cristal de hielo con anillos de plata; vaho frío alrededor. Expresión serena y distante, piel pálida con reflejos azules. Halo blanco escarcha y cian pálido detrás.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-05 · Pícaro Veneno

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-picaro-veneno` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-05.png` |
| Archivo final | `shared/ui-kit/arte/heroes/picaro-veneno.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: la Pícara Veneno: capucha oscura que deja ver media cara, coraza ligera de cuero con correas cruzadas de cuero carmesí, guantes de cuero negro gastados. Sostiene junto al pecho una daga curva por cuya acanaladura corre un veneno verde-violeta que gotea; un frasco facetado al cinto. Ojos atentos y astutos, media sonrisa. Halo verde tóxico y violeta detrás. Silueta esbelta y en sombra.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-06 · Pícaro Machete

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-picaro-machete` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-06.png` |
| Archivo final | `shared/ui-kit/arte/heroes/picaro-machete.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: el Pícaro Machete: luchador rudo de los caminos, chaleco de cuero endurecido, brazaletes con púas de hierro y piedras rojo oscuro, pañuelo carmesí al cuello. Apoya en el hombro un machete ancho y pesado cuya hoja está envuelta a medias en vendas claras con runas cian pintadas. Rostro moreno con barba de días y una sonrisa desafiante. Halo carmesí oscuro detrás.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-07 · Chamán

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-chaman` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-07.png` |
| Archivo final | `shared/ui-kit/arte/heroes/chaman.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: el Chamán, sanador del bosque: peto de pieles, cuero y musgo con cuentas de hueso, y un yelmo de hueso y madera con astas cortas, plumas y ojos tallados que brillan cian. Sostiene junto al pecho una raíz retorcida usada como talismán, atada con cordones, de la que salen brotes con luz verde-cian. Rostro mayor, sabio y tranquilo, pintura ritual en la frente. Halo verde musgo y cian detrás.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### HER-08 · Médico

| Campo | Valor |
|---|---|
| Categoría | Héroe (retrato) |
| Entidad | Tabla 6 · `heroe-medico` (catalogo-inicial.json) |
| Uso | Héroes del Nexo, detalle del héroe, inventario y equipo, tienda, selector y validación, sala y combate, estrategia de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `HER-08.png` |
| Archivo final | `shared/ui-kit/arte/heroes/medico.webp` (WebP 640 × 640, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Retrato de UN solo personaje, de la cintura hacia arriba, en vista de tres cuartos, mirando hacia la izquierda del espectador. Formato cuadrado 1:1. La cara y los hombros van centrados en horizontal y en la franja central-superior (los ojos a un 38 % de la altura): la imagen se recorta en un círculo pequeño, en un cuadrado y en una franja horizontal ancha, así que no puede haber nada importante en las esquinas ni en el 12 % de cada borde. El arma o foco del héroe se ve junto al pecho, dentro del centro. Fondo: penumbra azul marino con niebla y un halo suave del color de su rol detrás de la cabeza, sin escenario detallado y oscuro hacia los bordes. Proporciones heroicas pero realistas, manos con cinco dedos bien formadas.

Asunto: la Médica de campaña del Nexo: bata larga color hueso con cinturones y bolsillos con viales, rodilleras de cuero, y en bandolera un maletín de cuero con herrajes de plata con un emblema en rombo verde azulado (no una cruz roja). Sostiene junto al pecho un dispositivo de reanimación arcano: dos palas de plata unidas por cables trenzados a un núcleo de cristal cian que chisporrotea. Mirada firme y compasiva, cabello corto recogido. Halo verde azulado detrás.

Mismo encuadre, tamaño de cabeza, fondo e iluminación que el retrato de héroe aprobado que adjunto como referencia (desde el segundo héroe).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un solo personaje; sin miembros extra ni manos deformes; sin rostro de actor real; sin texto de nombre ni estadísticas
```

**Notas de crop:** Círculo de las tarjetas (≈ 72–96 px) centrado en la cara; ficha de detalle: franja 16:7 con `object-position: 50% 30%`; combate y sala: círculo. Se exporta el cuadrado completo.

### MIS-02 · El Sendero de los Aprendices

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión (cambio autorizado n.º 2) · `sendero-de-los-aprendices` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-02.png` |
| Archivo final | `shared/ui-kit/arte/misiones/sendero-de-los-aprendices.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: El Sendero de los Aprendices, al atardecer: un camino de tierra que entra en un bosque claro desde un pequeño campamento de tiendas con estandartes azules del rombo y una fogata. En el centro del camino, una barricada improvisada de troncos y cajas de los saqueadores, con un farol ámbar. Luz cálida y amable, sensación de primera aventura y de peligro menor. Dificultad fácil: el cielo es la escena más luminosa de las ocho.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-03 · La Mina Abandonada

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `mina-abandonada` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-03.png` |
| Archivo final | `shared/ui-kit/arte/misiones/mina-abandonada.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: La Mina Abandonada: la boca de una vieja mina de hierro excavada en la roca, con un armazón de vigas de madera, raíles oxidados y una vagoneta volcada que entran en la oscuridad. En el centro, dentro del túnel, se adivina la silueta enorme de un golem de escombros hecho de rocas y raíles, con grietas de luz naranja. Murciélagos de ceniza revolotean. Polvo en el aire, un farol ámbar caído. Dificultad fácil: crepúsculo templado.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-04 · El Pantano de los Susurros

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `pantano-de-los-susurros` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-04.png` |
| Archivo final | `shared/ui-kit/arte/misiones/pantano-de-los-susurros.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: El Pantano de los Susurros: agua oscura y quieta entre árboles muertos cubiertos de musgo colgante, niebla espesa a ras del agua que forma remolinos como figuras. En el centro, una cabaña de bruja sobre pilotes con una luz azul helada en la ventana, y una pasarela de tablones que lleva hasta ella. Hielo fino en el borde del agua, sapos sobre las raíces. Atmósfera inquietante y silenciosa. Dificultad normal: luna velada.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-05 · La Fortaleza Quebrada

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `fortaleza-quebrada` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-05.png` |
| Archivo final | `shared/ui-kit/arte/misiones/fortaleza-quebrada.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: La Fortaleza Quebrada: las ruinas de una fortaleza que cierra un paso de montaña, con murallas derrumbadas y una torre partida por la mitad. En el centro, sobre la puerta rota, un estandarte del Nexo desgarrado. En las almenas, siluetas fantasmales de soldados caídos en formación, con un brillo azul espectral; en la torre, una luz de hielo. Melancolía y guardia eterna. Dificultad normal: noche con niebla.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-06 · Las Cumbres Heladas

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `cumbres-heladas` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-06.png` |
| Archivo final | `shared/ui-kit/arte/misiones/cumbres-heladas.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: Las Cumbres Heladas: un paso de alta montaña en plena ventisca, nieve que vuela en diagonal, picos afilados. En el centro, un refugio de piedra de pastores con la chimenea apagada y la puerta cubierta de escarcha. En la ladera, siluetas de lobos de escarcha con ojos cian; arriba, una forma de cristal de hielo enorme apenas visible en la tormenta. Frío extremo y desolación. Dificultad normal: blanco azulado frío, la escena más clara en tonos.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-07 · El Volcán Dormido

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `volcan-dormido` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-07.png` |
| Archivo final | `shared/ui-kit/arte/misiones/volcan-dormido.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: El Volcán Dormido: el cráter de un volcán que despierta, con ríos de lava naranja que bajan por grietas entre rocas de obsidiana negra. En el centro, en el fondo del cráter, un enorme corazón de magma que late con un brillo naranja y rojo. Junto a la lava, siluetas de salamandras de fuego; en el borde del cráter, guardianes de obsidiana. Humo, ceniza y calor. Dificultad difícil: la única escena dominada por naranja, contrastado con el índigo del cielo.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

### MIS-08 · La Ciudadela de las Sombras

| Campo | Valor |
|---|---|
| Categoría | Misión (escena) |
| Entidad | Progresión · `ciudadela-de-las-sombras` |
| Uso | tablón (tarjeta), banner «Nueva», detalle de la misión, en curso, reporte |
| Aspect ratio | 16:9 — generar 1536 × 1024 PNG (se recorta a 16:9) |
| Archivo que me entregas | `MIS-08.png` |
| Archivo final | `shared/ui-kit/arte/misiones/ciudadela-de-las-sombras.webp` (WebP 960 × 540, calidad 76) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Escena ambiental de paisaje y lugar, sin personajes protagonistas, en formato horizontal 16:9 (si el generador solo da 3:2, deja cielo y suelo de sobra para recortar arriba y abajo). Un único punto focal en el centro exacto, reconocible aunque la imagen se vea a 300 px de ancho y aunque se recorte en un círculo centrado. Las dos esquinas superiores tranquilas y oscuras, porque ahí van etiquetas; el cuarto inferior en penumbra, porque ahí empieza el texto. Profundidad atmosférica, niebla y una sola fuente de luz dominante. Las criaturas, si aparecen, son siluetas lejanas sin rostro.

Asunto: La Ciudadela de las Sombras: una ciudadela negra y sin puertas, de torres afiladas, en el linde del Bosque Sombrío. En el centro, su muralla lisa e impenetrable con un único arco cegado del que sale humo violeta. En los adarves, siluetas de caballeros negros con armadura pesada; en las sombras de los arcos, figuras veladas de asesinos. Amenaza, opresión y la sensación de última frontera antes del templo. Dificultad difícil: noche violeta muy oscura.

Mismo estilo de pincel, paleta e iluminación que la escena de misión aprobada que adjunto como referencia (desde la segunda misión).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes en primer plano; sin rostros; sin sangre ni gore
```

**Notas de crop:** Tarjeta del tablón: 16:9 completo; banner «Nueva» y reporte: círculo centrado; detalle: franja ancha con `object-position: 50% 45%`.

## Lote 2 — Armas

Las quince armas que faltan tras la prueba (Tablas 8-19 del documento).

### ARM-02 · Espada de dos manos

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Guerrero Armas |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-02.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/espada-de-dos-manos.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Espada de dos manos del Guerrero Armas: mandoble largo de hoja ancha con runas cian grabadas a lo largo del vaceo, guarda ancha de plata envejecida con puntas, empuñadura larga envuelta en cuero rojo oscuro. Colocado en diagonal. Aspecto agresivo y pesado.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-03 · Escudo de dragón

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Guerrero Tanque |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-03.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/escudo-de-dragon.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Escudo de dragón del Guerrero Tanque (en el documento es un arma): escudo de acero oscuro con forma de lágrima, con una cabeza de dragón en relieve de plata en el centro, ojos de cristal cian y escamas grabadas a lo largo del borde. Visto de frente en tres cuartos.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-04 · Piedra de afilar

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Guerrero Armas |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-04.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/piedra-de-afilar.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Piedra de afilar del Guerrero Armas: una piedra de afilar alargada de grano gris oscuro con vetas de cristal cian, sujeta a una correa de cuero con hebilla de plata para llevarla al cinto. Unas pocas chispas cian saltan de su superficie.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-05 · Orbe de manos ardientes

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Mago Fuego |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-05.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/orbe-de-manos-ardientes.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Orbe de manos ardientes del Mago Fuego: un orbe de cristal ahumado del tamaño de un puño, sujeto por garras de bronce oscuro, con fuego naranja y ámbar girando dentro y runas que brillan como brasas en su montura.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-06 · Báculo de Permafrost

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Mago Hielo |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-06.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/baculo-de-permafrost.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Báculo de Permafrost del Mago Hielo: báculo largo de madera oscura petrificada y cubierta de escarcha, coronado por un gran cristal de hielo en forma de rombo rodeado de anillos de plata, con vaho frío que cae. Colocado en diagonal de esquina a esquina sin tocar los bordes.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-07 · Fuego fatuo

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Mago Fuego |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-07.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/fuego-fatuo.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** es un arma del documento sin forma descrita; se representa como un foco mágico (llama sobre disco rúnico), sin atribuirle ninguna función.

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Fuego fatuo del Mago Fuego: una llama fatua etérea, de núcleo blanco y bordes violeta y ámbar, que flota sobre un pequeño disco de piedra oscura con runas talladas que brillan como brasas.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-08 · Venas heladas

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Mago Hielo |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-08.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/venas-heladas.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** arma sin forma descrita; se representa como relicario de cristal con venas de hielo.

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: las Venas heladas del Mago Hielo: un relicario alargado de cristal transparente con tapa y base de plata, atravesado por venas de hielo azul brillante que se ramifican por dentro como raíces, con escarcha en la superficie.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-09 · Daga purulenta

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Pícaro Veneno |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-09.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/daga-purulenta.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Daga purulenta del Pícaro Veneno: daga curva de hoja estrecha con una acanaladura por la que corre un veneno verde-violeta espeso que gotea de la punta, empuñadura envuelta en cuero negro y pomo con un pequeño cristal violeta.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-10 · Machete vendito

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Pícaro Machete |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-10.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/machete-vendito.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** el nombre del documento se conserva tal cual («vendito»); la imagen juega con «vendas» y «bendito».

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Machete vendito del Pícaro Machete: machete ancho y pesado de hoja mellada, con la parte central de la hoja envuelta en vendas claras atadas con runas cian pintadas como una bendición, mango de madera oscura con remaches.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-11 · Visión borrosa

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Pícaro Veneno |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-11.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/vision-borrosa.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** arma sin forma descrita; se representa como amuleto-ojo empañado.

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Visión borrosa del Pícaro Veneno: un amuleto con forma de ojo de cristal empañado, dentro del cual se arremolina un humo violeta, colgado de una cadena negra con una montura de plata oscura.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-12 · Cierra sangrienta

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Pícaro Machete |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-12.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/cierra-sangrienta.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** el nombre del documento se conserva tal cual («Cierra»); la imagen es una sierra.

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Cierra sangrienta del Pícaro Machete: una hoja corta de sierra con dientes grandes y curvos, montada en un mango de hierro envuelto en cuero, con un brillo carmesí oscuro en los dientes y el filo, sin sangre goteando.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-13 · Raíz china

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Chamán |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-13.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/raiz-china.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Raíz china del Chamán: una raíz retorcida y nudosa usada como talismán, atada con cordones de colores terrosos y cuentas de hueso, de la que salen brotes pequeños con una luz verde-cian suave.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-14 · Kit de urgencias

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Médico |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-14.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/kit-de-urgencias.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Kit de urgencias del Médico: un maletín de campaña de cuero marrón oscuro con herrajes de plata, entreabierto, del que asoman vendas, frascos de vidrio con líquidos verde azulado y una pinza; en la tapa, un emblema de rombo verde azulado (no una cruz roja).

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-15 · Yerbabuena

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Chamán |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-15.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/yerbabuena.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: la Yerbabuena del Chamán: un manojo de ramas frescas de hierbabuena atado con un cordel, con gotas de rocío que brillan como pequeñas chispas cian y un vapor verde muy suave.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

### ARM-16 · Reanimador

| Campo | Valor |
|---|---|
| Categoría | Arma (objeto) |
| Entidad | Tablas 8-19 · arma de Médico |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARM-16.png` |
| Archivo final | `shared/ui-kit/arte/productos/armas/reanimador.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, colocado en diagonal si es alargado, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831, sin degradados ni escenario). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué es aunque se vea a 96 px.

Asunto: el Reanimador del Médico: un dispositivo arcano-tecnológico de reanimación, dos palas de plata y latón unidas por cables trenzados a un núcleo de cristal cian del tamaño de un puño del que saltan chispas de energía.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia (desde el segundo objeto).
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto (o un único par si se indica); sin manos ni personajes; sin pedestal salvo que se indique; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; la tienda y la subasta lo encajan con `object-fit: contain` sobre la ranura oscura.

## Lote 3 — Armaduras

Las dieciséis armaduras, una por parte del cuerpo indicada en el documento.

### ARD-01 · Defensa del enfurecido

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Guerrero Tanque |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-01.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/defensa-del-enfurecido.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Defensa del enfurecido» del Guerrero Tanque, pieza de pecho: peto pesado de placas de acero oscuro con un rombo en relieve en el centro, remaches de plata y rasguños de batalla, hombreras incluidas. Color de acento: azul cobalto y acero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-02 · Puño lúcido

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Guantes de Guerrero Armas |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-02.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/puno-lucido.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Puño lúcido» del Guerrero Armas, pieza de guantes: un par de guanteletes articulados de acero oscuro con nudillos reforzados y una runa cian que brilla en el dorso de cada uno. Color de acento: rojo ámbar de brasa y acero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-03 · Magma Ardiente

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Casco de Guerrero Tanque |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-03.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/magma-ardiente.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Magma Ardiente» del Guerrero Tanque, pieza de casco: yelmo cerrado de acero ennegrecido con visera estrecha y grietas por las que brilla magma naranja. Color de acento: azul cobalto y acero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-04 · Puños en llamas

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Brazaletes de Guerrero Armas |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-04.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/punos-en-llamas.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Puños en llamas» del Guerrero Armas, pieza de brazaletes: un par de brazaletes (avambrazos) de bronce oscuro con grabados en forma de llama y brasas ámbar en las juntas. Color de acento: rojo ámbar de brasa y acero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-05 · Túnica arcana

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Mago Fuego |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-05.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/tunica-arcana.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Túnica arcana» del Mago Fuego, pieza de pecho: túnica de mago de tela índigo gruesa con bordados dorados y runas color ascua en el pecho y los puños, presentada sobre un maniquí invisible. Color de acento: naranja fuego y ámbar.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-06 · Corona de hielo

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Casco de Mago Hielo |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-06.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/corona-de-hielo.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Corona de hielo» del Mago Hielo, pieza de casco: corona de cristales de hielo en forma de rombos ascendentes sobre una banda de plata, con escarcha y un brillo cian pálido. Color de acento: blanco escarcha y cian pálido.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-07 · Caída de fuego

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pantalón de Mago Fuego |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-07.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/caida-de-fuego.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Caída de fuego» del Mago Fuego, pieza de pantalón: pantalones de mago de tela gruesa índigo y rojo oscuro, con los bajos chamuscados y ribetes que brillan como brasas, presentados sobre un maniquí invisible. Color de acento: naranja fuego y ámbar.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-08 · Ventisca

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Mago Hielo |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-08.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/ventisca.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Ventisca» del Mago Hielo, pieza de pecho: manto-peto de pieles blancas y placas de plata escarchada, con un remolino de viento helado y copos a su alrededor, sobre un maniquí invisible. Color de acento: blanco escarcha y cian pálido.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-09 · Mano del desterrado

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Guantes de Pícaro Veneno |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-09.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/mano-del-desterrado.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Mano del desterrado» del Pícaro Veneno, pieza de guantes: un par de guantes de cuero negro gastados, con correas y hebillas pequeñas y una marca violeta tenue en la palma. Color de acento: verde tóxico y violeta.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-10 · Pie de atleta

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Zapatos de Pícaro Machete |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-10.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/pie-de-atleta.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Pie de atleta» del Pícaro Machete, pieza de zapatos: un par de botas ligeras de cuero con suela flexible, vendas atadas en el tobillo y costuras carmesí. Color de acento: carmesí oscuro y cuero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-11 · Atadura carmesí

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Pícaro Veneno |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-11.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/atadura-carmesi.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Atadura carmesí» del Pícaro Veneno, pieza de pecho: coraza ligera de cuero negro con correas cruzadas de cuero carmesí y hebillas de plata, sobre un maniquí invisible. Color de acento: verde tóxico y violeta.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-12 · Sangre cruel

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Brazaletes de Pícaro Machete |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-12.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/sangre-cruel.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Sangre cruel» del Pícaro Machete, pieza de brazaletes: un par de brazaletes de cuero endurecido con púas de hierro y piedras rojo oscuro engastadas. Color de acento: carmesí oscuro y cuero.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-13 · Piel de Caminante del Bosque

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Chamán |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-13.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/piel-de-caminante-del-bosque.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Piel de Caminante del Bosque» del Chamán, pieza de pecho: peto de pieles y cuero con musgo, hojas secas y cuentas de hueso, con una capa corta de piel, sobre un maniquí invisible. Color de acento: verde musgo, hueso y cian.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-14 · Bata de Cirujano

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pecho de Médico |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-14.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/bata-de-cirujano.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Bata de Cirujano» del Médico, pieza de pecho: bata larga de campaña color hueso con cinturones de cuero, bolsillos con viales y un emblema de rombo verde azulado (no una cruz roja), sobre un maniquí invisible. Color de acento: verde azulado y blanco hueso.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-15 · Casco de Ecos Ancestrales

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Casco de Chamán |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-15.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/casco-de-ecos-ancestrales.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Casco de Ecos Ancestrales» del Chamán, pieza de casco: yelmo de hueso y madera tallada con astas cortas, plumas y conchas, y dos ojos tallados que brillan cian. Color de acento: verde musgo, hueso y cian.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

### ARD-16 · Pantalón de Expedición Médica

| Campo | Valor |
|---|---|
| Categoría | Armadura (objeto) |
| Entidad | Tablas 8-19 · Pantalón de Médico |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ARD-16.png` |
| Archivo final | `shared/ui-kit/arte/productos/armaduras/pantalon-de-expedicion-medica.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UNA única pieza de armadura de RPG (o un único par si es de manos o pies), aislada y centrada, sin persona dentro: si es una prenda, como si la llevara un maniquí invisible. Vista de tres cuartos ligeramente desde arriba, ocupando el 72 % del lienzo con margen uniforme. Formato cuadrado 1:1 con fondo transparente (si el generador no permite transparencia: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo ni sombra proyectada. Tiene que reconocerse qué parte del cuerpo cubre aunque se vea a 96 px.

Asunto: la armadura «Pantalón de Expedición Médica» del Médico, pieza de pantalón: pantalón de expedición resistente color arena oscuro, con rodilleras de cuero y bolsillos con vendas y viales, sobre un maniquí invisible. Color de acento: verde azulado y blanco hueso.

Mismo estilo, ángulo, tamaño en el lienzo e iluminación que el objeto aprobado que adjunto como referencia.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin persona ni cabeza dentro de la pieza; sin manos; un único objeto o par; sin fondo de escenario
```

**Notas de crop:** Centrado con margen; mismo encaje que las armas.

## Lote 4 — Ítems y épicas

Ocho ítems y nueve épicas (ocho de la Tabla 20 y la de la misión del Templo).

### ITM-01 · Pinchos de escudo

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Guerrero Tanque |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-01.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/pinchos-de-escudo.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Pinchos de escudo» del Guerrero Tanque: una placa circular de refuerzo de acero oscuro con pinchos cortos y afilados, pensada para acoplarse a un escudo, con tornillería de plata. Color de acento: azul cobalto y acero.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-02 · Empuñadura de Furia

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Guerrero Armas |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-02.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/empunadura-de-furia.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Empuñadura de Furia» del Guerrero Armas: una empuñadura de espada suelta, sin hoja, envuelta en cuero rojo oscuro, con un cristal ámbar que palpita en el pomo. Color de acento: rojo ámbar de brasa y acero.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-03 · Anillo para Piro-explosión

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Mago Fuego |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-03.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/anillo-para-piro-explosion.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Anillo para Piro-explosión» del Mago Fuego: un anillo de oro oscuro con una gema naranja facetada con fuego en su interior y pequeñas chispas a su alrededor. Color de acento: naranja fuego y ámbar.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-04 · Libro de la ventisca helada

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Mago Hielo |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-04.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/libro-de-la-ventisca-helada.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Libro de la ventisca helada» del Mago Hielo: un grimorio cerrado con tapas de cuero azul cubiertas de escarcha, cierre de plata y un cristal de hielo en el centro de la tapa. Color de acento: blanco escarcha y cian pálido.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-05 · Veneno lacerante

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Pícaro Veneno |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-05.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/veneno-lacerante.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Veneno lacerante» del Pícaro Veneno: un frasco de vidrio facetado con un veneno verde-violeta espeso y burbujeante, tapón de cera negra y una etiqueta de cuero sin escritura. Color de acento: verde tóxico y violeta.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-06 · Mancuerna yugular

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Pícaro Machete |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-06.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/mancuerna-yugular.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** «mancuerna» se representa como pesa de mano dentada; el efecto del documento no se dibuja.

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Mancuerna yugular» del Pícaro Machete: una pesa de mano (mancuerna) de hierro forjado con dientes de sierra en los discos y un grabado carmesí oscuro en la barra. Color de acento: carmesí oscuro y cuero.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-07 · Pluma sanadora

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Chamán |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-07.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/pluma-sanadora.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Pluma sanadora» del Chamán: una pluma grande e iridiscente de raquis dorado que desprende motas de luz verde-cian. Color de acento: verde musgo, hueso y cian.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### ITM-08 · Benditas

| Campo | Valor |
|---|---|
| Categoría | Ítem (objeto) |
| Entidad | Tablas 8-19 · ítem de Médico |
| Uso | tienda (tarjeta y ficha), inventario, equipamiento, subasta, detalle |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `ITM-08.png` |
| Archivo final | `shared/ui-kit/arte/productos/items/benditas.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Interpretación visual (aprobar con el PO):** «benditas» se lee como tiritas o vendas adhesivas (sana heridas durante tres turnos).

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto pequeño de RPG (un accesorio o un consumible, que no debe parecer un arma ni una armadura), aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 60 % del lienzo con margen amplio y un brillo mágico suave a su alrededor. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian; sin suelo. Reconocible a 96 px.

Asunto: el ítem «Benditas» del Médico: un pequeño estuche de tela abierto con tiras de vendaje adhesivo cortas, cada una con una runa cian dibujada. Color de acento: verde azulado y blanco hueso.

Mismo estilo e iluminación que el objeto aprobado que adjunto como referencia, pero un poco más pequeño en el lienzo.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin manos ni personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen amplio.

### EPI-01 · Golpe de defensa

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Guerrero Tanque |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-01.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/golpe-de-defensa.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Golpe de defensa» del Guerrero Tanque: un escudo de acero en el centro del que estalla una onda de choque circular cian que rompe el aire en fragmentos de energía. Color de acento: azul cobalto y acero.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-02 · Segundo impulso

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Guerrero Armas |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-02.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/segundo-impulso.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Segundo impulso» del Guerrero Armas: un mandoble clavado en el suelo rodeado por un torbellino ascendente de energía ámbar y cian, como quien vuelve a levantarse. Color de acento: rojo ámbar de brasa y acero.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-03 · Luz cegadora

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Mago Fuego |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-03.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/luz-cegadora.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Luz cegadora» del Mago Fuego: un orbe del que nace un estallido de luz blanca y ámbar con rayos radiales que lo llenan todo. Color de acento: naranja fuego y ámbar.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-04 · Frio concentrado

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Mago Hielo |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-04.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/frio-concentrado.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Frio concentrado» del Mago Hielo: un cristal de hielo perfecto suspendido dentro de una esfera protectora de escarcha y viento helado. Color de acento: blanco escarcha y cian pálido.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-05 · Toma y lleva

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Pícaro Veneno |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-05.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/toma-y-lleva.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Toma y lleva» del Pícaro Veneno: dos dagas cruzadas que reflejan un golpe, con una espiral de energía violeta y verde que se devuelve hacia afuera. Color de acento: verde tóxico y violeta.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-06 · Intimidación sangrienta

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Pícaro Machete |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-06.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/intimidacion-sangrienta.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Intimidación sangrienta» del Pícaro Machete: una máscara de guerra de hierro con ojos que brillan carmesí detrás de un machete envuelto en humo rojo oscuro. Color de acento: carmesí oscuro y cuero.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-07 · Té changua

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Chamán |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-07.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/te-changua.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Té changua» del Chamán: un cuenco de cerámica rústica humeante con una infusión de hierbas de la que sube un vapor verde-cian sanador en espirales. Color de acento: verde musgo, hueso y cian.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-08 · Reanimador 3000

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Tabla 20 · épica de Médico |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-08.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/reanimador-3000.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Reanimador 3000» del Médico: una versión superior del reanimador: un gran núcleo de cristal cian con anillos de plata que giran, palas de plata y rayos de energía cian y violeta, como un latido. Color de acento: verde azulado y blanco hueso.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

### EPI-09 · Velo de Sombras

| Campo | Valor |
|---|---|
| Categoría | Épica (poder) |
| Entidad | Documento §7.8.14 (Master «Sombra del Olvido») · épica de Pícaro Veneno |
| Uso | inventario, detalle de la épica, recompensa de misión (Master), combate (activación) |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EPI-09.png` |
| Archivo final | `shared/ui-kit/arte/productos/epicas/velo-de-sombras.webp` (WebP 512 × 512, calidad 82) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración de un poder épico, no de un objeto corriente: una composición circular y centrada que se pueda recortar en círculo o en rombo, sobre un fondo propio índigo muy oscuro con un anillo tenue de runas cian que no formen letras. Tiene que sentirse excepcional a primera vista: más contraste, partículas de energía, un halo violeta y cian alrededor del motivo central y más detalle que un objeto normal, sin marco dorado. Formato cuadrado 1:1. Sin personajes: el poder se cuenta con objetos y energía.

Asunto: la épica «Velo de Sombras» del Pícaro Veneno: un velo de tela oscura semitransparente que flota en el aire, con humo violeta y gotas de veneno verde escapando de sus bordes; sensación de intangibilidad. Color de acento: verde tóxico y violeta.

Mismo tratamiento de anillo rúnico, halo y partículas que la épica aprobada que adjunto como referencia (desde la segunda épica), cambiando el color de acento del héroe.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes ni manos; sin marco dorado; sin letras en el anillo de runas
```

**Notas de crop:** Círculo centrado (inventario, recompensa de misión) y cuadrado (ficha).

## Lote 5 — Otros (opcional, decisión del PO)

No bloquean el cierre: el Cofre de Bronce (recompensa del Templo, sin producto en el catálogo) y los diez emblemas de equipo de torneo, que hoy son ilustraciones caricaturescas de otro estilo.

### OTR-01 · Cofre de Bronce

| Campo | Valor |
|---|---|
| Categoría | Otro (objeto) |
| Entidad | Documento §7.8.14 (recompensa garantizada; sin producto en el catálogo) |
| Uso | Mis cofres, recompensa de misión |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG con fondo transparente |
| Archivo que me entregas | `OTR-01.png` |
| Archivo final | `shared/ui-kit/arte/otros/cofre-de-bronce.webp` (WebP 512 × 512 con alfa, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Ilustración pintada de UN único objeto de RPG, aislado y centrado, en vista de tres cuartos ligeramente desde arriba, ocupando el 70 % del lienzo. Formato cuadrado 1:1 con fondo transparente (si no se puede: fondo liso azul marino muy oscuro, #121831). Luz fría desde arriba a la izquierda y contraluz cian. Reconocible a 96 px.

Asunto: un cofre de madera oscura reforzado con herrajes de bronce, cerrado, con una cerradura de cristal cian y un leve resplandor dorado por las juntas.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; un único objeto; sin personajes; sin fondo de escenario
```

**Notas de crop:** Centrado con margen.

### EMB-01 · Emblema «Guerrero»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-01` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-01.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-01.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Guerrero»: como símbolo central, una espada vertical, en acero y rojo ámbar con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-02 · Emblema «Escudo»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-02` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-02.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-02.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Escudo»: como símbolo central, un escudo pequeño con un rombo, en azul cobalto con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-03 · Emblema «Fuego»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-03` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-03.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-03.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Fuego»: como símbolo central, una llama, en naranja fuego con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-04 · Emblema «Hielo»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-04` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-04.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-04.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Hielo»: como símbolo central, un copo de nieve geométrico, en cian pálido con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-05 · Emblema «Sabio»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-05` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-05.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-05.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Sabio»: como símbolo central, un ojo dentro de un rombo, en violeta con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-06 · Emblema «Sombra»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-06` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-06.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-06.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Sombra»: como símbolo central, una luna creciente atravesada por una daga, en violeta oscuro con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-07 · Emblema «Arco»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-07` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-07.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-07.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Arco»: como símbolo central, un arco tensado con una flecha, en verde bosque con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-08 · Emblema «Sanación»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-08` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-08.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-08.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Sanación»: como símbolo central, una hoja con una gota de luz, en verde azulado con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-09 · Emblema «Alquimia»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-09` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-09.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-09.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Alquimia»: como símbolo central, un matraz con burbujas, en verde tóxico con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).

### EMB-10 · Emblema «Comandante»

| Campo | Valor |
|---|---|
| Categoría | Emblema de equipo |
| Entidad | `plataforma/torneos/equipo-formulario.js` · `emblema-10` (hoy `cuentas/avatares/*.jpg`) |
| Uso | crear equipo (torneos), cuadro del torneo |
| Aspect ratio | 1:1 — generar 1024 × 1024 PNG |
| Archivo que me entregas | `EMB-10.png` |
| Archivo final | `shared/ui-kit/arte/emblemas/emblema-10.webp` (WebP 256 × 256, calidad 80) |

**Prompt principal**

```text
Ilustración digital de fantasía oscura, estilo concept art de videojuego RPG premium, con pincelada detallada y acabado cinematográfico, del mismo mundo, estilo, paleta e iluminación que la imagen de referencia adjunta: la Ciudadela del Nexo, una fantasía medieval atravesada por la energía arcana del Nexo. Paleta: azul marino e índigo profundos, violeta, y energía cian eléctrica en runas y en cristales en forma de rombo, con acentos cálidos ámbar puntuales. Materiales creíbles y con desgaste: acero oscuro, plata envejecida, cuero, tela gruesa, madera oscura, cristal. Iluminación: luz principal fría y azulada desde arriba a la izquierda, contraluz cian suave que recorta la silueta, sombras profundas y alto contraste.

Emblema heráldico para el estandarte de un equipo de torneo: un escudo de armas de forma de rombo alargado, de acero oscuro con borde de plata, con un único símbolo central grande y simple, sobre fondo azul marino muy oscuro. Formato cuadrado 1:1, centrado, legible a 48 px: formas grandes, pocos detalles, contraste alto. Todos los emblemas comparten la misma forma de escudo y solo cambian el símbolo y su color de acento.

Asunto: el emblema «Comandante»: como símbolo central, un yelmo con alas, en plata y oro viejo con brillo cian tenue.
```

**Negative constraints**

```text
sin texto, letras, números ni runas que formen palabras; sin logotipos, marcas de agua, firmas, marcos, bordes, viñetas decorativas ni elementos de interfaz; sin estilo caricaturesco, chibi, anime, pixel art, render plástico 3D ni fotografía realista; sin armas de fuego ni tecnología moderna; sin personajes; sin letras ni iniciales
```

**Notas de crop:** Círculo y cuadrado pequeños (selector de equipo, cuadro del torneo).
