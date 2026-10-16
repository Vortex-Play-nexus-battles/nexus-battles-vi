#!/usr/bin/env python3
"""
Guardian de la fase 2 de arte (ART-9): lo que dice el manifiesto es lo que hay.

    python3 tests/contratos/arte-del-catalogo.py
    python3 tests/contratos/arte-del-catalogo.py --autoprueba

Falla si:
  * un asset INTEGRADO de docs/arte/manifest-assets.json no tiene su WebP en
    shared/ui-kit/arte/, no es WebP, no tiene el tamano de su tipo o pesa mas
    de 160 KB (nada de imagenes de varios MB en produccion);
  * hay un WebP en shared/ui-kit/arte/ que el manifiesto no declara (un
    archivo duplicado por pantalla, o huerfano);
  * la semilla de productos o la de misiones no declara en `imagen` la ruta
    del manifiesto para su entidad;
  * el mapa del frontend (arte-manifiesto.js) no esta al dia con el manifiesto
    (se regenera con docs/arte/generar-documentos.py).
"""
import json
import pathlib
import re
import struct
import sys
import tempfile
import shutil

RAIZ = pathlib.Path(__file__).resolve().parents[2]
MANIFIESTO = pathlib.Path('docs/arte/manifest-assets.json')
SEMILLA_PRODUCTOS = pathlib.Path('services/contenido/productos/src/main/resources/semilla/catalogo-inicial.json')
SEMILLAS_MISIONES = [
    pathlib.Path('services/contenido/misiones/src/main/resources/semilla/misiones-del-documento.json'),
    pathlib.Path('services/contenido/misiones/src/main/resources/semilla/misiones-de-progresion.json'),
]
MODULO_FRONTEND = pathlib.Path('frontend/app-web/src/comun/ui/juego/arte-manifiesto.js')
TAMANO = {'heroe': (640, 640), 'mision': (960, 540), 'arma': (512, 512), 'armadura': (512, 512),
          'item': (512, 512), 'epica': (512, 512)}
PESO_MAXIMO = 160 * 1024


def tamano_webp(datos: bytes):
    if datos[:4] != b'RIFF' or datos[8:12] != b'WEBP':
        return None
    trozo = datos[12:16]
    if trozo == b'VP8X':
        w = int.from_bytes(datos[24:27], 'little') + 1
        h = int.from_bytes(datos[27:30], 'little') + 1
        return w, h
    if trozo == b'VP8 ':
        w, h = struct.unpack('<HH', datos[26:30])
        return w & 0x3FFF, h & 0x3FFF
    if trozo == b'VP8L':
        b = int.from_bytes(datos[21:25], 'little')
        return (b & 0x3FFF) + 1, ((b >> 14) & 0x3FFF) + 1
    return None


