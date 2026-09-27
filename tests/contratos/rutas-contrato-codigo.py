#!/usr/bin/env python3
"""
Guardian: contrato <-> codigo <-> clientes (B0, BACKEND-01).

`validar-contratos.py` comprueba que cada contrato sea OpenAPI valido y
`cambios-de-contrato.py` que un cambio incompatible abra version mayor. Ninguno
de los dos mira el codigo, y por eso pudo pasar, con todo el repositorio en
verde, la transferencia de subasta de #660/#669: un cliente llamaba a una ruta
que no existia y un controlador servia otra que ningun contrato declaraba. La
auditoria contractual de septiembre encontro tres casos mas del mismo tipo
(`GET /correos/envios`, `GET /cofres/mios`, `GET /admin/sistema/servicios`) y
tres rutas de contrato sin una linea de codigo detras (`productos.yaml`:
`suspender`, `reactivar`, `adquisiciones`).

Este guardian falla la integracion continua cuando:

  A. **un contrato declara una operacion que ningun controlador implementa**
     (metodo + ruta completa, con el `context-path` del servicio). Una
     operacion que todavia no existe PUEDE declararse si lleva
     `x-implementacion: pendiente` y `x-fase: <fase o issue>`: el contrato se
     congela primero (regla 1) sin mentir sobre lo que hay detras.
  B. **una operacion marcada `pendiente` ya esta implementada**: la marca
     caducada es tan falsa como la ruta inventada.
  C. **un controlador expone una ruta que ningun contrato de su servicio
     declara** (regla 1: contrato primero). Las excepciones razonadas viven en
     `mapa-contratos.yaml` > `excepciones.codigo` con su motivo.
  D. **un cliente llama a una ruta que no esta en ningun contrato**: literales
     de ruta en los clientes HTTP de Java, en las URL de `application.*` y de
     los compose, y en el frontend. Es el caso exacto de la transferencia.
  E. **un destino STOMP `/app/**` del AsyncAPI no tiene @MessageMapping /
     @SubscribeMapping, o al reves.**

Como se lee el codigo: sin compilar y sin arrancar nada. Se quitan los
comentarios, se resuelven las constantes `static final String` del propio
servicio y se leen las anotaciones de Spring. El monorepo usa una sola forma
de declarar rutas (anotacion con literal o constante, sin arreglos), asi que
no hace falta un analizador de Java completo; si alguien introduce una forma
que este lector no entiende, el efecto es una ruta que falta y el guardian se
pone rojo en vez de dejar pasar algo en silencio.

Uso:
    python3 tests/contratos/rutas-contrato-codigo.py [--detalle]
Salida distinta de cero si hay algun hallazgo.
"""

from __future__ import annotations

import collections
import glob
import os
import re
import sys

import yaml

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAPA = os.path.join(RAIZ, "tests", "contratos", "mapa-contratos.yaml")

METODOS_HTTP = {"get", "post", "put", "patch", "delete", "head", "options"}
IGNORAR_DIRECTORIOS = {"build", "target", "node_modules", ".gradle", ".git", "out", "bin", "dist"}

# Anotaciones de Spring MVC y su metodo HTTP.
MAPEOS_METODO = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
}

LITERAL = r'"(?:[^"\\\n]|\\.)*"'


# --------------------------------------------------------------------------
# Utilidades de texto
# --------------------------------------------------------------------------

def leer(ruta: str) -> str:
    with open(ruta, encoding="utf-8", errors="replace") as archivo:
        return archivo.read()


