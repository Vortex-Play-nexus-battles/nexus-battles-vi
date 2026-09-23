#!/usr/bin/env python3
"""Guardian: ningun servicio desplegable puede ser invisible para el CD.

EL DEFECTO QUE PREVIENE
-----------------------
El sintoma es siempre el mismo y no lo detecta ninguna compuerta existente:
se abre un PR, CI pasa en verde, se fusiona... y en DEV no existe nada. El
codigo esta, las pruebas estan, la cobertura esta. Lo que no esta es el
servicio, porque el flujo de despliegue no sabe que existe.

Paso de verdad con DOS servicios a la vez:

* ``ms-finanzas`` -- HU-PAG-002 (#536) y HU-JUE-012 (#470) implementadas,
  fusionadas y verdes desde semanas antes; ``GET /api/v1/creditos/.../saldo``
  respondia **502** en el host de dev.
* ``ms-subastas`` -- issue #571, mismo 502, diagnosticado por otra persona de
  forma independiente **sin que nadie relacionara los dos casos**.

La causa raiz es una sola y es un detalle de una linea. ``cd.yml`` descubre
los servicios Gradle asi::

    grep -E '^services/(plataforma|contenido)/[^/]+/'

y despues engancha a mano, bloque por bloque, los de ``services/cuentas/``
-- pero solo los de Maven, porque quien los escribio asumio que
``cuentas`` implicaba Maven. Los comentarios lo dicen con todas las letras:
«NO generalizado a un grep de cuentas/*».

``ms-finanzas`` y ``ms-subastas`` son Gradle **bajo services/cuentas/**. Caen
por las dos rendijas a la vez: la regex generica los excluye por carpeta, y
los bloques manuales los excluyen por herramienta. Ni imagen, ni etiqueta, ni
despliegue, ni mensaje de error -- el flujo simplemente no los nombra nunca.

Y encima el borde SI los enruta (``srv-ms-finanzas:8093``,
``srv-ms-subastas:8092``), asi que nginx resuelve un nombre que no existe y
devuelve 502. El defecto se presenta disfrazado de fallo de red.

POR QUE UNA PRUEBA Y NO UNA NOTA EN EL README
---------------------------------------------
Porque la nota ya existia. ``cd.yml`` avisa en un comentario que al agregar
un servicio «hay que sumarlo aqui Y en docker-compose.yml Y en
docker-compose.deploy.yml -- no se deriva solo». El comentario estaba, se
leyo, y aun asi pasaron dos servicios de largo. Un invariante que depende de
que alguien recuerde leer un comentario no es un invariante.

QUE COMPRUEBA
-------------
Para cada servicio desplegable (carpeta bajo ``services/*/*`` con ``src/main``
y ``Dockerfile`` propio):

1. **El CD lo conoce** -- tiene puerto asignado, sea en ``puerto_de()`` o en
   un bloque propio de ``detectar-servicios``.
2. **El puerto es coherente de punta a punta** -- el del CD (lado host) es el
   que publica su compose, y el del contenedor es el que declara su
   ``Dockerfile``/``server.port``.
3. **Tiene descriptor de ejecucion** -- algun ``docker-compose*.yml`` declara
   su contenedor con una imagen de ghcr.io.
4. **Su salud es verificable de verdad** -- la ruta que ``desplegar.sh``
   consulta es la que el servicio expone de verdad, derivada de su
   ``context-path``, y ``health`` esta entre los endpoints publicados.

El punto 4 es el que evita el fallo gemelo: un servicio SI desplegado que el
healthcheck da por caido porque pregunta en ``/actuator/health`` cuando el
servicio vive en ``/api/v1/actuator/health``. Le paso a ``ms-ecommerce`` y
esta a un paso de pasarle a cualquier servicio con ``context-path``.

No necesita Docker, ni red, ni credenciales: es analisis estatico del propio
repositorio. Corre siempre (ver docs/gobierno/GUARDIANES.md: un guardian
condicional es un guardian apagado).

Uso::

    python3 tests/contratos/servicios-reconocidos-por-el-cd.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
CD = RAIZ / ".github" / "workflows" / "cd.yml"
DESPLEGAR = RAIZ / "scripts" / "cd" / "desplegar.sh"

# Servicios que existen en el arbol pero que el flujo de despliegue NO debe
# conocer todavia. Cada entrada necesita un motivo comprobable: la idea es que
# esta lista solo pueda encoger. Si algo esta aqui "por ahora", el "por ahora"
# se escribe con su numero de issue.
NO_DESPLEGABLES: dict[str, str] = {}


def aviso(mensaje: str) -> None:
    print(f"::error::{mensaje}")


# --------------------------------------------------------------------------
# 1) Que servicios son desplegables de verdad
# --------------------------------------------------------------------------
def servicios_del_arbol() -> list[tuple[str, Path]]:
    """Carpetas con codigo de produccion Y Dockerfile propio.

    Las dos condiciones importan. Sin ``src/main`` es un esqueleto vacio y no
    hay nada que desplegar; sin ``Dockerfile`` no hay forma de empaquetarlo,
    asi que exigirle al CD que lo despliegue seria exigirle un imposible.
    """
    encontrados = []
    for dockerfile in sorted((RAIZ / "services").glob("*/*/Dockerfile")):
        carpeta = dockerfile.parent
        if not (carpeta / "src" / "main").is_dir():
            continue
        encontrados.append((carpeta.name, carpeta))
    return encontrados


# --------------------------------------------------------------------------
# 2) Que servicios conoce el CD, y en que puerto
# --------------------------------------------------------------------------
def puertos_que_conoce_el_cd(texto: str) -> dict[str, str]:
    """Une las dos formas que tiene cd.yml de registrar un servicio.

    a) ``puerto_de()``: un ``case`` con los servicios de plataforma/contenido.
    b) bloques propios: un ``jq`` con ``--arg servicio "X"`` y ``puerto:"N"``.

    Se leen las dos porque un servicio registrado de cualquiera de las dos
    formas esta igual de bien registrado -- lo que no vale es no estar.
    """
    puertos: dict[str, str] = {}

    bloque = re.search(r"puerto_de\(\)\s*\{(.*?)\n\s*\}", texto, re.S)
    if bloque:
        for servicio, puerto in re.findall(
            r"^\s*([a-z0-9-]+)\)\s*echo\s+(\d+)\s*;;", bloque.group(1), re.M
        ):
            puertos[servicio] = puerto

    # Los bloques propios reparten el jq en varias lineas; se busca el par
    # (servicio, puerto) dentro de una misma invocacion de jq.
    for invocacion in re.findall(r"MATRIZ=\$\(echo \"\$MATRIZ\" \| jq -c(.*?)\)\n", texto, re.S):
        servicio = re.search(r'--arg servicio "([a-z0-9-]+)"', invocacion)
        puerto = re.search(r'puerto:"(\d+)"', invocacion)
        if servicio and puerto:
            puertos[servicio.group(1)] = puerto.group(1)

    return puertos


# --------------------------------------------------------------------------
# 3) Configuracion real del servicio (la fuente de verdad, no los comentarios)
# --------------------------------------------------------------------------
def configuracion_del_servicio(carpeta: Path) -> dict[str, str]:
    """``server.port``, ``context-path`` y endpoints de actuator publicados.

    Se lee ``application.properties``/``application.yml`` -- nunca el
    comentario que dice lo que deberia poner. Confiar en el comentario en vez
    de en el archivo es exactamente el error que tuvo ``ms-ecommerce``.
    """
    config: dict[str, str] = {}
    recursos = carpeta / "src" / "main" / "resources"
    for nombre in ("application.properties", "application.yml", "application.yaml"):
        archivo = recursos / nombre
        if not archivo.is_file():
            continue
        texto = archivo.read_text(encoding="utf-8", errors="replace")
        for clave, patron in (
            ("puerto", r"^\s*server\.port\s*[:=]\s*(\S+)"),
            ("contexto", r"^\s*server\.servlet\.context-path\s*[:=]\s*(\S+)"),
            ("base_actuator", r"^\s*management\.endpoints\.web\.base-path\s*[:=]\s*(\S+)"),
            ("puerto_actuator", r"^\s*management\.server\.port\s*[:=]\s*(\S+)"),
            ("expuestos", r"^\s*management\.endpoints\.web\.exposure\.include\s*[:=]\s*(\S+)"),
        ):
            hallazgo = re.search(patron, texto, re.M)
            if hallazgo and clave not in config:
                config[clave] = hallazgo.group(1).strip()
    return config


def resolver_marcador(valor: str) -> str:
    """``${PUERTO_HEROES:8080}`` -> ``8080``.

    Varios servicios dejan el puerto configurable por entorno con un valor por
    omision. Comparar el literal contra el ``EXPOSE`` del Dockerfile daria un
    falso positivo en todos ellos: lo que hay que comparar es el valor que el
    contenedor usa de verdad cuando nadie pisa la variable, que es el
    predeterminado.
    """
    marcador = re.fullmatch(r"\$\{[A-Za-z0-9_]+:(-?)([^}]*)\}", valor.strip())
    if marcador:
        return marcador.group(2).strip()
    return valor.strip()


def salud_esperada(config: dict[str, str]) -> str:
    """La ruta donde el servicio publica su salud de verdad.

    Spring sirve actuator bajo el ``context-path`` del servlet mientras no se
    mueva a su propio puerto con ``management.server.port``. Esa es la regla
    que el healthcheck tiene que respetar.
    """
    if config.get("puerto_actuator"):
        # En su propio puerto, actuator queda fuera del context-path.
        return f"{config.get('base_actuator', '/actuator')}/health"
    contexto = config.get("contexto", "").rstrip("/")
    base = config.get("base_actuator", "/actuator")
    return f"{contexto}{base}/health"


def rutas_de_salud_del_script(texto: str) -> dict[str, str]:
    bloque = re.search(r"ruta_salud_de\(\)\s*\{(.*?)\n\s*\}", texto, re.S)
    if not bloque:
        return {}
    rutas = {}
    for servicio, ruta in re.findall(
        r'^\s*([a-z0-9-]+)\)\s*echo\s+"([^"]+)"\s*;;', bloque.group(1), re.M
    ):
        rutas[servicio] = ruta
    return rutas


# --------------------------------------------------------------------------
# 4) Descriptores de ejecucion
# --------------------------------------------------------------------------
def composes_por_servicio() -> dict[str, list[str]]:
    """Que ``docker-compose*.yml`` declara el contenedor de cada servicio.

    Se busca por el nombre de la imagen publicada, no por el del contenedor:
    el nombre del contenedor lo elige quien escribe el compose y cambia, el de
    la imagen lo fija el CD (``nexus-battles-vi-<servicio>``) y es el vinculo
    real entre lo que se publica y lo que se ejecuta.
    """
    mapa: dict[str, list[str]] = {}
    for archivo in sorted(RAIZ.glob("docker-compose*.yml")):
        texto = archivo.read_text(encoding="utf-8", errors="replace")
        for servicio in re.findall(r"nexus-battles-vi-([a-z0-9-]+):", texto):
            mapa.setdefault(servicio, []).append(archivo.name)
    return mapa


def overrides_que_llegan_al_servidor(texto_cd: str, texto_sh: str) -> tuple[set[str], set[str]]:
    """Que overrides usa ``desplegar.sh`` y cuales viajan de verdad por SCP.

    Un override que el script menciona pero que nadie copia al host hace que
    ``docker compose -f`` falle con «no such file», y solo en el momento del
    despliegue: nunca antes. Los archivos que el propio script genera en el
    servidor (``cat > ... <<EOF``, como el del simulacro de reversion) no
    cuentan -- no tienen que viajar porque nacen alli.
    """
    usados = set(re.findall(r"\$DIRECTORIO/(docker-compose[^\"']*\.yml)", texto_sh))
    generados = {
        nombre
        for nombre in usados
        if re.search(rf'cat > "\$\{{?\w+\}}?" <<', texto_sh)
        and re.search(rf'COMPOSE_\w+="\$DIRECTORIO/{re.escape(nombre)}"\s*\n\s*rm -f', texto_sh)
    }
    copiados: set[str] = set()
    for lista in re.findall(r'source: "([^"]+)"', texto_cd):
        copiados |= {x for x in lista.split(",") if x.startswith("docker-compose")}
    return usados - generados, copiados


def puerto_publicado(servicio: str, archivos: list[str]) -> tuple[str, str] | None:
    """El par ``"HOST:CONTENEDOR"`` con el que el compose publica el servicio."""
    for nombre in archivos:
        texto = (RAIZ / nombre).read_text(encoding="utf-8", errors="replace")
        bloque = re.search(
            rf"nexus-battles-vi-{re.escape(servicio)}:.*?(?=\n  [a-z]|\Z)", texto, re.S
        )
        if not bloque:
            continue
        publicado = re.search(r'-\s*"(\d+):(\d+)"', bloque.group(0))
        if publicado:
            return publicado.group(1), publicado.group(2)
    return None


# --------------------------------------------------------------------------
def main() -> int:
    for archivo in (CD, DESPLEGAR):
        if not archivo.is_file():
            aviso(f"no encuentro «{archivo.relative_to(RAIZ)}»")
            return 1

    texto_cd = CD.read_text(encoding="utf-8", errors="replace")
    texto_desplegar = DESPLEGAR.read_text(encoding="utf-8", errors="replace")

    conocidos = puertos_que_conoce_el_cd(texto_cd)
    rutas_salud = rutas_de_salud_del_script(texto_desplegar)
    composes = composes_por_servicio()
    servicios = servicios_del_arbol()

    if not servicios:
        aviso("no encontre ningun servicio desplegable: el descubrimiento esta roto")
        return 1

    fallos: list[str] = []
    print(f"Servicios desplegables encontrados en el arbol: {len(servicios)}\n")

    for nombre, carpeta in servicios:
        ruta = carpeta.relative_to(RAIZ).as_posix()
        if nombre in NO_DESPLEGABLES:
            print(f"  {nombre:20} OMITIDO -- {NO_DESPLEGABLES[nombre]}")
            continue

        config = configuracion_del_servicio(carpeta)
        problemas: list[str] = []

        # (1) El CD lo conoce.
        puerto_cd = conocidos.get(nombre)
        if puerto_cd is None:
            problemas.append(
                "el flujo de despliegue no lo nombra en ninguna parte: no se construye su "
                "imagen, no se le asigna etiqueta y nunca entra a la lista de despliegue. "
                "Se fusiona en verde y en DEV no existe. Hace falta un bloque en "
                "«detectar-servicios» de cd.yml (o una entrada en puerto_de) con su puerto"
            )

        # (2) Puerto coherente de punta a punta.
        archivos_compose = composes.get(nombre, [])
        if puerto_cd and archivos_compose:
            par = puerto_publicado(nombre, archivos_compose)
            if par and par[0] != puerto_cd:
                problemas.append(
                    f"cd.yml consulta el puerto {puerto_cd} del host pero su compose publica "
                    f"«{par[0]}:{par[1]}»: el healthcheck preguntaria en un puerto donde no "
                    "hay nadie y daria el despliegue por caido"
                )
        puerto_config = resolver_marcador(config.get("puerto", ""))
        expuesto = re.search(r"^EXPOSE\s+(\d+)", (carpeta / "Dockerfile").read_text(
            encoding="utf-8", errors="replace"), re.M)
        if puerto_config and expuesto and puerto_config != expuesto.group(1):
            problemas.append(
                f"su Dockerfile declara EXPOSE {expuesto.group(1)} pero server.port es "
                f"«{config.get('puerto')}» (valor efectivo {puerto_config}): el contenedor "
                "no escucha donde dice escuchar"
            )

        # (3) Descriptor de ejecucion.
        if puerto_cd and not archivos_compose:
            problemas.append(
                "ningun docker-compose*.yml declara su contenedor con la imagen "
                f"«nexus-battles-vi-{nombre}»: el CD publicaria la imagen y despues no "
                "tendria con que levantarla"
            )

        # (4) Salud verificable.
        if puerto_cd:
            esperada = salud_esperada(config)
            consultada = rutas_salud.get(nombre, rutas_salud.get("*", "/actuator/health"))
            if consultada != esperada:
                problemas.append(
                    f"desplegar.sh consulta «{consultada}» pero el servicio publica su salud "
                    f"en «{esperada}» (context-path «{config.get('contexto', '(ninguno)')}»): "
                    "el servicio arrancaria bien y el despliegue lo declararia caido. "
                    "Se corrige con una entrada en ruta_salud_de() de scripts/cd/desplegar.sh"
                )
            expuestos = config.get("expuestos", "")
            if expuestos and "health" not in expuestos and "*" not in expuestos:
                problemas.append(
                    "no publica «health» en management.endpoints.web.exposure.include "
                    f"(publica «{expuestos}»): la sonda recibiria 404 (regla 3 de plataforma)"
                )

        if problemas:
            print(f"  {nombre:20} FALLA  ({ruta})")
            for problema in problemas:
                print(f"      - {problema}")
                fallos.append(f"{nombre}: {problema}")
        else:
            destino = f"puerto {puerto_cd}" if puerto_cd else "sin puerto"
            print(f"  {nombre:20} ok     ({destino}, salud {salud_esperada(config)})")

    # (5) Todo override que el despliegue usa tiene que llegar al servidor.
    #     Es transversal, no por servicio: un override que no viaja rompe la
    #     corrida entera en la que se le necesita, no solo a su servicio.
    necesarios, copiados = overrides_que_llegan_al_servidor(texto_cd, texto_desplegar)
    for override in sorted(necesarios - copiados):
        problema = (
            f"«{override}» lo usa scripts/cd/desplegar.sh pero ninguna lista «source:» de "
            "cd.yml lo copia al servidor: «docker compose -f» fallaria con «no such file» "
            "en el momento del despliegue, nunca antes"
        )
        print(f"  {'(transversal)':20} FALLA")
        print(f"      - {problema}")
        fallos.append(f"cd.yml: {problema}")

    print()
    if fallos:
        aviso(
            f"{len(fallos)} problema(s) de despliegue en {len(set(f.split(':')[0] for f in fallos))} "
            "servicio(s): hay codigo fusionado que nunca llegaria a un entorno"
        )
        print(
            "\nEste guardian existe porque ya paso: ms-finanzas y ms-subastas llevaban\n"
            "semanas fusionados y en verde, devolviendo 502 en dev, y el diagnostico se\n"
            "hizo dos veces por separado sin relacionar los dos casos.\n"
        )
        return 1

    print(f"TODO OK -- los {len(servicios)} servicios desplegables son visibles para el CD.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
