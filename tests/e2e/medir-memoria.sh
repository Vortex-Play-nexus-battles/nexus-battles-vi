#!/usr/bin/env bash
# Memoria real de cada contenedor del banco E2E (R16.5).
#
# Uso:
#   tests/e2e/medir-memoria.sh <etiqueta> [salida.tsv]
#
# ## Qué mide y de dónde lo saca
#
# Del cgroup v2 de cada contenedor, que es lo que cuenta el núcleo:
#
#   anon    memoria anónima: montón, metaspace, code cache, pilas y malloc.
#           No se puede soltar; bajo presión acaba en swap. Es el número que
#           decide si los servicios caben juntos en un host.
#   actual  memory.current = anon + caché de archivos (el jar leído, por
#           ejemplo). Esa caché la recupera el núcleo solo cuando le falta.
#   pico    memory.peak: lo más alto desde que arrancó el contenedor. Suele
#           marcarlo el arranque, con Spring, Flyway e Hibernate a la vez.
#
# Y de cada servicio Spring, lo que dice su propio Actuator (regla 3): montón
# usado, comprometido y máximo, no-montón comprometido (metaspace + code
# cache) e hilos vivos.
#
# Solo lee. No fuerza GC ni toca la JVM: la primera versión de la medición en
# dev lo hacía y dejó el host sin responder nueve minutos (#683).
set -uo pipefail

etiqueta="${1:?Uso: medir-memoria.sh <etiqueta> [salida.tsv]}"
salida="${2:-/dev/stdout}"
proyecto="${PROYECTO_COMPOSE:-nexus-e2e}"

# Valor de una métrica de Actuator, en crudo. Vacío si no responde.
metrica() {
  local contenedor="$1" actuator="$2" nombre="$3" etiqueta_metrica="${4:-}"
  local url="$actuator/metrics/$nombre"
  [ -n "$etiqueta_metrica" ] && url="$url?tag=$etiqueta_metrica"
  docker exec "$contenedor" curl -fsS --max-time 5 "$url" 2>/dev/null |
    python3 -c 'import json, sys
try:
    print(int(json.load(sys.stdin)["measurements"][0]["value"]))
except Exception:
    print("")'
}

mib() { if [ -n "${1:-}" ]; then echo $(( $1 / 1048576 )); else echo "-"; fi; }

cabecera='etiqueta\tcontenedor\tanon_mib\tactual_mib\tpico_mib\theap_usado_mib\theap_comprometido_mib\theap_max_mib\tnoheap_comprometido_mib\thilos\n'
if [ "$salida" = /dev/stdout ]; then printf "$cabecera"; else printf "$cabecera" > "$salida"; fi

for c in $(docker ps --filter "label=com.docker.compose.project=$proyecto" --format '{{.Names}}' | sort); do
  id=$(docker inspect -f '{{.Id}}' "$c")
  base=""
  for d in "/sys/fs/cgroup/system.slice/docker-$id.scope" "/sys/fs/cgroup/docker/$id"; do
    if [ -r "$d/memory.current" ]; then base="$d"; break; fi
  done
  if [ -z "$base" ]; then
    echo "::warning::sin cgroup v2 legible para $c" >&2
    continue
  fi

  anon=$(awk '$1 == "anon" { print $2 }' "$base/memory.stat")
  actual=$(cat "$base/memory.current")
  pico=""
  [ -r "$base/memory.peak" ] && pico=$(cat "$base/memory.peak")

  usado=""; comprometido=""; maximo=""; noheap=""; hilos=""
  # La URL de Actuator sale del healthcheck del propio contenedor, así que
  # respeta puertos y context-path (/ecommerce) sin una tabla aparte.
  actuator=$(docker inspect -f '{{join .Config.Healthcheck.Test " "}}' "$c" 2>/dev/null |
    grep -o 'http://localhost:[0-9]*[^ ]*/actuator' | head -1 || true)
  if [ -n "$actuator" ]; then
    usado=$(metrica "$c" "$actuator" jvm.memory.used area:heap)
    comprometido=$(metrica "$c" "$actuator" jvm.memory.committed area:heap)
    maximo=$(metrica "$c" "$actuator" jvm.memory.max area:heap)
    noheap=$(metrica "$c" "$actuator" jvm.memory.committed area:nonheap)
    hilos=$(metrica "$c" "$actuator" jvm.threads.live)
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$etiqueta" "$c" "$(mib "$anon")" "$(mib "$actual")" "$(mib "$pico")" \
    "$(mib "$usado")" "$(mib "$comprometido")" "$(mib "$maximo")" "$(mib "$noheap")" "${hilos:--}" \
    >> "$salida"
done