def quitar_comentarios_java(texto: str) -> str:
    """Quita `//` y `/* */` respetando cadenas, caracteres y bloques de texto."""
    salida: list[str] = []
    i, n = 0, len(texto)
    while i < n:
        if texto.startswith('"""', i):
            fin = texto.find('"""', i + 3)
            fin = n if fin < 0 else fin + 3
            salida.append(texto[i:fin])
            i = fin
            continue
        c = texto[i]
        if c == '"' or c == "'":
            j = i + 1
            while j < n and texto[j] != c and texto[j] != "\n":
                j += 2 if texto[j] == "\\" else 1
            salida.append(texto[i:j + 1])
            i = j + 1
            continue
        if texto.startswith("//", i):
            fin = texto.find("\n", i)
            i = n if fin < 0 else fin
            continue
        if texto.startswith("/*", i):
            fin = texto.find("*/", i + 2)
            i = n if fin < 0 else fin + 2
            salida.append(" ")
            continue
        salida.append(c)
        i += 1
    return "".join(salida)


def quitar_comentarios_js(texto: str) -> str:
    """Igual que en Java, con plantillas `...` y sin tocar las URL `http://`."""
    salida: list[str] = []
    i, n = 0, len(texto)
    while i < n:
        c = texto[i]
        if c in "\"'`":
            j = i + 1
            while j < n and texto[j] != c:
                if texto[j] == "\\":
                    j += 2
                    continue
                if c != "`" and texto[j] == "\n":
                    break
                j += 1
            salida.append(texto[i:j + 1])
            i = j + 1
            continue
        if texto.startswith("//", i):
            fin = texto.find("\n", i)
            i = n if fin < 0 else fin
            continue
        if texto.startswith("/*", i):
            fin = texto.find("*/", i + 2)
            i = n if fin < 0 else fin + 2
            salida.append(" ")
            continue
        salida.append(c)
        i += 1
    return "".join(salida)


def normalizar(ruta: str) -> str:
    """Forma comparable de una ruta: sin consulta, variables como `{}`."""
    ruta = ruta.split("?")[0].split("#")[0]
    ruta = re.sub(r"\$\{[^}]*\}", "{}", ruta)       # plantillas JS ${x}
    ruta = re.sub(r"\{[^{}]*\}", "{}", ruta)        # {id} y {id:regex}
    ruta = re.sub(r"%[sd]", "{}", ruta)             # String.format
    ruta = re.sub(r"/{2,}", "/", ruta)
    if len(ruta) > 1:
        ruta = ruta.rstrip("/")
    return ruta or "/"


def segmentos(ruta: str) -> list[str]:
    return [s for s in ruta.split("/") if s]


def coincide(plantilla_a: str, plantilla_b: str) -> bool:
    """Dos rutas normalizadas casan si cada segmento es igual o variable."""
    a, b = segmentos(plantilla_a), segmentos(plantilla_b)
    if len(a) != len(b):
        return False
    return all(x == y or x == "{}" or y == "{}" for x, y in zip(a, b))


def es_prefijo(prefijo: str, ruta: str) -> bool:
    a, b = segmentos(prefijo), segmentos(ruta)
    if len(a) > len(b):
        return False
    return all(x == y or x == "{}" or y == "{}" for x, y in zip(a, b))


def es_tramo(tramo: str, ruta: str) -> bool:
    """`tramo` aparece como secuencia contigua de segmentos dentro de `ruta`."""
    a, b = segmentos(tramo), segmentos(ruta)
    if not a or len(a) > len(b):
        return False
    for inicio in range(len(b) - len(a) + 1):
        if all(x == y or x == "{}" or y == "{}" for x, y in zip(a, b[inicio:inicio + len(a)])):
            return True
    return False


def siguiente_no_blanco(texto: str, posicion: int) -> str:
    while posicion < len(texto) and texto[posicion] in " \t\r\n":
        posicion += 1
    return texto[posicion] if posicion < len(texto) else ""


def plantilla_js(cuerpo: str) -> str:
    """`/api/v1/x/${id}` -> `/api/v1/x/{}` con llaves equilibradas."""
    salida, i = [], 0
    while i < len(cuerpo):
        if cuerpo.startswith("${", i):
            profundidad, j = 0, i + 1
            while j < len(cuerpo):
                if cuerpo[j] == "{":
                    profundidad += 1
                elif cuerpo[j] == "}":
                    profundidad -= 1
                    if profundidad == 0:
                        break
                j += 1
            salida.append("{}")
            i = j + 1
            continue
        salida.append(cuerpo[i])
        i += 1
    return "".join(salida)


