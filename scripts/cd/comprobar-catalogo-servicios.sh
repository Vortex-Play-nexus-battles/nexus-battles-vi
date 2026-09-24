#!/usr/bin/env bash
# Guardian del catalogo de despliegue (D14).
#
# Por que existe: ms-finanzas y ms-subastas tenian codigo completo, pruebas en
# verde y una ruta reservada en el borde, pero el flujo de despliegue no sabia
# que existian -- la regex de cd.yml solo miraba services/(plataforma|
# contenido)/*, y services/cuentas/* dependia de bloques escritos a mano. El
# resultado fue un 502 en dev durante semanas para HU que el equipo daba por
# terminadas (HU-PAG-002 #536, HU-JUE-012 #470, issue #571).
#
# Este script falla el pull request cuando vuelve a pasar. No revisa codigo:
# compara el catalogo (infrastructure/despliegue/servicios.json) con lo que hay
# de verdad en el repositorio.
#
# Se puede correr en local: bash scripts/cd/comprobar-catalogo-servicios.sh
set -uo pipefail

CATALOGO="infrastructure/despliegue/servicios.json"
FALLOS=0

fallo() {
  FALLOS=$((FALLOS + 1))
  echo "::error::$1"
  shift
  for l in "$@"; do echo "    $l"; done
}

command -v jq >/dev/null 2>&1 || { echo "::error::hace falta jq"; exit 1; }
[ -f "$CATALOGO" ] || { echo "::error::no existe $CATALOGO"; exit 1; }
jq empty "$CATALOGO" 2>/dev/null || { echo "::error::$CATALOGO no es JSON valido"; exit 1; }

echo "== 1) Todo servicio desplegable esta en el catalogo =="
# "Desplegable" = tiene con que construirse (build.gradle o pom.xml), tiene
# Dockerfile y tiene codigo. Un servicio que solo es una carpeta con README no
# cuenta: todavia no hay nada que desplegar.
for dir in services/*/*/; do
  dir=${dir%/}
  [ -f "$dir/Dockerfile" ] || continue
  [ -d "$dir/src/main/java" ] || continue
  { [ -f "$dir/build.gradle" ] || [ -f "$dir/pom.xml" ]; } || continue

  if ! jq -e --arg r "$dir" '.servicios[] | select(.ruta == $r)' "$CATALOGO" >/dev/null; then
    fallo "$dir se puede construir y desplegar, pero no esta en $CATALOGO." \
      "Nadie va a construir su imagen ni a desplegarla: va a dar 502 en dev" \
      "por mas que su CI este en verde. Esto es lo que le paso a ms-finanzas" \
      "y a ms-subastas (#571)." \
      "" \
      "Como se arregla: agrega una entrada con nombre, ruta, herramienta," \
      "puertoHost, puertoContenedor, rutaSalud, claseHost, composeExtra," \
      "desplegableDev y secretsRequeridos."
  fi
done
[ "$FALLOS" -eq 0 ] && echo "OK: ningun servicio desplegable falta del catalogo."

