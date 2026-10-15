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
