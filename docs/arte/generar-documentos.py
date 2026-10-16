#!/usr/bin/env python3
"""
Genera los documentos de la fase 2 de arte a partir de UNA fuente:
`docs/arte/manifest-assets.json`.

    python3 docs/arte/generar-documentos.py

Escribe (y despues: npx prettier --write sobre arte-manifiesto.js):
  * docs/arte/manifest-assets.md      — la tabla del manifiesto (estado de cada asset)
  * docs/arte/PROMPTS-ASSETS-NEXUS.md  — un prompt listo para copiar por asset, por lote

Para cambiar el estado de un asset (PENDIENTE → GENERADO → APROBADO →
OPTIMIZADO → INTEGRADO) se edita el JSON y se vuelve a generar: así la tabla
y los prompts nunca se separan.
"""
import json
import pathlib
import sys

AQUI = pathlib.Path(__file__).resolve().parent
FUENTE = AQUI / 'manifest-assets.json'

ORDEN_LOTES = ['0', '1', '2', '3', '4', '5']


def cargar():
    return json.loads(FUENTE.read_text(encoding='utf-8'))


def ruta_final(m, a):
    return f"{m['raiz']}/{a['archivo']}"


def prompt_de(m, a):
    b = m['bloques']
    cat = m['categorias'][a['categoria']]
    partes = [b['estilo'], cat['formato'], f"Asunto: {a['descripcion']}"]
    if cat.get('referencia'):
        partes.append(cat['referencia'])
    return '\n\n'.join(partes)


def negativos_de(m, a):
    cat = m['categorias'][a['categoria']]
    return '; '.join([m['bloques']['evitar']] + cat.get('negativos', []) + a.get('negativos', []))


def tabla_manifiesto(m):
    filas = [
        '| ID | Tipo | Nombre | Archivo | Ratio | Uso | Estado | Fuente |',
        '|---|---|---|---|---|---|---|---|',
    ]
    for a in m['assets']:
        filas.append(
            f"| {a['id']} | {m['categorias'][a['categoria']]['tipo']} | {a['nombre']} | "
            f"`{ruta_final(m, a)}` | {m['categorias'][a['categoria']]['ratio']} | "
            f"{', '.join(a['uso'])} | {a['estado']} | {a['fuente']} |"
        )
    return '\n'.join(filas)


def escribir_manifiesto(m):
    cuenta = {}
    for a in m['assets']:
        clave = (a['lote'], a['estado'])
        cuenta[clave] = cuenta.get(clave, 0) + 1
    lotes = '\n'.join(
        f"| {l} | {m['lotes'][l]['nombre']} | "
        f"{sum(1 for a in m['assets'] if a['lote'] == l)} | {m['lotes'][l]['nota']} |"
        for l in ORDEN_LOTES
    )
    especs = '\n'.join(
        f"| {c['tipo']} | {c['ratio']} | {c['generar']} | {c['exportar']} | {c['peso']} |"
        for c in m['categorias'].values()
    )
    texto = f"""# Manifiesto de assets — fase 2 de arte

> Generado por `docs/arte/generar-documentos.py` desde `manifest-assets.json` (fuente única).
> No editar a mano: editar el JSON y volver a generar.

Actualizado: {m['actualizado']} · Raíz de los archivos finales: `{m['raiz']}/` (se sirve en
`/{m['raiz']}/…`, mismo origen). Estados: PENDIENTE → GENERADO → APROBADO → OPTIMIZADO → INTEGRADO.

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
{especs}

Los masters (PNG del generador) no se suben al repositorio: solo el WebP final.

## Lotes

| Lote | Contenido | Assets | Nota |
|---|---|---|---|
{lotes}

## Manifiesto

{tabla_manifiesto(m)}
"""
    (AQUI / 'manifest-assets.md').write_text(texto, encoding='utf-8')