echo
echo "== 2) Lo que dice el catalogo coincide con el servicio real =="
while IFS=$'\t' read -r nombre ruta herramienta puertoContenedor rutaSalud composeExtra memLimit; do
  [ -d "$ruta" ] || { fallo "$nombre: la ruta $ruta no existe."; continue; }

  # a) herramienta declarada vs la que hay
  if [ "$herramienta" = "gradle" ] && [ ! -f "$ruta/build.gradle" ]; then
    fallo "$nombre: el catalogo dice gradle pero no hay $ruta/build.gradle."
  fi
  if [ "$herramienta" = "maven" ] && [ ! -f "$ruta/pom.xml" ]; then
    fallo "$nombre: el catalogo dice maven pero no hay $ruta/pom.xml."
  fi

  # b) puerto del contenedor vs EXPOSE del Dockerfile
  expuesto=$(grep -hE '^EXPOSE ' "$ruta/Dockerfile" 2>/dev/null | head -1 | awk '{print $2}')
  if [ -n "$expuesto" ] && [ "$expuesto" != "$puertoContenedor" ]; then
    fallo "$nombre: el catalogo dice puertoContenedor=$puertoContenedor y el Dockerfile expone $expuesto." \
      "Con un puerto equivocado el contenedor arranca y el healthcheck lo da" \
      "por muerto (le paso a ms-identidad cuando movio 8081 -> 8089)."
  fi

  # c) ruta de salud vs context-path real. Es el bug que ya ocurrio dos veces
  #    por copiar la ruta de un comentario en vez de mirar la configuracion.
  ctx=$(grep -hE '^server\.servlet\.context-path=' "$ruta"/src/main/resources/application.properties 2>/dev/null | head -1 | cut -d= -f2)
  if [ -z "$ctx" ]; then
    ctx=$(grep -hA2 -E '^ *servlet:' "$ruta"/src/main/resources/application.y*ml 2>/dev/null | grep -E 'context-path:' | head -1 | sed 's/.*context-path: *//')
  fi
  esperada="${ctx}/actuator/health"
  if [ "$rutaSalud" != "$esperada" ]; then
    fallo "$nombre: el catalogo dice rutaSalud=$rutaSalud y su configuracion implica $esperada." \
      "context-path leido de la configuracion: '${ctx:-(ninguno)}'." \
      "No copies la ruta de un comentario: miralo en application.properties."
  fi

  # c2) el techo de memoria declarado en el catalogo es el que aplica el
  #     compose. Si divergen, las decisiones de capacidad se toman con un
  #     numero y el host ejecuta otro -- y el JVM se dimensiona con el del
  #     compose (MaxRAMPercentage), no con el del catalogo.
  if [ "$composeExtra" != "null" ] && [ -n "$composeExtra" ] && [ -f "$composeExtra" ] && [ -n "$memLimit" ] && [ "$memLimit" != "null" ]; then
    enCompose=$(grep -A4 -E "^ *srv-${nombre}:" "$composeExtra" | grep -oE 'mem_limit: [0-9]+m' | head -1 | grep -oE '[0-9]+')
    if [ -n "$enCompose" ] && [ "$enCompose" != "$memLimit" ]; then
      fallo "$nombre: el catalogo dice memLimitMiB=$memLimit y $composeExtra aplica ${enCompose}m." \
        "La capacidad se decide con el numero del catalogo y el host ejecuta el" \
        "del compose: tienen que ser el mismo."
    fi
  fi

  # d) el override de compose existe y define de verdad el contenedor
  if [ "$composeExtra" != "null" ] && [ -n "$composeExtra" ]; then
    if [ ! -f "$composeExtra" ]; then
      fallo "$nombre: composeExtra apunta a $composeExtra y ese archivo no existe."
    elif ! grep -qE "^ *srv-${nombre}:" "$composeExtra"; then
      fallo "$nombre: $composeExtra no declara el servicio srv-${nombre}." \
        "Una imagen publicada que ningun compose arranca no llega a dev."
    fi
  else
    if ! grep -qE "^ *srv-${nombre}:" docker-compose.yml 2>/dev/null; then
      fallo "$nombre: no tiene composeExtra y docker-compose.yml no declara srv-${nombre}."
    fi
  fi
done < <(jq -r '.servicios[] | [.nombre, .ruta, .herramienta, .puertoContenedor, .rutaSalud, (.composeExtra // "null"), (.memLimitMiB // "null")] | @tsv' "$CATALOGO")

echo
echo "== 3) Los puertos de host no chocan entre si =="
repetidos=$(jq -r '.servicios[] | "\(.claseHost) \(.puertoHost)"' "$CATALOGO" | sort | uniq -d)
if [ -n "$repetidos" ]; then
  fallo "Hay puertos de host repetidos dentro de la misma clase de host:" "$repetidos"
fi

echo
echo "== 4) El flujo de despliegue lee el catalogo, no una lista paralela =="
# Si alguien vuelve a escribir a mano una tabla de puertos en cd.yml, el
# catalogo deja de ser la fuente unica y volvemos al punto de partida.
if grep -qE '^\s+puerto_de\(\)' .github/workflows/cd.yml 2>/dev/null; then
  fallo "cd.yml volvio a tener una funcion puerto_de() con puertos escritos a mano." \
    "Los puertos salen de $CATALOGO."
fi
if ! grep -q "$CATALOGO" .github/workflows/cd.yml 2>/dev/null; then
  fallo "cd.yml no lee $CATALOGO."
fi
for script in scripts/cd/desplegar.sh scripts/cd/revertir.sh; do
  if ! grep -q "servicios.json" "$script" 2>/dev/null; then
    fallo "$script no lee el catalogo." \
      "Una lista de overrides paralela aqui significa revertir un servicio" \
      "sin su base de datos, justo cuando algo ya fallo."
  fi
done

