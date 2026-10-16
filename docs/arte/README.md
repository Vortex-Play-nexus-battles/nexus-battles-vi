# Arte del juego — fase 2 (héroes, misiones y productos)

La fase 1 son los fondos (`shared/ui-kit/fondos/README.md`). Esta fase da arte propio a lo que hoy
usa una figura provisional o un símbolo genérico.

| Archivo | Para qué |
|---|---|
| `AUDITORIA-ARTE-2026-10-14.md` | Qué falta, por qué falta (causa raíz), drift con el documento y plan ART-0…ART-9 |
| `manifest-assets.json` | **Fuente única**: cada asset con su ID, entidad del catálogo, archivo final, uso, lote, estado y descripción |
| `manifest-assets.md` | La tabla del manifiesto, generada desde el JSON |
| `PROMPTS-ASSETS-NEXUS.md` | Un prompt listo para copiar por asset, ordenado por lote, generado desde el JSON |
| `generar-documentos.py` | Regenera los dos `.md` y valida el JSON (IDs y archivos únicos, estados válidos, salida `.webp`) |

## Dirección artística

El mismo mundo que los fondos: la Ciudadela del Nexo, fantasía medieval atravesada por energía
arcana. Azul marino, índigo y violeta, cian eléctrico en runas y cristales en rombo, acentos ámbar.
Concept art de RPG pintado: ni caricatura ni foto.

Cada héroe tiene un color de acento que repiten su equipo y su épica:

| Héroe | Acento |
|---|---|
| Guerrero Tanque | azul cobalto y acero |
| Guerrero Armas | rojo ámbar de brasa |
| Mago Fuego | naranja fuego |
| Mago Hielo | blanco escarcha y cian pálido |
| Pícaro Veneno | verde tóxico y violeta |
| Pícaro Machete | carmesí oscuro |
| Chamán | verde musgo y hueso |
| Médico | verde azulado y blanco hueso |

Para que las siluetas se distingan, cuatro retratos son mujeres (Armas, Hielo, Veneno, Médico) y
cuatro hombres. El documento no fija el género: es una decisión de arte que el PO puede cambiar
editando la descripción en el JSON.

## Flujo

1. Generar el lote en una sola conversación, con `fondos/entrada.webp` como referencia de estilo
   (desde el lote 1, además, la imagen aprobada de la misma categoría).
2. Entregar los PNG nombrados por ID (`HER-01.png`…).
3. Validar, recortar, convertir a WebP con el tamaño y el peso del manifiesto y colocar en
   `shared/ui-kit/arte/<ruta del manifiesto>`.
4. Declarar la ruta en el campo de su contrato (`imagen` de la semilla de productos o de misiones)
   y subir el estado en el JSON hasta INTEGRADO.
5. Guarda `tests/contratos/arte-del-catalogo.py` (ART-9), laboratorio visual, PR, CI, despliegue y
   comprobación en DEV.

## Integración (lote de 65, 2026-10-16)

| Pieza | Dónde |
|---|---|
| WebP finales | `shared/ui-kit/arte/{heroes,misiones,productos/*}/` — 65 archivos, 4,8 MB (los PNG master sumaban 160 MB y no se suben) |
| Exportación | `docs/arte/exportar-assets.py <carpeta-de-PNG>`: héroes 640×640, misiones 960×540, productos 512×512 recortados al contenido con margen (ítems al 60 % con halo), épicas 512×512 |
| Semillas | `imagen` en `catalogo-inicial.json` (versión 3) y en las dos semillas de misiones |
| Puesta al día | `MapeadorDelCatalogo.imagenAlPonerAlDia`: la semilla solo cambia la figura provisional, el vacío o su propia ruta de arte; la imagen de un administrador se respeta |
| Frontend | `comun/ui/juego/arte.js` (+ `arte-manifiesto.js`, generado): la imagen de la API si es real; si es la provisional o falta, el WebP aprobado por id, por nombre (subastas) o por prototipo (combate); si no hay, el icono del tipo |
| Guardián | `tests/contratos/arte-del-catalogo.py` (en CI) |

### Decisiones pendientes del PO (no bloquean)

- **MIS-08 «La Ciudadela de las Sombras»**: los estandartes de la ciudadela enemiga llevan el rombo del Nexo. Si el PO lo marca, se regenera solo esa escena.
- **Género de los retratos**: cuatro mujeres (Guerrero Armas, Mago Hielo, Pícaro Veneno, Médico) y cuatro hombres. El documento no lo fija.
- **Lote 5 opcional** (Cofre de Bronce y 10 emblemas): no se genera hasta que el PO lo apruebe.
