# Barrido visual de las 32 vistas — cierre de UX-R3

Cada vista se clasifica en uno de cuatro veredictos:

| Veredicto | Qué significa |
|---|---|
| **COHERENTE CON JUEGO** | Se lee como una pantalla de Nexus Battles VI: jerarquía propia, acción primaria clara, el sistema de diseño usado con intención. |
| **COHERENTE CON ADMIN** | Se lee como la consola del Nexo: densa, tabular, filtrable, y sin dejar de ser el mismo producto. |
| **DEMASIADO GENÉRICA** | Funciona, pero podría ser de cualquier aplicación: cabecera, fondo, tarjeta, botón. Sin identidad. |
| **REQUIERE CORRECCIÓN** | Tiene un defecto concreto de UX, de copy, de estado o de permiso. |

## Cómo se hizo

Cada vista se abrió en el navegador **con el rol que la ve de verdad** —el que
dice `MATRIZ` en `comun/matriz-acceso.js`, no el que uno supone— a 1440×900, y
se miró la captura. Sin backend, así que las listas fallan solas: lo que se
fotografía es el estado degradado, que es el que nadie mira y el que más se
rompe.

La columna «permiso» sale de `MATRIZ`, que es lo que el código ejecuta.
«Responsive» sale del laboratorio visual, que recorre las 32 vistas × 5
actores × 5 anchuras. Las medidas concretas (alturas de cabecera, anchos de
navegación) se tomaron con sondas de `getComputedStyle` sobre la página viva,
no a ojo: a ojo ya se falló dos veces en este bloque.

## Lo que este documento NO es

No es una lista de deseos. Una vista marcada **REQUIERE CORRECCIÓN** lleva el
defecto escrito y, en este bloque, el arreglo hecho. Una vista marcada
**DEMASIADO GENÉRICA** que se deja así lleva la razón y qué haría falta para
cambiarlo.

---

## I · Portal (PublicShell) — 4 vistas

Nadie que vea estas cuatro tiene sesión. Cabecera de portal: marca y la única
acción que falta por hacer. Ninguna de las cuatro nombra los seis destinos del
producto.

### 1 · `login.html` — Iniciar sesión

| | |
|---|---|
| **Actor** | Visitante sin cuenta, o con la sesión caducada |
| **HU / RF** | RF-USR-002 · RF-INV-008 (flujo alternativo) · HU-UX-001 |
| **Propósito** | Decir qué es Nexus Battles VI y dejar entrar |
| **Acción primaria** | «Entrar al Nexo» |
| **Estado visual** | Cromo a pantalla completa, la tarjeta del formulario como única superficie clara, sobretítulo «Return of the Warriors», tres capacidades del juego |
| **Navegación** | Marca + «Crear cuenta». No ofrece «Iniciar sesión»: ya estás ahí |
| **Permiso** | `PUBLICA` |
| **Responsive** | A 375 el formulario va primero; las capacidades bajan |
| **Problema (antes)** | Barra completa del producto con seis destinos, cuatro de los cuales devolvían al propio login. «Proyecto Integrador II · UPB Bucaramanga» encima del nombre del juego |
| **Arreglo** | UX-R3.0 y R3.1 |

**COHERENTE CON JUEGO.**

### 2 · `registro.html` — Crear cuenta

| | |
|---|---|
| **Actor** | Visitante sin cuenta |
| **HU / RF** | RF-USR-001 · RF-INV-008 (flujo alternativo) |
| **Propósito** | Abrir cuenta de jugador |
| **Acción primaria** | Crear la cuenta |
| **Estado visual** | Mismo portal; el formulario es más largo y la columna de capacidades se recorta |
| **Navegación** | Marca + «Iniciar sesión» |
| **Permiso** | `PUBLICA` |
| **Responsive** | Igual que login |
| **Problema (antes)** | La misma línea académica, copiada del login, y la misma barra privada |
| **Arreglo** | UX-R3.0 y R3.1 |

**COHERENTE CON JUEGO.**

### 3 · `restablecer-solicitar.html` — Recuperar el acceso

| | |
|---|---|
| **Actor** | Visitante que perdió la contraseña |
| **HU / RF** | RF-USR-003 |
| **Propósito** | Pedir el código por correo |
| **Acción primaria** | Enviar el código |
| **Estado visual** | Portal, un solo campo, los tres pasos explicados |
| **Navegación** | Marca + «Iniciar sesión» |
| **Permiso** | `PUBLICA` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | Barra privada completa |
| **Arreglo** | UX-R3.1 |

**COHERENTE CON JUEGO.**

### 4 · `restablecer-confirmar.html` — Restablecer contraseña

| | |
|---|---|
| **Actor** | Visitante con el código en el correo |
| **HU / RF** | RF-USR-003 |
| **Propósito** | Canjear el código por una contraseña nueva |
| **Acción primaria** | «Restablecer contraseña» |
| **Estado visual** | Portal completo: sobretítulo «RECUPERAR EL ACCESO», marca grande, explicación, tarjeta con filo de acento, dos campos con «Ver» y las reglas de la contraseña a la vista |
| **Navegación** | Marca + «Iniciar sesión»; al pie, «Pedir otro código» y «Volver a entrar» |
| **Permiso** | `PUBLICA` |
| **Responsive** | Sin incidencias |
| **Problema** | El párrafo del héroe decía «El codigo llega por correo […] pide otro desde «Recuperar contrasena»». **Tres palabras sin tilde en la superficie más visible del producto**, y encima nombrando un destino («Recuperar contraseña») que en esa pantalla se llama «Pedir otro código» |
| **Arreglo** | UX-R3.11: tildes, y el texto nombra el enlace que existe |