echo
echo "== 5) Todo cliente de servicio recibe su propia credencial =="
# desplegar.sh registra en el emisor (AUTH_CLIENTES_SERVICIO) una credencial
# por cada nombre de CLIENTES_DE_SERVICIO. Si el compose de ese servicio no le
# pasa DIRECTORIO_ACTIVO_CLIENT_ID/SECRET, el servicio pide su token con el
# client_id global, que no esta registrado, y se lo niegan -- en silencio,
# porque la llamada fallida se ve como un timeout aguas abajo y no como un
# problema de credenciales. Le pasaba a notificaciones, ms-finanzas y
# ms-subastas a la vez.
CLIENTES=$(grep -E '^CLIENTES_DE_SERVICIO=' scripts/cd/desplegar.sh | head -1 | cut -d'"' -f2)
if [ -z "$CLIENTES" ]; then
  fallo "No se pudo leer CLIENTES_DE_SERVICIO de scripts/cd/desplegar.sh."
else
  for cliente in $CLIENTES; do
    if grep -qhE "DIRECTORIO_ACTIVO_CLIENT_ID: ${cliente}\$" docker-compose*.yml 2>/dev/null; then
      echo "  ok    $cliente"
    else
      fallo "$cliente esta registrado como cliente de servicio pero ningun compose le pasa su credencial." \
        "Agrega a su bloque environment:" \
        "  DIRECTORIO_ACTIVO_CLIENT_ID: $cliente" \
        "  DIRECTORIO_ACTIVO_CLIENT_SECRET: \${SECRETO_SERVICIO_$(echo "$cliente" | tr 'a-z-' 'A-Z_'):-}"
    fi
  done
fi

echo
echo "== 6) Quien presenta credencial sabe a quien presentarsela =="
# Declarar DIRECTORIO_ACTIVO_CLIENT_ID sin DIRECTORIO_ACTIVO_URL no degrada
# nada: el servicio NO ARRANCA. La biblioteca compartida construye el bean
# TokenDeServicio en el arranque y explota con "Falta la URL del emisor".
# Le paso a ms-finanzas en la corrida 35939650016: cinco reinicios, health en
# rojo durante tres minutos, y el diagnostico decia OOMKilled=false mientras
# todo el mundo buscaba un problema de memoria.
for archivo in docker-compose.ms-*.yml; do
  [ -f "$archivo" ] || continue
  if grep -q "DIRECTORIO_ACTIVO_CLIENT_ID:" "$archivo" && ! grep -q "DIRECTORIO_ACTIVO_URL:" "$archivo"; then
    fallo "$archivo declara DIRECTORIO_ACTIVO_CLIENT_ID pero no DIRECTORIO_ACTIVO_URL." \
      "Sin la URL del emisor el servicio no arranca, no es que funcione peor." \
      "Agrega:  DIRECTORIO_ACTIVO_URL: \${DIRECTORIO_ACTIVO_URL:-http://srv-ms-identidad:8089/api/v1/auth/token}"
  else
    grep -q "DIRECTORIO_ACTIVO_CLIENT_ID:" "$archivo" && echo "  ok    $archivo"
  fi
done

echo
echo "== 7) Los compose se combinan sin claves repetidas =="
# Una clave repetida dentro de un mismo bloque no la ve el ojo en una
# revision y rompe TODOS los despliegues, no solo el del servicio afectado:
# "docker compose" no puede ni leer el archivo. Paso en la corrida
# 35923936097 y dejo el pipeline entero parado.
if command -v python3 >/dev/null 2>&1; then
  for archivo in docker-compose*.yml; do
    [ -f "$archivo" ] || continue
    python3 - "$archivo" <<'PY' || FALLOS=$((FALLOS + 1))
import sys, yaml
class Estricto(yaml.SafeLoader): pass
def sin_repetidas(loader, node, deep=False):
    vistas = set()
    for clave, _ in node.value:
        k = loader.construct_object(clave, deep=deep)
        if k in vistas:
            raise yaml.YAMLError(f"clave repetida: {k!r} (linea {clave.start_mark.line + 1})")
        vistas.add(k)
    return yaml.SafeLoader.construct_mapping(loader, node, deep)
Estricto.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, sin_repetidas)
# Las anclas de compose (<<: *servicio-plataforma) son legitimas: sin esto el
# lector estricto las confunde con un tipo desconocido.
Estricto.add_constructor("tag:yaml.org,2002:merge", lambda l, n: None)
try:
    yaml.load(open(sys.argv[1], encoding="utf-8"), Estricto)
except yaml.YAMLError as e:
    print(f"::error::{sys.argv[1]}: {e}")
    sys.exit(1)
PY
  done
  echo "  ok    ningun compose tiene claves repetidas"
fi

echo
if [ "$FALLOS" -gt 0 ]; then
  echo "::error::$FALLOS problema(s) en el catalogo de despliegue."
  exit 1
fi
echo "OK: el catalogo de despliegue coincide con el repositorio."