def archivos(base: str, extensiones: tuple[str, ...]) -> list[str]:
    encontrados: list[str] = []
    for carpeta, subcarpetas, nombres in os.walk(base):
        subcarpetas[:] = [s for s in subcarpetas if s not in IGNORAR_DIRECTORIOS]
        for nombre in nombres:
            if nombre.endswith(extensiones):
                encontrados.append(os.path.join(carpeta, nombre))
    return sorted(encontrados)


def relativo(ruta: str) -> str:
    return os.path.relpath(ruta, RAIZ).replace(os.sep, "/")


# --------------------------------------------------------------------------
# Lectura de un servicio Spring
# --------------------------------------------------------------------------

class Servicio:
    def __init__(self, carpeta: str):
        self.carpeta = carpeta
        self.java = [f for f in archivos(os.path.join(RAIZ, carpeta, "src", "main", "java"), (".java",))]
        self.textos = {f: quitar_comentarios_java(leer(f)) for f in self.java}
        self.contexto = self._context_path()
        self.constantes = self._constantes()
        self.rutas: dict[tuple[str, str], str] = {}      # (METODO, ruta) -> archivo
        self.destinos_stomp: dict[str, str] = {}          # /app/... -> archivo
        self.prefijo_app = self._prefijo_app()
        self._leer_controladores()

    # -- propiedades -------------------------------------------------------
    def _context_path(self) -> str:
        recursos = os.path.join(RAIZ, self.carpeta, "src", "main", "resources")
        for nombre in ("application.properties", "application.yml", "application.yaml"):
            ruta = os.path.join(recursos, nombre)
            if not os.path.exists(ruta):
                continue
            texto = leer(ruta)
            m = re.search(r"^\s*server\.servlet\.context-path\s*[=:]\s*(\S+)", texto, re.M)
            if m:
                return m.group(1).strip().strip("'\"")
            m = re.search(r"servlet:\s*\n\s+context-path:\s*(\S+)", texto)
            if m:
                return m.group(1).strip().strip("'\"")
        return ""

    def _constantes(self) -> dict[str, str]:
        crudas: dict[str, str] = {}
        patron = re.compile(
            r"(?:static\s+final|final\s+static)\s+String\s+(\w+)\s*=\s*([^;]+);")
        for texto in self.textos.values():
            for nombre, expresion in patron.findall(texto):
                crudas[nombre] = expresion.strip()
        resueltas: dict[str, str] = {}
        for _ in range(5):  # constantes que dependen de otras constantes
            for nombre, expresion in crudas.items():
                valor = self.evaluar(expresion, resueltas)
                if valor is not None:
                    resueltas[nombre] = valor
        return resueltas

    @staticmethod
    def evaluar(expresion: str, constantes: dict[str, str]) -> str | None:
        """Concatenacion de literales y constantes; None si no se puede."""
        partes = [p.strip() for p in re.split(r"\s*\+\s*", expresion.strip())]
        valor = ""
        for parte in partes:
            if re.fullmatch(LITERAL, parte):
                valor += bytes(parte[1:-1], "utf-8").decode("unicode_escape")
            else:
                nombre = parte.split(".")[-1]
                if nombre in constantes:
                    valor += constantes[nombre]
                else:
                    return None
        return valor

    def _prefijo_app(self) -> str:
        for texto in self.textos.values():
            m = re.search(r'setApplicationDestinationPrefixes\(\s*"([^"]+)"', texto)
            if m:
                return m.group(1)
        return "/app"

    # -- anotaciones -------------------------------------------------------
    @staticmethod
    def argumentos(texto: str, desde: int) -> str | None:
        """Contenido entre parentesis equilibrados a partir de `desde`."""
        i = desde
        while i < len(texto) and texto[i] in " \t\r\n":
            i += 1
        if i >= len(texto) or texto[i] != "(":
            return None
        profundidad, j = 0, i
        while j < len(texto):
            c = texto[j]
            if c == '"':
                j += 1
                while j < len(texto) and texto[j] != '"':
                    j += 2 if texto[j] == "\\" else 1
            elif c == "(":
                profundidad += 1
            elif c == ")":
                profundidad -= 1
                if profundidad == 0:
                    return texto[i + 1:j]
            j += 1
        return None

    @staticmethod
    def partes_de_nivel_cero(contenido: str) -> list[str]:
        partes, actual, profundidad, i = [], [], 0, 0
        while i < len(contenido):
            c = contenido[i]
            if c == '"':
                j = i + 1
                while j < len(contenido) and contenido[j] != '"':
                    j += 2 if contenido[j] == "\\" else 1
                actual.append(contenido[i:j + 1])
                i = j + 1
                continue
            if c in "({[":
                profundidad += 1
            elif c in ")}]":
                profundidad -= 1
            if c == "," and profundidad == 0:
                partes.append("".join(actual).strip())
                actual = []
            else:
                actual.append(c)
            i += 1
        if "".join(actual).strip():
            partes.append("".join(actual).strip())
        return partes

    def rutas_de_anotacion(self, contenido: str | None, archivo: str) -> tuple[list[str], list[str]]:
        """(rutas, metodos) declarados en los argumentos de una anotacion."""
        if contenido is None or not contenido.strip():
            return [""], []
        rutas: list[str] = []
        metodos: list[str] = []
        partes = self.partes_de_nivel_cero(contenido)
        for indice, parte in enumerate(partes):
            m = re.match(r"^(value|path)\s*=\s*(.+)$", parte, re.S)
            if m:
                expresiones = [m.group(2)]
            elif re.match(r"^method\s*=", parte):
                metodos += re.findall(r"RequestMethod\.(\w+)", parte)
                continue
            elif "=" in parte.split('"')[0]:
                continue  # produces =, consumes =, params =, ...
            elif indice == 0:
                expresiones = [parte]
            else:
                continue
            for expresion in expresiones:
                expresion = expresion.strip()
                if expresion.startswith("{") and expresion.endswith("}"):
                    elementos = self.partes_de_nivel_cero(expresion[1:-1])
                else:
                    elementos = [expresion]
                for elemento in elementos:
                    valor = self.evaluar(elemento, self.constantes)
                    if valor is None:
                        print(f"::error file={relativo(archivo)}::no se pudo resolver la ruta «{elemento}»")
                        FALLOS.append(f"ruta no resoluble en {relativo(archivo)}: {elemento}")
                    else:
                        rutas.append(valor)
        return (rutas or [""]), metodos

    def _leer_controladores(self) -> None:
        for archivo, texto in self.textos.items():
            es_rest = re.search(r"@RestController\b|@Controller\b", texto)
            if not es_rest:
                continue
            clase = re.search(r"\b(class|record|interface)\s+\w+", texto)
            inicio_clase = clase.start() if clase else 0

            prefijos_http, prefijos_stomp = [""], [""]
            for m in re.finditer(r"@RequestMapping\b", texto[:inicio_clase]):
                prefijos_http, _ = self.rutas_de_anotacion(self.argumentos(texto, m.end()), archivo)
            for m in re.finditer(r"@MessageMapping\b", texto[:inicio_clase]):
                prefijos_stomp, _ = self.rutas_de_anotacion(self.argumentos(texto, m.end()), archivo)

            cuerpo = texto[inicio_clase:]
            for m in re.finditer(r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\b", cuerpo):
                anotacion = m.group(1)
                rutas, metodos = self.rutas_de_anotacion(self.argumentos(cuerpo, m.end()), archivo)
                if anotacion != "RequestMapping":
                    metodos = [MAPEOS_METODO[anotacion]]
                elif not metodos:
                    metodos = ["GET", "POST", "PUT", "PATCH", "DELETE"]
                for prefijo in prefijos_http:
                    for ruta in rutas:
                        completa = normalizar(self.contexto + "/" + prefijo + "/" + ruta)
                        for metodo in metodos:
                            self.rutas[(metodo.upper(), completa)] = archivo

            if es_rest.group(0) == "@Controller" or "@MessageMapping" in cuerpo or "@SubscribeMapping" in cuerpo:
                for m in re.finditer(r"@(MessageMapping|SubscribeMapping)\b", cuerpo):
                    rutas, _ = self.rutas_de_anotacion(self.argumentos(cuerpo, m.end()), archivo)
                    for prefijo in prefijos_stomp:
                        for ruta in rutas:
                            destino = normalizar(self.prefijo_app + "/" + prefijo + "/" + ruta)
                            self.destinos_stomp[destino] = archivo


# --------------------------------------------------------------------------
# Contratos
# --------------------------------------------------------------------------

Operacion = collections.namedtuple("Operacion", "contrato metodo ruta relativa pendiente fase servicio")


def operaciones_openapi(ruta: str, servicio: str) -> list[Operacion]:
    documento = yaml.safe_load(leer(ruta)) or {}
    servidores = documento.get("servers") or [{}]
    prefijo = str((servidores[0] or {}).get("url", ""))
    prefijo = re.sub(r"^[a-z]+://[^/]+", "", prefijo)
    resultado = []
    for camino, operaciones in (documento.get("paths") or {}).items():
        if not isinstance(operaciones, dict):
            continue
        for metodo, operacion in operaciones.items():
            if metodo not in METODOS_HTTP or not isinstance(operacion, dict):
                continue
            pendiente = str(operacion.get("x-implementacion", "")).strip().lower() == "pendiente"
            resultado.append(Operacion(
                contrato=relativo(ruta),
                metodo=metodo.upper(),
                ruta=normalizar(prefijo + "/" + camino),
                relativa=normalizar(camino),
                pendiente=pendiente,
                fase=str(operacion.get("x-fase", "")),
                servicio=servicio,
            ))
    return resultado


def destinos_asyncapi(ruta: str, prefijo_app: str) -> dict[str, bool]:
    """Destinos de aplicacion (`/app/**`) del documento -> pendiente?"""
    documento = yaml.safe_load(leer(ruta)) or {}
    destinos: dict[str, bool] = {}
    for canal in (documento.get("channels") or {}).values():
        if not isinstance(canal, dict):
            continue
        direccion = str(canal.get("address") or "")
        if direccion.startswith(prefijo_app + "/"):
            pendiente = str(canal.get("x-implementacion", "")).strip().lower() == "pendiente"
            destinos[normalizar(direccion)] = pendiente
    return destinos


# --------------------------------------------------------------------------
# Clientes
# --------------------------------------------------------------------------

RUTA_API = re.compile(r"^(?:/ecommerce)?/api/v1(?:/|$)|^/internal/")
BASES = {"/api/v1", "/ecommerce/api/v1", "/ecommerce", "/internal"}


def literales_java_cliente(servicio: Servicio) -> list[tuple[str, str, str]]:
    """(literal, archivo, modo) de rutas que un servicio llama.

    modo: `exacta` (la ruta entera), `prefijo` (va seguida de `+` o acaba en
    `/`) o `tramo` (un fragmento relativo a una URL base, como `/liberar` en
    `base + "/reservas/" + id + "/liberar"`: tiene que aparecer como
    segmentos contiguos de alguna ruta de contrato).
    """
    encontrados = []
    for archivo, texto in servicio.textos.items():
        if re.search(r"@RestController\b|@Controller\b|HttpSecurity|SecurityFilterChain|"
                     r"WebSocketMessageBrokerConfigurer|ChannelInterceptor|OncePerRequestFilter|"
                     r"@WebMvcTest|HandlerInterceptor|WebMvcConfigurer", texto):
            continue
        es_cliente = re.search(r"RestClient|WebClient|RestTemplate|HttpClient|HttpRequest|UriComponentsBuilder", texto)
        for m in re.finditer(LITERAL, texto):
            valor = m.group(0)[1:-1]
            if "*" in valor or " " in valor or "/actuator" in valor:
                continue
            if re.match(r"^/(metrics|health|info|prometheus)(/|$)", valor):
                continue  # subrutas de Actuator: infraestructura, no contrato de negocio
            concatenado = siguiente_no_blanco(texto, m.end()) == "+"
            if RUTA_API.match(valor):
                if normalizar(valor) in BASES:
                    continue
                modo = "prefijo" if (concatenado or valor.endswith("/")) else "exacta"
                encontrados.append((valor, archivo, modo))
            elif es_cliente and re.fullmatch(r"/[a-z][a-z0-9\-_]*(?:/[a-zA-Z0-9\-_{}%.]+)*/?", valor):
                encontrados.append((valor, archivo, "tramo"))
    return encontrados


def literales_configuracion() -> list[tuple[str, str, str]]:
    """Rutas dentro de URL de application.* y de los compose (base o completa)."""
    candidatos = glob.glob(os.path.join(RAIZ, "docker-compose*.yml"))
    candidatos += glob.glob(os.path.join(RAIZ, "tests", "e2e", "compose.yml"))
    candidatos += glob.glob(os.path.join(RAIZ, "infrastructure", "**", "*.yml"), recursive=True)
    candidatos += glob.glob(os.path.join(RAIZ, "services", "*", "*", "src", "main", "resources", "application*"))
    encontrados = []
    for archivo in sorted(set(candidatos)):
        texto = leer(archivo)
        for m in re.finditer(r"https?://[^/\s\"'$}]+(/[^\s\"'$}#?]*)", texto):
            camino = m.group(1)
            if "/actuator" in camino or not RUTA_API.match(camino) or normalizar(camino) in BASES:
                continue
            encontrados.append((camino, archivo, "prefijo"))
    return encontrados


def literales_frontend() -> list[tuple[str, str, str]]:
    base = os.path.join(RAIZ, "frontend", "app-web", "src")
    encontrados = []
    for archivo in archivos(base, (".js",)):
        if archivo.endswith((".test.js", ".spec.js")):
            continue
        texto = quitar_comentarios_js(leer(archivo))
        i = 0
        while i < len(texto):
            c = texto[i]
            if c not in "\"'`":
                i += 1
                continue
            j = i + 1
            while j < len(texto) and texto[j] != c:
                if texto[j] == "\\":
                    j += 2
                    continue
                if c == "`" and texto.startswith("${", j):
                    profundidad = 0
                    while j < len(texto):
                        if texto[j] == "{":
                            profundidad += 1
                        elif texto[j] == "}":
                            profundidad -= 1
                            if profundidad == 0:
                                break
                        j += 1
                j += 1
            cuerpo = texto[i + 1:j]
            siguiente = siguiente_no_blanco(texto, j + 1)
            i = j + 1
            if c == "`":
                cuerpo = plantilla_js(cuerpo)
                # `${consulta}` pegado a un segmento es la cadena de consulta, no una variable de ruta.
                cuerpo = re.sub(r"(?<=[^/]){}$", "", cuerpo)
            if not re.match(r"^(?:/ecommerce)?/api/v1/", cuerpo) or "*" in cuerpo or "/actuator" in cuerpo:
                continue
            # En el frontend una ruta suele ser la base de otras (`const BASE = '/api/v1/x'`
            # y luego `${BASE}/${id}`): basta con que sea el principio de una ruta de contrato.
            encontrados.append((cuerpo, archivo, "prefijo"))
    return encontrados


# --------------------------------------------------------------------------
# Programa
# --------------------------------------------------------------------------

FALLOS: list[str] = []
PENDIENTES: list[str] = []


def fallo(mensaje: str, archivo: str | None = None) -> None:
    if archivo:
        print(f"::error file={archivo}::{mensaje}")
    else:
        print(f"::error::{mensaje}")
    FALLOS.append(mensaje)


def main() -> int:
    detalle = "--detalle" in sys.argv
    mapa = yaml.safe_load(leer(MAPA)) or {}
    mapa_openapi: dict[str, str] = mapa.get("openapi") or {}
    mapa_asyncapi: dict[str, str] = mapa.get("asyncapi") or {}
    excepciones = mapa.get("excepciones") or {}
    exc_codigo = {(e["metodo"].upper(), normalizar(e["ruta"])) for e in excepciones.get("codigo") or []}
    exc_clientes = {normalizar(e["ruta"]) for e in excepciones.get("clientes") or []}

    # Todo contrato debe tener dueno en el mapa, y todo dueno debe existir.
    for ruta in sorted(glob.glob(os.path.join(RAIZ, "contracts", "openapi", "*.yaml"))):
        if os.path.basename(ruta) not in mapa_openapi:
            fallo(f"{relativo(ruta)} no tiene servicio en mapa-contratos.yaml", relativo(ruta))
    for ruta in sorted(glob.glob(os.path.join(RAIZ, "contracts", "websocket", "*.yaml"))):
        if os.path.basename(ruta) not in mapa_asyncapi:
            fallo(f"{relativo(ruta)} no tiene servicio en mapa-contratos.yaml", relativo(ruta))

    servicios: dict[str, Servicio] = {}
    for carpeta in sorted(set(mapa_openapi.values()) | set(mapa_asyncapi.values())):
        if not os.path.isdir(os.path.join(RAIZ, carpeta, "src", "main", "java")):
            fallo(f"el servicio {carpeta} del mapa no tiene codigo")
            continue
        servicios[carpeta] = Servicio(carpeta)

    operaciones: list[Operacion] = []
    for nombre, carpeta in mapa_openapi.items():
        ruta = os.path.join(RAIZ, "contracts", "openapi", nombre)
        if not os.path.exists(ruta):
            fallo(f"mapa-contratos.yaml menciona {nombre}, que no existe")
            continue
        operaciones += operaciones_openapi(ruta, carpeta)

    # A y B: cada operacion de contrato contra el codigo de su servicio.
    implementadas = 0
    for op in operaciones:
        servicio = servicios.get(op.servicio)
        if servicio is None:
            continue
        # Igualdad estructural: literal con literal y variable con variable.
        # `/perfiles/publicos` NO lo implementa `/perfiles/{usuario}`.
        existe = (op.metodo, op.ruta) in servicio.rutas
        if op.pendiente:
            if existe:
                fallo(f"{op.metodo} {op.ruta} esta marcada x-implementacion: pendiente pero ya existe en {op.servicio}: quita la marca", op.contrato)
            else:
                PENDIENTES.append(f"{op.metodo} {op.ruta}  ({op.contrato}, fase {op.fase or '?'})")
            continue
        if not existe:
            fallo(f"{op.metodo} {op.ruta} esta en el contrato pero {op.servicio} no la implementa", op.contrato)
        else:
            implementadas += 1

    # C: cada ruta de controlador contra los contratos de SU servicio.
    por_servicio = collections.defaultdict(list)
    for op in operaciones:
        por_servicio[op.servicio].append(op)
    for carpeta, servicio in servicios.items():
        for (metodo, ruta), archivo in sorted(servicio.rutas.items()):
            if (metodo, ruta) in exc_codigo:
                continue
            declarada = any(op.metodo == metodo and op.ruta == ruta for op in por_servicio.get(carpeta, []))
            if not declarada:
                fallo(f"{metodo} {ruta} la expone {relativo(archivo)} y ningun contrato de {carpeta} la declara", relativo(archivo))

    # D: clientes contra el conjunto de contratos.
    completas = [op.ruta for op in operaciones]
    relativas = [op.relativa for op in operaciones]
    sin_contexto = [re.sub(r"^/ecommerce(?=/)", "", r) for r in completas]
    universo = set(completas) | set(relativas) | set(sin_contexto)

    def conocida(literal: str, modo: str) -> bool:
        normal = normalizar(literal)
        if normal in exc_clientes:
            return True
        for candidata in universo:
            if coincide(normal, candidata):
                return True
            if modo == "prefijo" and es_prefijo(normal, candidata):
                return True
            if modo == "tramo" and es_tramo(normal, candidata):
                return True
        return False

    clientes = []
    for servicio in servicios.values():
        clientes += literales_java_cliente(servicio)
    # Servicios que llaman a otros y no son duenos de ningun contrato tambien cuentan.
    for carpeta in sorted(glob.glob(os.path.join(RAIZ, "services", "*", "*"))):
        relativa = relativo(carpeta)
        if relativa not in servicios and os.path.isdir(os.path.join(carpeta, "src", "main", "java")):
            clientes += literales_java_cliente(Servicio(relativa))
    clientes += literales_configuracion()
    clientes += literales_frontend()

    vistos = set()
    for literal, archivo, modo in clientes:
        clave = (normalizar(literal), relativo(archivo), modo)
        if clave in vistos:
            continue
        vistos.add(clave)
        if not conocida(literal, modo):
            fallo(f"el cliente llama a «{literal}» ({modo}) y ningun contrato la declara", relativo(archivo))

    # E: STOMP /app/** contra AsyncAPI. Un servicio puede tener varios
    # documentos (salas-partidas: salas y chat, y mensajes privados), asi que
    # los destinos se juntan por servicio antes de comparar con el codigo.
    destinos_por_servicio: dict[str, dict[str, tuple[bool, str]]] = collections.defaultdict(dict)
    for nombre, carpeta in mapa_asyncapi.items():
        ruta = os.path.join(RAIZ, "contracts", "websocket", nombre)
        servicio = servicios.get(carpeta)
        if servicio is None or not os.path.exists(ruta):
            continue
        for destino, pendiente in destinos_asyncapi(ruta, servicio.prefijo_app).items():
            destinos_por_servicio[carpeta][destino] = (pendiente, relativo(ruta))
    for carpeta, destinos in destinos_por_servicio.items():
        servicio = servicios[carpeta]
        for destino, (pendiente, documento) in destinos.items():
            existe = destino in servicio.destinos_stomp
            if pendiente and existe:
                fallo(f"el destino {destino} esta pendiente en {documento} pero ya existe: quita la marca", documento)
            elif pendiente:
                PENDIENTES.append(f"STOMP {destino}  ({documento})")
            elif not existe:
                fallo(f"el destino {destino} esta en {documento} y {carpeta} no lo atiende", documento)
        for destino, archivo in servicio.destinos_stomp.items():
            if destino not in destinos:
                fallo(f"el destino STOMP {destino} lo atiende {relativo(archivo)} y ningun AsyncAPI de {carpeta} lo declara", relativo(archivo))

    total_rutas = sum(len(s.rutas) for s in servicios.values())
    print(f"\n{len(operaciones)} operaciones de contrato, {implementadas} implementadas, "
          f"{len(PENDIENTES)} pendientes declaradas; {total_rutas} rutas en {len(servicios)} servicios; "
          f"{len(vistos)} llamadas de cliente revisadas.")
    if PENDIENTES:
        print("\nPendientes declaradas (x-implementacion: pendiente):")
        for linea in PENDIENTES:
            print(f"  - {linea}")
    if detalle:
        for carpeta, servicio in servicios.items():
            print(f"\n{carpeta} (context-path «{servicio.contexto}»)")
            for (metodo, ruta) in sorted(servicio.rutas):
                print(f"  {metodo:6} {ruta}")
            for destino in sorted(servicio.destinos_stomp):
                print(f"  STOMP  {destino}")
    if FALLOS:
        print(f"\n{len(FALLOS)} hallazgo(s): contrato, codigo y clientes no cuentan la misma historia.")
        return 1
    print("\nContrato, codigo y clientes coinciden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