**COHERENTE CON JUEGO.**

---

## II · Juego (PlayerShell) — 18 vistas

Cabecera del jugador: los seis destinos de RF-INV-008, el buscador, el saldo, el
apodo y la campana. Ninguna de las dieciocho nombra una herramienta de la
consola —comprobado por `permisos.spec.js`, no por inspección.

### 5 · `index.html` — Inicio

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | RF-INV-008 · HU-UX-001 |
| **Propósito** | Quién eres y a qué juegas |
| **Acción primaria** | «Jugar ahora» |
| **Estado visual** | Portada en cromo: el héroe a la izquierda, la acción a la derecha; debajo, accesos a lo que existe |
| **Navegación** | Cabecera de jugador, «Inicio» activa |
| **Permiso** | `SESION` |
| **Responsive** | La portada se apila a 768 |
| **Problema (antes)** | Sección de administración dentro de la home del jugador. Y el título de la portada salía **blanco sobre blanco** |
| **Arreglo** | UX-R3.2 |

**COHERENTE CON JUEGO.**

> **Lo que NO se añadió.** Se evaluó poner actividad reciente, ranking,
> recompensa diaria y misiones sugeridas: es lo que pondría cualquier home de
> juego. Ninguna de las cuatro tiene HU, RF ni contrato detrás, así que habría
> sido dato inventado en la pantalla de entrada. La home enseña lo que el
> servidor sabe: héroe, créditos, salas abiertas, notificaciones.

### 6 · `perfil.html` — Mi Cuenta

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | RF-USR-00x · RF-INV-008 |
| **Propósito** | Tus datos, tu seguridad y lo que movió tus créditos |
| **Acción primaria** | Editar el perfil |
| **Estado visual** | Datos + accesos a cofres, transacciones y sanciones |
| **Navegación** | «Mi Cuenta» activa |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | Ninguno pendiente |

**COHERENTE CON JUEGO.**

### 7 · `inventario.html` — Mi inventario

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-INV-001 · RF-INV-008 |
| **Propósito** | Lo que posees: héroes y objetos |
| **Acción primaria** | Equipar / ver ficha |
| **Estado visual** | Fichas con la rareza a la vista |
| **Navegación** | «Mi inventario» activa |
| **Permiso** | `SESION` |
| **Responsive** | Rejilla fluida |
| **Problema (antes)** | `padding` en `<body>`: la barra de la aplicación heredaba el margen y no llegaba al borde. La única vista donde la cabecera flotaba |
| **Arreglo** | UX-R3.5, y el laboratorio gana la auditoría `cabeceraDeBordeABorde`, que recorre 32×5 buscando esa clase de fallo. Pilló al momento que `subastas` la reintroducía en sus consultas estrechas |

**COHERENTE CON JUEGO.**

### 8 · `batallas.html` — Jugar online

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SAL-001, HU-SAL-002 |
| **Propósito** | Ver salas abiertas y entrar |
| **Acción primaria** | Entrar a una sala / crear una |
| **Estado visual** | Rejilla de salas con estado y ocupación |
| **Navegación** | «Jugar online» activa |
| **Permiso** | `SESION` |
| **Responsive** | Una columna a 768 |
| **Problema (antes)** | Cuando el listado fallaba con 404, el mensaje decía que no existía **la sala** — un texto escrito para el detalle, puesto en el listado |
| **Arreglo** | UX-R3.4 |

**COHERENTE CON JUEGO.**

### 9 · `crear-sala.html` — Crear sala de batalla

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SAL-001 |
| **Propósito** | Configurar la partida antes de abrirla |
| **Acción primaria** | «Crear sala» |
| **Estado visual** | Tres modalidades como tarjetas elegibles —1 contra 1, contra la IA, hasta seis—, cada una con su frase. Es de las pocas pantallas donde una decisión de juego se ve como una decisión de juego y no como un desplegable |
| **Navegación** | «Jugar online» activa |
| **Permiso** | `SESION` |
| **Responsive** | Las tres modalidades se apilan |
| **Problema** | «Los campos invalidos te dicen el motivo del rechazo», «Combate multiple», «Solo accesible por invitacion», «Enfrentate a un rival controlado» |
| **Arreglo** | UX-R3.11 (tildes) |

**COHERENTE CON JUEGO.**

### 10 · `sala-batalla.html` — Combate

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-JUE-017 (6/6 CA) |
| **Propósito** | El clímax del producto: el turno |
| **Acción primaria** | La acción del turno |
| **Estado visual** | HUD a pantalla completa, cabecera de combate en cromo, sin barra de navegación compitiendo |
| **Navegación** | Salida explícita, no la cabecera de jugador |
| **Permiso** | `SESION` |
| **Responsive** | Verificado a 1024 y 768; el HUD se reordena |
| **Problema (antes)** | Sin partida, el estado vacío no se centraba: `align-items: center` no hacía nada porque `.combate__sala` es `position: absolute`. Lo reveló una sonda de `getComputedStyle` **después de dos intentos fallidos a ojo** |
| **Arreglo** | UX-R3.4 |

