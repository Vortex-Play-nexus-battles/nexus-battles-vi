#!/usr/bin/env python3
"""
Convierte los PNG aprobados (masters, fuera del repo) en los WebP finales de
`shared/ui-kit/arte/`, segun `docs/arte/manifest-assets.json`.

    python3 docs/arte/exportar-assets.py <carpeta-con-los-PNG>

Cada PNG se llama por su ID (`HER-01.png`, `ARD-09.png`...). Reglas por tipo:

* heroe   — cuadrado 640x640, q82 (tarjeta, detalle y combate).
* mision  — panoramica 960x540, q76. MIS-03 lleva un oscurecido suave en la
            esquina superior izquierda (ahi va la etiqueta de dificultad).
* arma / armadura — se recorta al contenido real (alfa > 24, para no contar el
            ruido casi invisible del generador) y se centra en 512x512 con un
            margen del 7 %: el brillo nunca queda cortado. ARD-09, que llego de
            1295x1214, sale como todas: 512x512.
* item    — igual, pero el objeto ocupa el 60 % del lienzo y lleva un halo
            cian suave, para que se lea como accesorio y no como arma.
* epica   — 512x512 opaco, q82 (traen su propio fondo y anillo de runas).
"""
import json
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

AQUI = pathlib.Path(__file__).resolve().parent
RAIZ = AQUI.parent.parent
MANIFIESTO = json.loads((AQUI / 'manifest-assets.json').read_text(encoding='utf-8'))

LADO_PRODUCTO = 512
UMBRAL_ALFA = 24


def contenido(im):
    alfa = im.getchannel('A').point(lambda p: 255 if p > UMBRAL_ALFA else 0)
    return im.crop(alfa.getbbox())


def centrar(im, ocupacion, halo=False):
    obj = contenido(im)
    lado_util = int(LADO_PRODUCTO * ocupacion)
    escala = lado_util / max(obj.size)
    obj = obj.resize((max(1, round(obj.width * escala)), max(1, round(obj.height * escala))), Image.LANCZOS)
    lienzo = Image.new('RGBA', (LADO_PRODUCTO, LADO_PRODUCTO), (0, 0, 0, 0))
    pos = ((LADO_PRODUCTO - obj.width) // 2, (LADO_PRODUCTO - obj.height) // 2)
    if halo:
        sombra = Image.new('RGBA', lienzo.size, (0, 0, 0, 0))
        mascara = Image.new('L', lienzo.size, 0)
        mascara.paste(obj.getchannel('A'), pos)
        mascara = mascara.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(26))
        mascara = mascara.point(lambda p: int(p * 0.55))
        sombra.paste((92, 225, 255, 255), (0, 0), mascara)
        lienzo = Image.alpha_composite(lienzo, sombra)
    capa = Image.new('RGBA', lienzo.size, (0, 0, 0, 0))
    capa.paste(obj, pos)
    return Image.alpha_composite(lienzo, capa)


def oscurecer_esquina(im):
    """Degradado radial suave hacia la esquina superior izquierda."""
    w, h = im.size
    mascara = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(mascara)
    d.ellipse((-w * 0.35, -h * 0.55, w * 0.42, h * 0.5), fill=120)
    mascara = mascara.filter(ImageFilter.GaussianBlur(70))
    negro = Image.new('RGB', (w, h), (8, 10, 24))
    return Image.composite(negro, im, mascara)


def exportar(origen, asset):
    cat = asset['categoria']
    im = Image.open(origen)
    if cat == 'heroe':
        im = im.convert('RGB')
        lado = min(im.size)
        im = im.crop(((im.width - lado) // 2, (im.height - lado) // 2,
                      (im.width + lado) // 2, (im.height + lado) // 2)).resize((640, 640), Image.LANCZOS)
        return im, 82
    if cat == 'mision':
        im = im.convert('RGB')
        objetivo = 16 / 9
        if im.width / im.height > objetivo:
            ancho = round(im.height * objetivo)
            im = im.crop(((im.width - ancho) // 2, 0, (im.width + ancho) // 2, im.height))
        else:
            alto = round(im.width / objetivo)
            im = im.crop((0, (im.height - alto) // 2, im.width, (im.height + alto) // 2))
        im = im.resize((960, 540), Image.LANCZOS)
        if asset['id'] == 'MIS-03':
            im = oscurecer_esquina(im)
        return im, 76
    if cat == 'epica':
        return im.convert('RGB').resize((512, 512), Image.LANCZOS), 82
    im = im.convert('RGBA')
    if cat == 'item':
        return centrar(im, 0.60, halo=True), 80
    return centrar(im, 0.86), 80


def main(carpeta):
    carpeta = pathlib.Path(carpeta)
    raiz_arte = RAIZ / MANIFIESTO['raiz']
    filas = []
    for asset in MANIFIESTO['assets']:
        if asset['categoria'] in ('otro', 'emblema'):
            continue
        origen = carpeta / f"{asset['id']}.png"
        if not origen.exists():
            filas.append((asset['id'], 'FALTA', 0, 0))
            continue
        im, calidad = exportar(origen, asset)
        destino = raiz_arte / asset['archivo']
        destino.parent.mkdir(parents=True, exist_ok=True)
        im.save(destino, 'WEBP', quality=calidad, method=6)
        filas.append((asset['id'], asset['archivo'], origen.stat().st_size, destino.stat().st_size))
    for f in filas:
        print(f"{f[0]}\t{f[1]}\t{f[2] // 1024} KB -> {f[3] // 1024} KB")
    print(f"PNG {sum(f[2] for f in filas) / 1e6:.1f} MB -> WebP {sum(f[3] for f in filas) / 1e6:.2f} MB")


if __name__ == '__main__':
    main(sys.argv[1])
