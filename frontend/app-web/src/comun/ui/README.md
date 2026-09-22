# `comun/ui` — capa de componentes

Construye en JavaScript los componentes que `shared/ui-kit/css` define
visualmente. Nació en el bloque UX porque la auditoría del frontend encontró
esto:

| Síntoma | Medida |
|---|---|
| `function nodo()` reescrita en cada vista | 4 copias |
| `function pintarAviso()` reescrita en cada vista | 6 copias, en **dos familias** que no se parecían |
| `document.createElement` a pelo | 34 archivos |
| Componentes del kit que existían y **nadie usaba** | `tarjeta--subasta`, `marco-heroe`, `esqueleto`, `distintivo`, `ranura`, `metrica`, `pestanas`, `encuentro`, `logro`, `lote`, `linea-tiempo` |
| CSS local por vista | 5.562 líneas, 4 archivos sin una sola `var(--token)` |

## Qué hay

| Módulo | Para qué |
|---|---|
| `dom.js` | `h()`, `nodo()`, `vaciar()`, `clases()`. Siempre `textContent`, nunca `innerHTML` |
| `formato.js` | `creditos()`, `fecha()`, `fechaHora()`, `cuantoFalta()`, `numero()`, `porcentaje()` — una sola localización (`es-CO`) |
| `aviso.js` | `aviso()`, `pintarAviso()`, `limpiarAviso()`, `tonoPorEstado()` (MAPEO-ERRORES, tabla 4) |
| `estado-vista.js` | `estadoVacio()`, `estadoDeError()`, `estadoDeCarga()`, `pintarEstado()` |
| `esqueleto.js` | `esqueletoDeLista()`, `esqueletoDeTarjetas()`, `esqueletoDeFilas()` |
| `boton.js` | `boton()`, `conCarga()` — bloquea, lo anuncia y devuelve el texto exacto |
| `distintivo.js` | `distintivo()`, `distintivoDeRareza()`, `claseDeMarco()` |
| `tarjeta.js` | `tarjeta()`, `tarjetaDeCifra()` |
| `dialogo.js` | `abrirDialogo()`, `confirmar()` — foco atrapado, Escape, foco devuelto |
| `pagina.js` | `encabezadoDePagina()`, `encabezadoDeSeccion()` |

## Reglas

1. **Ningún color, espaciado ni radio a mano.** Todo sale de `tokens.css`.
2. **Ningún `innerHTML` con datos del servidor.** `h()` usa `textContent`.
3. **Los tres estados son obligatorios** en cualquier zona que pida datos:
   vacío, cargando y error. Un rectángulo en blanco no es un estado.
4. **Un error no enseña su código HTTP.** El tono se decide por el estado
   (`tonoPorEstado`), el texto lo escribe quien conoce el dominio.
5. **Si un componente sólo lo usa una vista, no es un componente.** Vive en
   la vista hasta que lo pida una segunda.