**COHERENTE CON JUEGO.** Es la vista con más peso visual del producto, que es lo que §20 pedía comprobar. No se tocó ninguna regla de juego.

### 11 · `validacion-heroe.html` — Verificación de héroe

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SAL-002 |
| **Propósito** | Confirmar con qué héroe entras y qué te cuesta |
| **Acción primaria** | Confirmar la entrada |
| **Estado visual** | Diálogo modal con retrato, estadísticas del héroe y el coste en créditos |
| **Navegación** | Modal; se cancela con Escape |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | Tres, y uno serio. (a) El `<h2>` se llevaba el foco al abrirse y salía con el anillo del kit a todo lo ancho: **en la captura se lee como un campo de texto vacío con un marcador de posición dentro**. (b) Sin sala, el diálogo decía «Abre esta verificacion desde el listado de Batallas, o anade `?sala=<id>` a la direccion» — una instrucción para quien programa, delante de quien juega. (c) Y solo ofrecía «Cancelar»: contaba un problema sin dar salida |
| **Arreglo** | UX-R3.11. El foco sigue moviéndose —lo necesita el lector de pantalla—; lo que se quita es el dibujo, en `[tabindex='-1']`. El texto explica de dónde se abre y la acción primaria lleva a las salas abiertas |

**COHERENTE CON JUEGO.**

### 12 · `chat.html` — Chat

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | RF-CHA-001, HU-CHA-001 |
| **Propósito** | Hablar con quien está jugando |
| **Acción primaria** | Enviar |
| **Estado visual** | Indicador de conexión junto al título, conversación, formulario |
| **Navegación** | «Jugar online» activa |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | Cuatro. (a) Tildes: «queda para quien entra despues», «Sin conexion», «logro de mision». (b) El fallo decía «No hay conexión con el chat» y debajo el mensaje del transporte, «No se pudo abrir el canal»: **las dos frases cuentan el mecanismo**, ninguna dice qué le pasa a quien lee. (c) Una tarjeta blanca vacía encima del formulario, sin nada dentro. (d) El botón «Enviar» ocupaba el ancho entero de la tarjeta |
| **Arreglo** | UX-R3.11: tildes; el fallo dice la consecuencia y ofrece reintentar; el canal en silencio es un estado del producto; `.fila--acciones` |

**COHERENTE CON JUEGO.**

### 13 · `torneos.html` — Torneo

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-TOR-001 y siguientes |
| **Propósito** | Ver e inscribirse |
| **Acción primaria** | Inscribirse |
| **Estado visual** | Lista con fecha, cupo y estado; árbol por llaves cuando el torneo está en curso |
| **Navegación** | «Torneo» activa |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | Al inscribirse salía el mismo aviso dos veces. Y la zona de aviso llevaba la clase `.aviso`, así que el aviso se pintaba dentro de otro aviso |
| **Arreglo** | UX-R3.6 y R3.11 |

**COHERENTE CON JUEGO.**

### 14 · `subastas.html` — Subasta

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SUB-001 y siguientes |
| **Propósito** | Qué se vende, por cuánto y cuánto queda |
| **Acción primaria** | Pujar |
| **Estado visual** | Vitrina con tiempo restante y puja actual en oro |
| **Navegación** | «Subasta» activa |
| **Permiso** | `SESION` |
| **Responsive** | Verificado a 1024, 768 y 375 |
| **Problema (antes)** | Tres a la vez: el kit cargado en orden incorrecto (la hoja de la vista antes que `componentes.css`), `padding` en `<body>` —reintroducido en dos consultas estrechas— y el 404 del listado con el texto del detalle |
| **Arreglo** | UX-R3.7 |

**COHERENTE CON JUEGO.**

### 15 · `publicar-subasta.html` — Publicar un producto

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SUB-001 |
| **Propósito** | Sacar algo tuyo al mercado |
| **Acción primaria** | Publicar |
| **Estado visual** | Cabecera de sección en cromo con filo de acento, pasos numerados 01/02/03 y un panel de confirmación que se actualiza solo. **La mejor construida de las 32**: la única donde un formulario largo se lee como una secuencia y no como una lista de campos |
| **Navegación** | Volver a Subastas |
| **Permiso** | `SESION` |
| **Responsive** | Dos columnas que se apilan |
| **Problema** | El mismo fallo contado **tres veces en una pantalla**: un banner rojo arriba, el mismo texto dentro del paso 01, y encima un error de validación —«Selecciona un producto disponible de tu inventario»— **sobre un formulario que nadie había tocado** |
| **Arreglo** | UX-R3.11: el fallo se cuenta donde está el hueco y con su botón; los errores de campo esperan al primer envío |

**COHERENTE CON JUEGO.**

