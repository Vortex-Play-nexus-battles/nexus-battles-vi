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
if [ "$FALLOS" -gt 0 ]; then
  echo "::error::$FALLOS problema(s) en el catalogo de despliegue."
  exit 1
fi
echo "OK: el catalogo de despliegue coincide con el repositorio."
