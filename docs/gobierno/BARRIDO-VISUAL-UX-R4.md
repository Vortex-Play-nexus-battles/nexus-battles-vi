# Barrido visual y funcional — cierre de UX-R4

Producto: **The Nexus Battles VI**, interfaz web.
Fecha del barrido: **23 de septiembre de 2026**.
Rama de referencia: `develop` tras fusionar UX-R4.1 … UX-R4.7 (#638, #639, #640,
#642, #644, #645, #646).

---

## 1. Qué se miró, y con qué

Conviene decir primero el tamaño de la muestra, porque «se revisó todo» no
significa lo mismo según quién lo diga.

| Comprobación | Cobertura | Quién la hace |
|---|---|---|
| Medidas de maquetación | **33 vistas × 5 anchuras = 165** combinaciones | `auditoria-visual.spec.js` |
| axe-core | **33 vistas × 2 anchuras = 66** | `accesibilidad.spec.js` |
| Matriz de permisos | **11 vistas de consola** + visitante + jugador, por URL directa | `permisos.spec.js` |
| Unidad y contrato de vista | **1.235** pruebas, 85 suites | Jest |
| **Revisión con la vista, a escala** | **33 vistas en escritorio + 33 en móvil** | hoja de contacto |
| **Revisión con la vista, a tamaño real** | **16 vistas** | una por una |

Las dos últimas filas son las que no puede hacer una máquina, y son las que
encontraron casi todo lo que se arregló en este bloque. Las tres primeras
llevaban en verde todo el tiempo.

Las cinco anchuras son 1440, 1280, 1024, 768 y 375. Las capturas se toman en
modo `sin-servicios`: **el estado degradado**, que es el que nadie mira y el
que más se rompe. Lo que ese modo NO pinta se dice en §5.

---

## 2. Resultado

| Clasificación | Vistas |
|---|---|
| PORTAL / JUEGO / CONSOLA **LISTO** | **33** |
| DEUDA MENOR | 4 (anotadas en §4; ninguna impide usar la vista) |
| **REQUIERE CORRECCIÓN** | **0** |

Las cuatro con deuda menor están además en la columna «listo»: la deuda es de
estructura interna o de decisión de producto pendiente, no de lo que se ve.

---

## 3. El catálogo, por armazón

### 3.1 Portal — 4 vistas, sin sesión

`login` · `registro` · `restablecer-solicitar` · `restablecer-confirmar`

Las cuatro comparten el panel oscuro de marca a la izquierda y el formulario a
la derecha, y las cuatro se apilan en móvil sin desbordar. **PORTAL LISTO.**

### 3.2 Aplicación del jugador — 18 vistas

Públicas para un visitante (RF-INV-008, flujo alternativo): `subastas`,
`pujas`, `torneos`. Con sesión: `home`, `perfil`,
`historial-transacciones`, `mis-cofres`, `inventario`, `tienda`,
`publicar-subasta`, `batallas`, `crear-sala`, `sala-batalla`,
`validacion-heroe`, `chat`, `publicar-comentario`, `notificaciones`,
`mis-sanciones`.

**JUEGO LISTO**, con las deudas de §4.2 y §4.3.

### 3.3 Consola — 11 vistas

`productos` y `gestion-usuarios`, `parametros-admin`, `panel-metricas`,
`tablero-tecnico` (administración) · `consola`, `sanciones-admin`,
`lista-negra-admin`, `moderar-comentarios` (moderación) ·
`crear-cuenta-admin`, `auditoria` (superadministración).

Las once niegan el acceso a un jugador **con palabras y sin enseñar un código
HTTP**, también entrando por URL directa. **CONSOLA LISTA.**

---

## 4. Deuda menor, anotada y no tapada

### 4.1 `validacion-heroe` no tiene puerta de entrada

Ningún enlace ni redirección del producto la nombra: solo se abre escribiendo
la URL. Es coherente con que su servicio no exista todavía —
`verificacion-heroe` está publicado en el contrato pero no implementado — y
meterla en el camino de entrada a una sala **es una decisión de producto, no de
maquetación**. Se deja anotada en vez de decidirla aquí.

La vista en sí está bien: es un `role="dialog"` con `aria-modal`, su nombre
accesible sale de `aria-labelledby`, y desde UX-R4.5 sus tres acciones llevan a
donde dicen.

### 4.2 `pujas.js` sigue siendo una plantilla de 2.400 líneas

Quince estilos en línea de maquetación (ya no de color: eso se cerró en
UX-R4.2) y el marcado declarado en cadenas de JavaScript. Está anotado en
`marcado-sin-plantillas.test.js` como `grupo-4`, con las cuatro excepciones de
`innerHTML` revisadas una por una en `sin-innerhtml.test.js`.

### 4.3 `tienda` nombra sus clases en inglés

`main-container`, `store-section`, `products-grid`, `warning-box`. El resto del
producto las nombra en castellano. Renombrarlas toca HTML, CSS y JS de la vista
a la vez y no cambia nada de lo que se ve, así que no entra en un bloque de
cierre.

### 4.4 El laboratorio no pinta componentes con datos

Es la deuda más importante de las cuatro, porque es la que esconde defectos.
Las 165 capturas y las 66 pasadas de axe se hacen en modo `sin-servicios`: sin
backend no hay subastas, ni cofres, ni filas de historial, así que **axe nunca
ha mirado una tarjeta de subasta, una ficha de rareza ni un botón urgente**.

Así fue como el contraste de 1,30:1 de UX-R4.7 sobrevivió con las dos
compuertas en verde. Lo encontró una persona mirando una captura y preguntándose
por qué el botón se veía lavado.

La respuesta de fondo es pasar axe también sobre los componentes montados con
los bancos de prueba que ya existen (`SUBASTAS_INICIALES` y compañía). Merece
su propio bloque.

---

## 5. Lo que este barrido NO demuestra

Decirlo importa tanto como decir lo que sí.

- **No se probó con backend real.** Todo es estado degradado más bancos de
  prueba. Que una vista pinte bien su error no dice cómo pinta sus datos.
- **No se miraron las 165 capturas a tamaño real**, sino 33 + 33 a escala en
  hoja de contacto y 16 a tamaño completo. Un defecto de pocos píxeles en una
  de las 133 restantes no lo habría visto nadie.
- **axe no cubre todo.** Detecta una parte de WCAG, y solo sobre lo que hay en
  pantalla (§4.4).
- **No se probó con lector de pantalla real**, ni con teclado de punta a punta
  en las 33 vistas.
- El navegador del laboratorio corre en `en-US`, así que los campos de fecha
  salen `mm/dd/yyyy` en las capturas. Es del navegador, no del producto.

---

## 6. Lo que se arregló en UX-R4, y cómo apareció

| Defecto | Lo encontró |
|---|---|
| 94 literales de color en 9 hojas | contar, no mirar |
| Quinta paleta (`PALETA_RAREZA`) y sexta (`PALETA_TRAMOS`), en JavaScript | el trinquete nuevo, al enseñarle a mirar los módulos |
| Once emoji haciendo de icono | una búsqueda por rangos Unicode |
| El bloque «¡ES TUYA!» con once `style` en línea | el mismo trinquete |
| Botón de buscar como un panel de media pantalla | **mirar la captura** |
| La cola de moderación sin forma de reintentar | comparar con las otras 16 vistas |
| «decision pendiente del PO, D-25» en pantalla | añadir una palabra al diccionario |
| Estado de error colocado como una ficha más de la rejilla | **mirar la captura** |
| Tarjeta dentro de tarjeta al fallar | **mirar la captura** |
| Dos botones grises alrededor de un hueco en blanco | **mirar la captura, en móvil** |
| Siete botones gritando en mayúsculas | contar mal primero, y luego bien |
| El botón principal que solo escribía en la consola | leer el código que lo llamaba |
| **Contraste 1,30:1 en la llamada a la acción más urgente** | **mirar la captura** |
| 1.400 px de filtros antes de la primera subasta, en móvil | **mirar la captura** |
| Píldora de estado de 630 px | **mirar la captura** |

Nueve de quince salieron de mirar una imagen. Ninguna de esas nueve la
habría encontrado una compuerta, porque todas las compuertas estaban verdes.

---

## 7. Criterio de cierre

- [x] Las 33 vistas clasificadas.
- [x] **Cero REQUIERE CORRECCIÓN.**
- [x] 165 combinaciones sin hallazgos de maquetación.
- [x] axe sin hallazgos de ninguna severidad, tampoco `moderate` ni `minor`.
- [x] Matriz de permisos verde, incluida la entrada por URL directa.
- [x] Identidades de prueba reproducibles, sin secretos en el repositorio.
- [x] Jest, ESLint y Prettier verdes.
- [x] Deuda anotada con nombre y sitio, no escondida.