### 16 · `pujas.html` — Mis pujas

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-SUB-002 y siguientes |
| **Propósito** | Dónde estás pujando y cómo vas |
| **Acción primaria** | Pujar / fijar límite automático |
| **Estado visual** | Panel de créditos segmentado, vitrina, historial de postores |
| **Navegación** | «Subasta» activa |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | El fallo se comía la pantalla entera y decía dos cosas que se contradicen: **«El mercado no responde»** con **«Esa subasta ya no existe.»** debajo. Causa: cualquier 404 se traducía por el texto de la ficha, también cuando lo que fallaba era el listado |
| **Arreglo** | UX-R3.11: el 404 se decide por la ruta pedida |

**COHERENTE CON JUEGO.**

### 17 · `tienda.html` — Tienda

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-PRD-00x |
| **Propósito** | Comprar con créditos |
| **Acción primaria** | Comprar |
| **Estado visual** | Vitrina con el precio en oro |
| **Navegación** | Desde el mercado |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | Ninguno pendiente |

**COHERENTE CON JUEGO.**

### 18 · `mis-cofres.html` — Mis cofres

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-JUE-012 |
| **Propósito** | Lo que has ganado acumulando créditos |
| **Acción primaria** | (lectura) |
| **Estado visual** | Rejilla de tarjetas con contenido y fecha |
| **Navegación** | Desde «Mi Cuenta» |
| **Permiso** | `SESION` |
| **Responsive** | Rejilla fluida |
| **Problema** | Cuatro. (a) **La tercera paleta paralela del repositorio**: `mis-cofres.css` redefinía `.estado` entero, con seis colores escritos a mano, encima de la versión buena de `tema-cuentas.css`. Un hexadecimal no responde a `prefers-contrast: more`. (b) El fallo era una píldora roja de una línea, sin explicación y sin reintento, con la paginación viva debajo. (c) El icono era un emoji 🎁. (d) «Volver» ocupaba casi todo el ancho |
| **Arreglo** | UX-R3.11. Sobre el icono: **el sprite del kit tiene treinta símbolos y ninguno es un cofre**. Coger «trofeo» o «estrella» sería darles un significado que ya tienen ocupado, y dibujar un símbolo nuevo a mano rompería el contrato del sprite —que es un export fiel del archivo de Figma. Queda sin icono y el hueco anotado |

**COHERENTE CON JUEGO.**

### 19 · `historial-transacciones.html` — Historial de transacciones

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-PAG-00x |
| **Propósito** | Cargas, compras y reversos en moneda real |
| **Acción primaria** | Ver el comprobante |
| **Estado visual** | Tabla de seis columnas |
| **Navegación** | Desde «Mi Cuenta» |
| **Permiso** | `SESION` |
| **Responsive** | La tabla desplaza dentro de su contenedor |
| **Problema** | Tres. (a) Cuando la carga falla, **la cabecera de columnas se queda flotando sobre el hueco**: seis títulos de columna encima de datos que no existen, con una píldora roja debajo. (b) La paginación sigue viva, con su «Página 1», ofreciendo pasar páginas de una lista que no se pudo cargar. (c) «Volver» estirado a 570 px |
| **Arreglo** | UX-R3.11 |

**DEMASIADO GENÉRICA — se deja así, con motivo.** Es la pantalla del dinero
real: fecha, concepto, monto, moneda, resultado, comprobante. Un historial de
pagos **tiene que** leerse como un historial de pagos —es lo que alguien va a
mirar cuando reclame un cargo, y ahí la fiction del juego estorba. Es la única
de las 18 donde lo genérico es la decisión correcta, y por eso se anota en vez
de arreglarse. Lo que sí tenía que arreglarse —el estado de fallo y la
paginación— está arreglado.

### 20 · `notificaciones.html` — Notificaciones

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-NOT-001 |
| **Propósito** | Qué ha pasado mientras no estabas |
| **Acción primaria** | Abrir / marcar leída |
| **Estado visual** | Lista con las no leídas destacadas |
| **Navegación** | Desde la campana |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | El fallo del servicio se contaba como mecanismo: se leía el nombre de la operación que había fallado |
| **Arreglo** | UX-R3.8 |

**COHERENTE CON JUEGO.**

### 21 · `mis-sanciones.html` — Mis sanciones

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-MOD-00x |
| **Propósito** | Si tienes una sanción y por qué |
| **Acción primaria** | Apelar |
| **Estado visual** | Lista con motivo y vigencia |
| **Navegación** | Desde «Mi Cuenta» |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | Cuando el servicio se caía, la vista decía **«No tienes permisos»**. Es la peor confusión posible en esta pantalla: le dice a alguien que ya está preocupado por una sanción que además no tiene acceso. Y la zona de aviso llevaba la clase `.aviso` |
| **Arreglo** | UX-R3.8 y R3.11 |

**COHERENTE CON JUEGO.**

### 22 · `publicar-comentario.html` — Comunidad

| | |
|---|---|
| **Actor** | Jugador |
| **HU / RF** | HU-COM-00x |
| **Propósito** | Opinar sobre un producto |
| **Acción primaria** | Publicar |
| **Estado visual** | Formulario con calificación, imágenes opcionales y el hilo debajo |
| **Navegación** | Desde la ficha del producto |
| **Permiso** | `SESION` |
| **Responsive** | Sin incidencias |
| **Problema** | Tildes en el aviso del filtro y en el promedio de calificación |
| **Arreglo** | UX-R3.11 |