def escribir_prompts(m):
    b = m['bloques']
    secciones = []
    for l in ORDEN_LOTES:
        del_lote = [a for a in m['assets'] if a['lote'] == l]
        if not del_lote:
            continue
        secciones.append(f"## Lote {l} — {m['lotes'][l]['nombre']}\n\n{m['lotes'][l]['nota']}\n")
        for a in del_lote:
            cat = m['categorias'][a['categoria']]
            interpretacion = (
                f"\n**Interpretación visual (aprobar con el PO):** {a['interpretacion']}\n"
                if a.get('interpretacion') else ''
            )
            secciones.append(f"""### {a['id']} · {a['nombre']}

| Campo | Valor |
|---|---|
| Categoría | {cat['tipo']} |
| Entidad | {a['fuente']} |
| Uso | {', '.join(a['uso'])} |
| Aspect ratio | {cat['ratio']} — generar {cat['generar']} |
| Archivo que me entregas | `{a['id']}.png` |
| Archivo final | `{ruta_final(m, a)}` ({cat['exportar']}) |
{interpretacion}
**Prompt principal**

```text
{prompt_de(m, a)}
```

**Negative constraints**

```text
{negativos_de(m, a)}
```

**Notas de crop:** {a.get('crop') or cat['crop']}
""")
    texto = f"""# Prompts de generación — Nexus Battles VI, fase 2 de arte

> Generado por `docs/arte/generar-documentos.py` desde `manifest-assets.json`.
> Cada bloque «Prompt principal» está completo: se copia tal cual en el generador.

## Cómo generar (el mismo flujo que los fondos)

1. Una sola conversación del generador por lote, adjuntando siempre como referencia de estilo
   `shared/ui-kit/fondos/entrada.webp` (la del login, igual que en los fondos).
2. **Lote 0 primero**: {b['lote0']}
3. Desde el lote 1, adjunta además la imagen aprobada de su misma categoría (el héroe aprobado para
   los héroes, el arma aprobada para los objetos, la misión aprobada para las misiones) y pide
   «mismo estilo, encuadre e iluminación que la referencia».
4. Si el generador permite pegar un prompt negativo, pega el bloque «Negative constraints»; si no,
   añádelo al final del prompt con «Evitar: …».
5. Descarga cada imagen en PNG al tamaño indicado y nómbrala con su ID (`HER-01.png`,
   `MIS-03.png`…). Entrégamelas en el chat o en `C:\\dev\\nexus-battles-vi\\.motion-transfer\\arte\\lote-N\\`.
   Yo las valido, recorto, convierto a WebP, las coloco en su ruta final y las integro.

## Bloques fijos

**Estilo (en todos los prompts)**

```text
{b['estilo']}
```

**Evitar (en todos los prompts)**

```text
{b['evitar']}
```

{chr(10).join(secciones)}"""
    (AQUI / 'PROMPTS-ASSETS-NEXUS.md').write_text(texto, encoding='utf-8')


def comprobar(m):
    ids = [a['id'] for a in m['assets']]
    archivos = [a['archivo'] for a in m['assets']]
    errores = []
    if len(ids) != len(set(ids)):
        errores.append('IDs repetidos')
    if len(archivos) != len(set(archivos)):
        errores.append('archivos repetidos')
    for a in m['assets']:
        if a['categoria'] not in m['categorias']:
            errores.append(f"{a['id']}: categoría desconocida")
        if a['lote'] not in m['lotes']:
            errores.append(f"{a['id']}: lote desconocido")
        if a['estado'] not in ('PENDIENTE', 'GENERADO', 'APROBADO', 'OPTIMIZADO', 'INTEGRADO'):
            errores.append(f"{a['id']}: estado desconocido")
        if not a['archivo'].endswith('.webp'):
            errores.append(f"{a['id']}: el archivo final debe ser .webp")
    return errores


def escribir_modulo_frontend(m):
    """El mapa del frontend: una sola fuente (el manifiesto) para las vistas."""
    raiz_repo = AQUI.parent.parent
    seed = json.loads((raiz_repo / 'services/contenido/productos/src/main/resources/semilla/'
                       'catalogo-inicial.json').read_text(encoding='utf-8'))
    nombres = {e['id']: e['nombre'] for k in ('heroes', 'armas', 'armaduras', 'items', 'epicas') for e in seed[k]}
    protos = {e['id']: e.get('prototipo') for e in seed['heroes']}
    raiz = '/' + m['raiz'] + '/'
    prod, mis, her = [], [], []
    for a in m['assets']:
        c = a['categoria']
        if c in ('otro', 'emblema') or a['estado'] != 'INTEGRADO':
            continue
        url = raiz + a['archivo']
        if c == 'mision':
            mis.append((a['entidad'], url))
            continue
        prod.append((a['entidad'], nombres[a['entidad']], url))
        if c == 'heroe':
            her.append((protos[a['entidad']], url))
    lineas = [
        '// Generado por docs/arte/generar-documentos.py desde docs/arte/manifest-assets.json.',
        '// No editar a mano. El guardian tests/contratos/arte-del-catalogo.py lo compara con el manifiesto.',
        '',
        '/** productoId -> [nombre del catalogo, WebP]. */',
        'export const ARTE_DE_PRODUCTOS = Object.freeze({',
        *[f"  '{i}': ['{n}', '{u}']," for i, n, u in prod],
        '});',
        '',
        '/** misionId -> WebP panoramico. */',
        'export const ARTE_DE_MISIONES = Object.freeze({',
        *[f"  '{i}': '{u}'," for i, u in mis],
        '});',
        '',
        '/** prototipo (Tabla 6) -> retrato. */',
        'export const RETRATOS_DE_PROTOTIPOS = Object.freeze({',
        *[f"  '{p}': '{u}'," for p, u in her],
        '});',
    ]
    (raiz_repo / 'frontend/app-web/src/comun/ui/juego/arte-manifiesto.js').write_text(
        '\n'.join(lineas) + '\n', encoding='utf-8')


if __name__ == '__main__':
    manifiesto = cargar()
    fallos = comprobar(manifiesto)
    if fallos:
        sys.exit('\n'.join(fallos))
    escribir_manifiesto(manifiesto)
    escribir_prompts(manifiesto)
    escribir_modulo_frontend(manifiesto)
    print(f"{len(manifiesto['assets'])} assets: manifest-assets.md y PROMPTS-ASSETS-NEXUS.md al día.")
