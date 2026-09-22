# Laboratorio visual — UX-R2.1

Recorre las **31 vistas** del producto en **cinco anchuras** (155 combinaciones),
guarda una captura de cada una y **mide** roturas de maquetación.

## Correrlo

```bash
cd frontend/app-web

# Sin backend: cada vista en su estado degradado. ~3,7 min, sin infraestructura.
npx playwright test --config=playwright.visual.config.js

# Contra un backend real (el banco de tests/e2e, o el host de dev).
VISUAL_BASE=http://localhost:8099 npx playwright test --config=playwright.visual.config.js

# Una sola vista
npx playwright test --config=playwright.visual.config.js -g "sala-batalla"
```

En local hace falta que `tests/visual/*.js` encuentre `@playwright/test`, que
vive en `frontend/app-web/node_modules`. Node resuelve subiendo directorios, así
que basta un enlace en la raíz del monorepo — el mismo apaño que usa `e2e.yml`:

```bash
ln -sfn frontend/app-web/node_modules node_modules          # Linux/macOS
mklink /J node_modules frontend\app-web\node_modules        # Windows (cmd)
```

El informe queda en `docs/evidencia/ux-r2/`: `INFORME.md` e `informe.json` se
versionan; las capturas no (ver `.gitignore`), viajan como artefacto de CI.

## Por qué no compara imágenes píxel a píxel

Una comparación de imágenes falla con cada cambio de fuente, cada píxel de
antialiasing y cada actualización del navegador. Acaba desactivada en dos
semanas y nadie vuelve a mirarla.

Lo que de verdad hay que detectar es **estructura rota**, y eso se mide:

| Motivo                         | Qué detecta                                    | Umbral                                |
| ------------------------------ | ---------------------------------------------- | ------------------------------------- |
| `desbordamiento-horizontal`    | el documento es más ancho que el viewport      | +1px de margen por subpíxeles         |
| `elemento-fuera-del-viewport`  | un hijo se sale por la derecha                 | ídem, ignora `position: fixed`        |
| `cabecera-en-mas-de-dos-filas` | la barra superior se parte                     | >140px (una fila son 64)              |
| `modal-fuera-de-pantalla`      | un diálogo abierto no cabe                     | cualquier borde fuera                 |
| `objetivo-tactil-pequeno`      | control por debajo de 44×44                    | solo <768px (WCAG 2.5.5)              |
| `texto-cortado`                | contenido recortado **sin** puntos suspensivos | ignora `.solo-lectores`               |
| `error-tecnico-visible`        | «Error 502» como el mensaje que lee el jugador | exige la etiqueta junto al código     |
| `contraste-insuficiente`       | color de texto contra su fondo **calculado**   | 4,5:1 · 3:1 si es grande (WCAG 1.4.3) |

La captura queda como evidencia para mirarla, no como oráculo.

## Qué falla y qué solo avisa

**Fallan** (son comportamiento, no estética):

- El **catálogo** se quedó corto: alguien añadió una vista y no la registró en
  `vistas.js`. Una lista de cobertura que se queda corta en silencio no sirve.
- El **kit no cargó**: se comprueba que `--cromo` tenga valor. La primera
  corrida de UX-R2.1 servía `src/` en vez de la raíz, los tres CSS daban 404 y
  las 155 capturas salieron sin un solo estilo — con todas las medidas
  mintiendo. Por eso el guardián existe.
- Una **vista privada sin sesión** que no acaba en el login.
- Una **pantalla en blanco**.

**Avisan** los ocho motivos de la tabla. Convertirlos en compuerta es el último
paso de UX-R2.9, cuando ya no queden hallazgos heredados: una compuerta que
nace roja la desactiva alguien en dos días.

## Identidad para las vistas privadas

22 de las 31 vistas exigen sesión, y son justo las que nadie fotografía.

- **Sin backend**: `sesionSintetica()` fabrica un JWT sin firmar (`alg: none`)
  con `sub`/`uid`/`rol`/`exp`. Solo tiene que dejar pintar la pantalla: no hay
  servidor que lo valide.
- **Con backend**: `identidadReal()` **registra una cuenta de verdad** contra
  `/api/v1/auth/registro` y entra por `/api/v1/auth/login`, con apodo y clave
  generados al vuelo (`crypto.randomBytes`).

No hay ninguna contraseña ni secreto escrito en estos archivos. La clave
efímera se puede fijar con `VISUAL_CLAVE` si algún entorno impone su propia
política.