**COHERENTE CON JUEGO.** Es, además, la vista que mejor trata un conflicto: el
409 de calificación simultánea ofrece «reintentar sin calificar» en vez de
perder lo escrito.

---

## III · Consola (AdminShell) — 10 vistas

Cabecera de consola: marca con sufijo «Control», filo de acento a lo ancho,
distintivo de rol a la vista, salida al juego y las herramientas filtradas por
la matriz. Más densa que la del jugador y sin HUD de juego. Ningún jugador ve
ninguna de las diez —comprobado por `permisos.spec.js`.

### 23 · `consola.html` — Resumen

| | |
|---|---|
| **Actor** | Moderador · Administrador · Super administrador |
| **HU / RF** | RF-RBAC-001, RF-RBAC-002 |
| **Propósito** | Qué herramientas tienes |
| **Acción primaria** | Abrir una herramienta |
| **Estado visual** | Rejilla de herramientas filtrada por rol, más un párrafo «Tu alcance» que dice **en palabras** qué puede hacer ese rol |
| **Navegación** | Cabecera de consola |
| **Permiso** | `MODERACION` |
| **Responsive** | La navegación se pliega bajo 1366 |
| **Problema (antes)** | No existía. La administración era un desplegable dentro de la cabecera del jugador |
| **Arreglo** | Creada en UX-R3.3 |

**COHERENTE CON ADMIN.**

> **Lo que NO se añadió.** Un resumen administrativo pide a gritos tarjetas de
> métrica: usuarios activos, sanciones abiertas, ingresos del día. No se
> pusieron porque no hay contrato que las devuelva, y un número inventado en la
> pantalla de resumen de una consola es peor que no tener número. «Tu alcance»
> ocupa ese sitio diciendo algo que sí es verdad, y que además responde a la
> pregunta que de verdad se hace quien entra: por qué me salen estas opciones y
> no otras.

### 24 · `gestion-usuarios.html` — Usuarios

| | |
|---|---|
| **Actor** | Administrador · Super administrador |
| **HU / RF** | RF-USR-004..007, RF-RBAC-003 |
| **Propósito** | Buscar una cuenta y actuar sobre ella |
| **Acción primaria** | Cambiar rol / estado |
| **Estado visual** | Tabla con filtro, densidad de consola |
| **Navegación** | «Usuarios» activa |
| **Permiso** | `ADMINISTRACION` |
| **Responsive** | La tabla desplaza dentro de su contenedor, no la página |
| **Problema (antes)** | Llamaba a `exigirSesion()` **y nada más**. Un jugador que escribiera la URL veía la pantalla entera: qué columnas tiene y qué acciones ofrece. La API le negaba los datos, pero la interfaz ya se lo había contado |
| **Arreglo** | UX-R3.0. La denegación marca el documento (`data-acceso="denegado"`) porque esta vista tiene la guarda en un `<script>` y monta la cabecera desde otro: lanzar una excepción detiene el primero y no el segundo, y la cabecera de consola se pintaba **encima** de la pantalla de «sin acceso» |

**COHERENTE CON ADMIN.**

### 25 · `crear-cuenta-admin.html` — Crear cuenta administrativa

| | |
|---|---|
| **Actor** | **Solo** super administrador |
| **HU / RF** | RF-RBAC-003 |
| **Propósito** | Dar de alta un moderador o un administrador |
| **Acción primaria** | Crear la cuenta |
| **Estado visual** | Formulario con los permisos del rol elegido a la vista |
| **Navegación** | «Usuarios» activa |
| **Permiso** | `SUPERADMINISTRACION` |
| **Responsive** | Sin incidencias |
| **Problema** | **La vista estaba muerta.** `checkPermission(PERMISO_CREAR)` se llamaba con un argumento cuando la firma es `(rol, accion)`: el permiso entraba como rol y la acción llegaba `undefined`. Y además **nunca se cargaba la matriz RBAC**, que es de donde `checkPermission` lee. Las dos cosas juntas: nadie podía crear una cuenta administrativa, tampoco un super administrador. Se vio en el barrido, en la captura tomada con persona `SUPER_ADMINISTRADOR`: «Acceso restringido». La única vista que cumple RF-RBAC-003 llevaba quién sabe cuánto sin funcionar para ningún rol |
| **Arreglo** | UX-R3.11: firma correcta, matriz cargada como en `gestion-usuarios`, y la denegación distingue «tu rol no alcanza» de «el servicio de identidad no responde» |

**COHERENTE CON ADMIN.**

### 26 · `productos.html` — Catálogo

