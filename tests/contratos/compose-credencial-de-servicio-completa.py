#!/usr/bin/env python3
"""Un compose que pide credencial de servicio trae las TRES variables.

## Por que existe este guardian

`CredencialesDeServicioAutoConfiguration` (shared/libs/plataforma-seguridad) se
activa en cuanto `DIRECTORIO_ACTIVO_CLIENT_ID` no esta vacio:

    @ConditionalOnExpression(
        "!'${seguridad.servicio.client-id:${DIRECTORIO_ACTIVO_CLIENT_ID:}}'.isBlank()")

Y una vez activa EXIGE la URL del emisor. Si falta, el servicio no arranca:

    Falta la URL del emisor (DIRECTORIO_ACTIVO_URL):
    sin eso el servicio no puede identificarse.

Los servicios de plataforma no lo notan: el compose base les da `env_file: .env`
y `desplegar.sh` escribe ahi `DIRECTORIO_ACTIVO_URL`. Los de Cuentas **no estan
en el compose base**, asi que solo tienen lo que declare su propio override. El
23-sep `ms-finanzas` se desplego con CLIENT_ID y CLIENT_SECRET pero sin URL y
murio en el arranque, despues de construir la imagen, levantar su Postgres y
correr Flyway correctamente (corrida 35939650016). `ms-subastas` tenia el mismo
agujero, sin haber llegado a intentarlo todavia.

El fallo es especialmente caro porque todo lo demas sale bien: la imagen se
publica, la base migra, y solo al final el healthcheck no responde. Desde el
log del CD parece un problema de arranque lento o de memoria.

## Que comprueba

Para cada servicio de cada compose: si declara `DIRECTORIO_ACTIVO_CLIENT_ID`,
tiene que declarar tambien `DIRECTORIO_ACTIVO_URL` y `DIRECTORIO_ACTIVO_CLIENT_SECRET`,
**o** traer `env_file`, que es de donde la heredan los de plataforma.
"""

import pathlib
import re
import sys

import yaml

CLIENT_ID = "DIRECTORIO_ACTIVO_CLIENT_ID"
CLIENT_SECRET = "DIRECTORIO_ACTIVO_CLIENT_SECRET"
URL = "DIRECTORIO_ACTIVO_URL"

INTERPOLACION = re.compile(r"\$\{[^}]*\}")


def _entorno(servicio):
    """El bloque environment: de un servicio, como diccionario.

    Compose acepta mapa o lista de "CLAVE=valor"; aqui interesan solo los
    nombres, asi que se normalizan los dos casos.
    """
    entorno = servicio.get("environment")
    if entorno is None:
        return {}
    if isinstance(entorno, dict):
        return entorno
    return {linea.split("=", 1)[0]: None for linea in entorno if isinstance(linea, str)}


def _cargar(archivo):
    texto = INTERPOLACION.sub("INTERPOLADO", archivo.read_text(encoding="utf-8"))
    return yaml.safe_load(texto) or {}


def main() -> int:
    raiz = pathlib.Path(__file__).resolve().parents[2]
    fallos = 0
    revisados = 0

    # El despliegue combina SIEMPRE docker-compose.yml con los overrides, y
    # Compose fusiona servicio por servicio. Un servicio definido tambien en el
    # base hereda su `env_file`, aunque el override no lo repita: comprobar
    # cada archivo por separado daria falsos positivos con salas-partidas y
    # notificaciones, que viven en los dos.
    base = _cargar(raiz / "docker-compose.yml")
    con_env_file_en_base = {
        nombre
        for nombre, servicio in (base.get("services") or {}).items()
        if isinstance(servicio, dict) and servicio.get("env_file")
    }

    for archivo in sorted(raiz.glob("docker-compose*.yml")):
        documento = _cargar(archivo)
        relativa = archivo.relative_to(raiz)

        for nombre, servicio in (documento.get("services") or {}).items():
            if not isinstance(servicio, dict):
                continue
            entorno = _entorno(servicio)
            if CLIENT_ID not in entorno:
                continue

            revisados += 1
            # Quien trae env_file -aqui o en el compose base- hereda del .env
            # que escribe desplegar.sh.
            if servicio.get("env_file") or nombre in con_env_file_en_base:
                print(f"  {relativa}: {nombre} pide credencial y hereda del env_file")
                continue

            faltan = [v for v in (URL, CLIENT_SECRET) if v not in entorno]
            if faltan:
                fallos += 1
                print(
                    f"::error file={relativa}::{nombre} declara {CLIENT_ID} pero le "
                    f"falta {', '.join(faltan)}. Sin eso el servicio no arranca."
                )
                print(f"  {relativa}: {nombre} -> FALTA {', '.join(faltan)}")
            else:
                print(f"  {relativa}: {nombre} pide credencial y la trae completa")

    if fallos:
        print()
        print(
            "::error::La autoconfiguracion de credenciales se activa con "
            "CLIENT_ID y exige la URL del emisor. Ver la cabecera de este guardian."
        )
        return 1

    print(f"\n{revisados} servicios piden credencial de servicio; ninguno incompleto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