def comprobar(raiz: pathlib.Path) -> list[str]:
    errores = []
    m = json.loads((raiz / MANIFIESTO).read_text(encoding='utf-8'))
    raiz_arte = raiz / m['raiz']
    url = lambda a: '/' + m['raiz'] + '/' + a['archivo']
    integrados = [a for a in m['assets'] if a['estado'] == 'INTEGRADO']

    for a in integrados:
        f = raiz_arte / a['archivo']
        if not f.exists():
            errores.append(f"{a['id']}: falta {f.relative_to(raiz)}")
            continue
        datos = f.read_bytes()
        dim = tamano_webp(datos)
        if dim is None:
            errores.append(f"{a['id']}: {a['archivo']} no es WebP")
        elif dim != TAMANO[a['categoria']]:
            errores.append(f"{a['id']}: {a['archivo']} mide {dim}, su tipo pide {TAMANO[a['categoria']]}")
        if len(datos) > PESO_MAXIMO:
            errores.append(f"{a['id']}: {a['archivo']} pesa {len(datos) // 1024} KB (maximo {PESO_MAXIMO // 1024})")

    declarados = {a['archivo'] for a in m['assets']}
    for f in sorted(raiz_arte.rglob('*.webp')) if raiz_arte.exists() else []:
        rel = f.relative_to(raiz_arte).as_posix()
        if rel not in declarados:
            errores.append(f"shared/ui-kit/arte/{rel} no esta en el manifiesto (duplicado o huerfano)")

    semilla = json.loads((raiz / SEMILLA_PRODUCTOS).read_text(encoding='utf-8'))
    imagen_de = {e['id']: e.get('imagen') for k in ('heroes', 'armas', 'armaduras', 'items', 'epicas')
                 for e in semilla.get(k, [])}
    misiones = {}
    for ruta in SEMILLAS_MISIONES:
        for x in json.loads((raiz / ruta).read_text(encoding='utf-8')).get('misiones', []):
            misiones[x['id']] = x.get('imagen')
    for a in integrados:
        tabla = misiones if a['categoria'] == 'mision' else imagen_de
        if a['entidad'] not in tabla:
            errores.append(f"{a['id']}: la entidad {a['entidad']} no esta en su semilla")
        elif tabla[a['entidad']] != url(a):
            errores.append(f"{a['id']}: la semilla declara {tabla[a['entidad']]!r} y el manifiesto {url(a)!r}")

    modulo = (raiz / MODULO_FRONTEND).read_text(encoding='utf-8')
    urls_modulo = set(re.findall(r"'(/shared/ui-kit/arte/[^']+)'", modulo))
    esperadas = {url(a) for a in integrados}
    for u in sorted(esperadas - urls_modulo):
        errores.append(f"{MODULO_FRONTEND} no tiene {u}: regenera con docs/arte/generar-documentos.py")
    for u in sorted(urls_modulo - esperadas):
        errores.append(f"{MODULO_FRONTEND} declara {u}, que no esta INTEGRADO en el manifiesto")
    return errores


def autoprueba() -> int:
    """Rompe copias a proposito y exige que el guardian lo vea."""
    with tempfile.TemporaryDirectory() as tmp:
        t = pathlib.Path(tmp)
        for rel in [MANIFIESTO, SEMILLA_PRODUCTOS, *SEMILLAS_MISIONES, MODULO_FRONTEND]:
            (t / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(RAIZ / rel, t / rel)
        shutil.copytree(RAIZ / 'shared/ui-kit/arte', t / 'shared/ui-kit/arte')
        assert not comprobar(t), 'la copia sin tocar deberia pasar'
        (t / 'shared/ui-kit/arte/heroes/chaman.webp').unlink()
        (t / 'shared/ui-kit/arte/heroes/sobrante.webp').write_bytes(b'x')
        semilla = t / SEMILLA_PRODUCTOS
        semilla.write_text(semilla.read_text(encoding='utf-8').replace(
            '/shared/ui-kit/arte/heroes/medico.webp', '/shared/ui-kit/arte/heroes/otro.webp'), encoding='utf-8')
        errores = comprobar(t)
        esperados = ['falta shared/ui-kit/arte/heroes/chaman.webp', 'sobrante.webp no esta en el manifiesto',
                     'HER-08: la semilla declara']
        faltan = [e for e in esperados if not any(e in x for x in errores)]
        if faltan:
            print('La autoprueba no vio:', faltan)
            return 1
    print('Autoprueba: el guardian se pone rojo con un WebP borrado, uno huerfano y una semilla desalineada.')
    return 0


def main() -> int:
    if '--autoprueba' in sys.argv:
        return autoprueba()
    errores = comprobar(RAIZ)
    for e in errores:
        print('ERROR', e)
    if errores:
        return 1
    m = json.loads((RAIZ / MANIFIESTO).read_text(encoding='utf-8'))
    n = sum(1 for a in m['assets'] if a['estado'] == 'INTEGRADO')
    print(f'Arte del catalogo al dia: {n} assets integrados, semillas y frontend alineados con el manifiesto.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