| | |
|---|---|
| **Actor** | Administrador · Super administrador |
| **HU / RF** | HU-PRD-00x · Tabla 24 («Gestionar productos — No / No / Sí / Sí») |
| **Propósito** | Alta y edición del catálogo |
| **Acción primaria** | Crear producto |
| **Estado visual** | Tabla de consola |
| **Navegación** | «Productos» activa |
| **Permiso** | `ADMINISTRACION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | **Abierta a cualquiera con sesión**, mientras el backend restringe el POST a administradores. Es el hueco de permisos más serio que apareció después de los ocho originales: la vista entera de gestión de catálogo, con su formulario de alta, visible para un jugador. Y `productos.css` escribía su propia paleta |
| **Arreglo** | UX-R3.3 |

**COHERENTE CON ADMIN.**

### 27 · `sanciones-admin.html` — Moderación · Sanciones

| | |
|---|---|
| **Actor** | Moderador · Administrador · Super administrador |
| **HU / RF** | HU-MOD-00x |
| **Propósito** | Emitir, revisar y revertir sanciones |
| **Acción primaria** | Emitir sanción |
| **Estado visual** | Historial de un usuario + formulario de emisión + apelaciones |
| **Navegación** | «Sanciones» activa. Con rol de moderador, la cabecera enseña **tres** destinos: Resumen, Sanciones, Lista negra |
| **Permiso** | `MODERACION` |
| **Responsive** | Sin incidencias |
| **Problema** | Tres. (a) Cuando las apelaciones no cargan, el fallo era una píldora ámbar dentro de una caja oscura —un `.aviso` dentro de otro `.aviso`, porque la zona llevaba además la clase— con el texto «No se pudo completar» y nada más. (b) «Politica violada». (c) «Ver historial» estirado a 810 px para dos palabras |
| **Arreglo** | UX-R3.11: el fallo se cuenta en su zona, con reintento; la zona de aviso deja de llevar la clase |

**COHERENTE CON ADMIN.**

### 28 · `lista-negra-admin.html` — Lista negra

| | |
|---|---|
| **Actor** | Moderador · Administrador · Super administrador |
| **HU / RF** | HU-MOD-00x |
| **Propósito** | Términos que el filtro de contenido bloquea |
| **Acción primaria** | Añadir / quitar término |
| **Estado visual** | Lista de términos + formulario en línea |
| **Navegación** | «Lista negra» activa |
| **Permiso** | `MODERACION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | **150 líneas de CSS incrustado en el HTML, con su propia paleta de 13 hexadecimales.** El segundo producto visual paralelo del repositorio. Y el mismo fallo de herencia de color: el subtítulo, blanco sobre blanco |
| **Arreglo** | UX-R3.3 |

**COHERENTE CON ADMIN.**

### 29 · `parametros-admin.html` — Parámetros del sistema

| | |
|---|---|
| **Actor** | Administrador · Super administrador (el moderador consulta) |
| **HU / RF** | HU-PAR-00x |
| **Propósito** | Los valores que gobiernan el juego, en caliente |
| **Acción primaria** | Cambiar un parámetro con motivo y vigencia |
| **Estado visual** | Lista de parámetros con su regla, su origen y su versión |
| **Navegación** | «Parámetros» activa |
| **Permiso** | `ADMINISTRACION` (lectura desde `MODERACION`) |
| **Responsive** | Sin incidencias |
| **Problema** | Cuando el catálogo no carga, la pantalla entera quedaba en blanco —setecientos píxeles de fondo— con una píldora ámbar arriba que decía **«No se pudo completar»** y nada más: ni qué falló, ni qué hacer, ni cómo reintentar |
| **Arreglo** | UX-R3.11: el fallo de la vista entera es un estado de la vista entera, con reintento. El catálogo vacío también pasa a ser un estado y no un párrafo suelto |

**COHERENTE CON ADMIN.**

### 30 · `panel-metricas.html` — Métricas

| | |
|---|---|
| **Actor** | Administrador · Super administrador |
| **HU / RF** | HU-MET-001, HU-MET-004 |
| **Propósito** | Latencia por operación, lecturas y escrituras separadas |
| **Acción primaria** | Filtrar por periodo |
| **Estado visual** | Distintivos y tabla |
| **Navegación** | «Métricas» activa |
| **Permiso** | `ADMINISTRACION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | La guarda vivía en un **segundo** `<script type="module">`: el panel disparaba sus consultas antes de comprobar nada, así que quien no tenía permiso veía la pantalla montarse y devolver 403 una por una antes de que el navegador lo sacara de ahí. Y la copia visible citaba `HU-MET-004` y `RNF-REN-001` |
| **Arreglo** | UX-R3.0 y R3.3 |

**COHERENTE CON ADMIN.**

### 31 · `tablero-tecnico.html` — Tablero técnico y de moderación

| | |
|---|---|
| **Actor** | Administrador · Super administrador |
| **HU / RF** | HU-REN-002, HU-REN-003, HU-MET-00x |
| **Propósito** | Salud de los servicios y actividad de moderación |
| **Acción primaria** | Consultar un periodo |
| **Estado visual** | Dos tarjetas: métricas técnicas arriba, usuarios y moderación abajo |
| **Navegación** | «Técnico» activa |
| **Permiso** | `ADMINISTRACION` |
| **Responsive** | Sin incidencias |
| **Problema** | La tarjeta de **usuarios y moderación** anunciaba «No se pudo obtener el informe **de latencia**». Causa: las dos tarjetas comparten `ErrorDeMetricas`, cuyo respaldo nombraba un informe concreto. Además título y detalle decían casi lo mismo, y no había reintento |
| **Arreglo** | UX-R3.11: el respaldo no nombra informe —lo nombra quien pinta—, y cada tarjeta ofrece reintentar lo suyo |

**COHERENTE CON ADMIN.**

### 32 · `auditoria.html` — Auditoría

| | |
|---|---|
| **Actor** | **Solo** super administrador |
| **HU / RF** | RF-RBAC-003 · `ms-cumplimiento` declara `/api/v1/admin/auditoria` como `hasRole("SUPER_ADMINISTRADOR")` |
| **Propósito** | El rastro de lo que se hizo en la trastienda |
| **Acción primaria** | Filtrar el rastro |
| **Estado visual** | Tabla densa |
| **Navegación** | «Auditoría» activa, solo visible para super administrador |
| **Permiso** | `SUPERADMINISTRACION` |
| **Responsive** | Sin incidencias |
| **Problema (antes)** | Se ofrecía a **cualquier** administrador, cuando el servicio ya la restringía al super administrador: quien entraba veía la pantalla y luego un 403 |
| **Arreglo** | UX-R3.0. Y en R3.11 sus botones pasan a la familia del kit, que sí tiene estado deshabilitado |

**COHERENTE CON ADMIN.**

---

## IV · Lo que se repitió

Seis defectos aparecieron más de una vez, en vistas que no tienen nada que ver
entre sí. Eso los convierte en fallos del sistema, no de la pantalla — y en
candidatos a guardián, no a parche.

### 1 · El color heredado que no se hereda · **3 sitios**

Un contenedor oscuro declara `color: var(--texto-sobre-color)`. El color se
hereda, así que todo lo de dentro debería quedar claro. Pero `base.css` declara
color **directamente** sobre `h1, h2, h3…`, y una declaración propia gana a la
heredada. Resultado: el `<h1>` sobrevive y el `<p class="…__titulo">` de al lado
sale blanco sobre blanco.

Sitios: portada de la home (R3.2), cabecera de lista negra (R3.3), `<h1>` de
productos (R3.3). Los tres se veían distintos y se diagnosticaban distinto.

### 2 · El error que no sabe de qué recurso habla · **5 sitios**

Un mensaje escrito para una pantalla, mostrado en otra.

| Sitio | Decía | Cuando lo que fallaba era |
|---|---|---|
| salas (R3.4) | «esa sala no existe» | el listado |
| subastas (R3.7) | el texto del detalle | el listado |
| sanciones (R3.8) | «No tienes permisos» | el servicio, caído |
| tablero técnico (R3.11) | «el informe **de latencia**» | el informe de moderación |
| pujas (R3.11) | «Esa subasta ya no existe» **y** «El mercado no responde», a la vez | el listado |

Los dos últimos comparten causa exacta: una clase de error con un respaldo que
**nombra un recurso concreto**, compartida por dos llamadas distintas.

### 3 · El relleno en `<body>` que la barra hereda · **2 sitios**

`body { padding: 24px }` hace que la barra de la aplicación, que es hija de
`<body>`, no llegue al borde de la pantalla.

Sitios: inventario (R3.5), subastas (R3.7).

**Este ya tiene guardián.** El laboratorio gana la auditoría
`cabeceraDeBordeABorde`, que mide la posición real de la barra en 32×5. Pilló de
inmediato que `subastas` volvía a introducirlo dentro de sus consultas
estrechas, donde no se ve a 1440.

### 4 · La paleta paralela · **3 hojas**

Una hoja de vista que escribe sus propios colores en vez de usar las fichas del
kit. No responde a `prefers-contrast: more`, así que quien activa «aumentar
contraste» en su sistema operativo lo recibe en casi todo el producto y **no**
en esas pantallas.

Sitios: `<style>` incrustado de lista negra, 13 hexadecimales (R3.3) ·
`productos.css` (R3.3) · `mis-cofres.css` (R3.11).

El guardián existente (`tokens-sin-sombra`) no pillaba el tercero, y la razón
importa: comprueba que ninguna hoja de vista redefina una clase **del kit**.
`.estado` no es del kit: es de `tema-cuentas.css`, otra hoja de vista. Dos hojas
de vista, una tapando a la otra, caían por el agujero.

### 5 · El aviso dentro del aviso · **4 de 8 zonas**

`pintarAviso` mete un `.aviso` **dentro** de la zona; la zona se queda como
contenedor. Cuatro de las ocho zonas llevaban además la clase `.aviso`, así que
salía una caja con otra caja dentro: en las capturas, un rectángulo oscuro con
una píldora ámbar pegada a la izquierda. Y con dos `role` contradictorios,
`status` fuera y `alert` dentro.

Sitios: parámetros, mis sanciones, sanciones-admin, torneos. Arreglado en R3.11.

### 6 · El botón que mide lo que mide la tarjeta · **5 sitios**

Un botón suelto dentro de una `.pila` se estira a todo el ancho del contenedor.
Un «Volver» de seis letras ocupando 570 px no se lee como un botón: se lee como
una barra.

Sitios: mis cofres, historial de transacciones, chat, sanciones-admin («Ver
historial», 810 px) y tablero técnico. La solución ya existía —`.fila--acciones`,
creada en R3.0— y simplemente no se había aplicado aquí.

### Y uno que solo se ve con herramienta · **32 vistas**

`button-name`, impacto **crítico**, en las 32 vistas privadas **a 375 px y solo
a 375 px**: el botón de cuenta de la cabecera se queda sin nombre accesible en
el móvil. Lo único con texto dentro es `.cabecera__identidad`, que la cabecera
oculta cuando no cabe; el avatar y el chevron son decoración con `aria-hidden`.
En escritorio no se ve porque el apodo da el nombre.

Es exactamente la clase de defecto que una revisión a ojo no encuentra —la
pantalla se ve bien— y que axe encuentra a la primera. Arreglado en R3.11
poniendo el nombre en el `aria-label` del propio botón, donde el CSS no llega.

---

## V · Recuento

| Veredicto | Vistas |
|---|---|
| **COHERENTE CON JUEGO** | 17 |
| **COHERENTE CON ADMIN** | 10 |
| **DEMASIADO GENÉRICA** | 1 · `historial-transacciones`, con motivo escrito |
| **REQUIERE CORRECCIÓN** | 0 |

Las 4 del portal y las 18 del juego suman 22, de las cuales 17 quedan
COHERENTE CON JUEGO, 4 son del portal —que es su propio armazón— y 1 se marca
DEMASIADO GENÉRICA a propósito.

### Defectos encontrados en este barrido y corregidos

| # | Vista | Qué |
|---|---|---|
| 1 | todas (32, solo a 375 px) | El botón de cuenta sin nombre accesible. **Crítico** |
| 2 | `auditoria`, `historial-transacciones` (375 px) | Región desplazable sin acceso por teclado. **Serio** |
| 3 | `crear-cuenta-admin` | La vista no funcionaba para ningún rol: firma mal y matriz RBAC sin cargar |
| 4 | todas las de consola (1280–1512 px) | Cabecera partida en dos filas |
| 5 | 27 ficheros | 101 palabras sin tilde en texto visible |
| 6 | 4 zonas de aviso | `.aviso` dentro de `.aviso`, con dos `role` |
| 7 | `mis-cofres` | Tercera paleta paralela; fallo sin salida; emoji |
| 8 | `historial-transacciones` | Cabecera de columnas sobre el hueco; paginación viva sobre una lista fallida |
| 9 | `parametros-admin`, `sanciones-admin` | «No se pudo completar» sobre pantalla vacía |
| 10 | `tablero-tecnico` | Una tarjeta anunciaba el fallo de la otra |
| 11 | `pujas` | Título y detalle contradiciéndose |
| 12 | `publicar-subasta` | El mismo fallo tres veces; validación sobre formulario intacto |
| 13 | `validacion-heroe` | `?sala=<id>` en pantalla; diálogo sin salida; `<h2>` con aspecto de campo |
| 14 | `chat` | Tarjeta vacía; error como mecanismo; botón estirado |
| 15 | `restablecer-confirmar` | Tildes en el portal; texto nombrando un enlace que no existe |
| 16 | `crear-sala` | Tildes |
| 17 | kit | `.dialogo__titulo` declarado dos veces con dos escalas distintas |

### Deuda medida que este bloque NO cierra

**94 literales de color en 9 hojas de vista.** Medido con los comentarios
descontados, sobre las hojas que sigue git:

| Hoja | Literales |
|---|---|
| `cuentas/pujas.css` | **59** (21 colores distintos) |
| `cuentas/historial-transacciones.css` | 15 |
| `cuentas/tema-cuentas.css` | 9 |
| `cuentas/registro.css` | 4 |
| `contenido/inventario/ficha-producto.css` | 3 |
| otras cuatro | 1 cada una |

Hay que separar dos cosas que no son lo mismo:

- **Las tres paletas paralelas** —las que redefinían un componente compartido
  con sus propios colores y por tanto **tapaban el modo de alto contraste del
  kit**— están las tres eliminadas: el `<style>` de lista negra (R3.3),
  `productos.css` (R3.3) y `mis-cofres.css` (R3.11). Eso es lo que rompía
  accesibilidad, y eso está cerrado; `tokens-sin-sombra` lo vigila.
- **Lo que queda son literales dentro de componentes propios de una vista** que
  no tapan nada del kit: `.tarjeta-subasta`, `.badge-legendaria`,
  `.caja-consejo-tactico`. No rompen el alto contraste, pero tampoco salen del
  sistema de diseño, así que «paleta oficial respetada» **no se cumple del
  todo** y decirlo es más útil que decir que sí.

El grueso es `pujas.css`, que está en la lista protegida de HU-SUB-001 y cuyo
dueño es grupo-4. Sus 21 colores incluyen los distintivos de rareza
—`.badge-comun`, `.badge-epica`, `.badge-legendaria`—, que son el caso más claro
de algo que **debería** salir de las fichas de rareza del kit, porque la rareza
es un concepto de primera clase del producto y no un adorno de una pantalla.

Hacerlo aquí, al cierre y contra un fichero protegido, sería meter un cambio
visual grande sin su dueño y sin evidencia propia. Queda como trabajo con
nombre y con número, no como «pendiente de pulir».

### Lo que queda anotado y no se toca

- **El sprite no tiene icono de cofre.** Treinta símbolos, ninguno sirve. `mis-cofres` queda sin icono antes que con uno que signifique otra cosa. Pedirlo al archivo de Figma es trabajo de diseño, no de este bloque.
- **`historial-transacciones` es genérica a propósito.** Motivo arriba.
- **Los hallazgos `moderate` y `minor` de axe** se publican como aviso y no bloquean. Convertirlos en compuerta de golpe obliga a tocar las 32 vistas en un PR.
