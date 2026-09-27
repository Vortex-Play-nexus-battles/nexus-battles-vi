#!/usr/bin/env bash
# Prueba del arranque escalonado del host de plataforma (R16.5b).
#
# El entrypoint de cada JVM (x-arranque-escalonado en docker-compose.deploy.yml)
# decide si espera su turno antes de arrancar java. Equivocarse en cualquiera de
# los dos sentidos sale caro:
#   - si NO espera cuando el host vuelve del apagado, las doce JVM arrancan a la
#     vez y el host se queda sin memoria (medido el 24-sep: swap 2040/2047 MiB,
#     nada respondia a los nueve minutos);
#   - si espera cuando NO toca (un despliegue, o una JVM que se cae a media
#     tarde), el servicio tarda minutos en volver sin motivo y el healthcheck
#     del CD lo da por muerto.
#
# Aqui se saca el script TAL CUAL de docker-compose.deploy.yml (con el mismo
# cambio de $$ por $ que hace Compose), se le da un /proc falso y un `java`
# falso, y se comprueba cada caso. No necesita Docker; si lo hay, repite todo
# con el sh de Alpine (busybox), que es el que corre de verdad en las imagenes
# eclipse-temurin:21-jdk-alpine.
#
#   bash scripts/cd/pruebas/arranque-escalonado.sh
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FALLOS=0

fallo() { FALLOS=$((FALLOS + 1)); echo "  FALLO: $*"; }
ok() { echo "  ok    $*"; }

# 1) El script, extraido del compose como lo veria el contenedor.
python3 - "$RAIZ/docker-compose.deploy.yml" "$TMP/arranque.sh" <<'PY' || { echo "::error::no se pudo extraer el script del compose"; exit 1; }
import sys, yaml
compose = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
ep = compose["x-arranque-escalonado"]
assert ep[:2] == ["/bin/sh", "-c"], ep[:2]
# Compose convierte $$ en $ al interpolar; el script no usa ningun $ simple.
open(sys.argv[2], "w", encoding="utf-8").write(ep[2].replace("$$", "$"))
PY

# 2) java falso: deja constancia de que se llego a arrancar.
mkdir -p "$TMP/bin"
cat > "$TMP/bin/java" <<'EOF'
#!/bin/sh
echo "JAVA_ARRANCO $*"
EOF
chmod +x "$TMP/bin/java"

# /proc falso: $1 = btime (arranque del host, epoch), $2 = segundos encendido.
proc_falso() {
  local dir="$TMP/proc-$1-$2"
  mkdir -p "$dir"
  printf 'cpu  1 2 3 4\nbtime %s\nprocesses 42\n' "$1" > "$dir/stat"
  printf '%s.37 1234.56\n' "$2" > "$dir/uptime"
  echo "$dir"
}

# Ejecuta el script con el sh indicado. Imprime la salida y deja en
# $TMP/segundos lo que tardo.
#   $1 = modo (local|alpine)  $2 = dir de /proc  resto = VAR=valor
ejecutar() {
  local modo="$1" proc="$2"; shift 2
  local inicio fin salida
  inicio=$(date +%s)
  if [ "$modo" = "local" ]; then
    salida=$(env -i PATH="$TMP/bin:/usr/bin:/bin" ARRANQUE_PROC="$proc" "$@" sh "$TMP/arranque.sh" 2>&1)
  else
    local args=()
    for v in "$@"; do args+=(-e "$v"); done
    salida=$(docker run --rm -v "$TMP:$TMP:ro" -e PATH="$TMP/bin:/usr/bin:/bin" \
      -e ARRANQUE_PROC="$proc" "${args[@]}" alpine:3.20 sh "$TMP/arranque.sh" 2>&1)
  fi
  fin=$(date +%s)
  echo $((fin - inicio)) > "$TMP/segundos"
  echo "$salida"
}

probar() {
  local modo="$1"
  echo
  echo "== Casos con el sh $modo =="
  local p salida s

  # Caso A: el host acaba de volver y el contenedor es de antes -> espera.
  p=$(proc_falso 1000 30)
  salida=$(ejecutar "$modo" "$p" ARRANQUE_CREADO_EN=900 ARRANQUE_TURNO=2 ARRANQUE_PASO_S=1)
  s=$(cat "$TMP/segundos")
  if echo "$salida" | grep -q "turno 2, espero 2s" && echo "$salida" | grep -q "JAVA_ARRANCO -jar app.jar" && [ "$s" -ge 2 ]; then
    ok "vuelta del apagado: espera su turno (2 s) y luego arranca java"
  else
    fallo "vuelta del apagado: se esperaba esperar 2 s y arrancar (tardo ${s}s): $salida"
  fi

  # Caso B: contenedor creado DESPUES del arranque (un despliegue) -> no espera.
  p=$(proc_falso 1000 30)
  salida=$(ejecutar "$modo" "$p" ARRANQUE_CREADO_EN=1100 ARRANQUE_TURNO=5 ARRANQUE_PASO_S=30)
  s=$(cat "$TMP/segundos")
  if ! echo "$salida" | grep -q "arranque escalonado" && echo "$salida" | grep -q "JAVA_ARRANCO" && [ "$s" -lt 5 ]; then
    ok "despliegue con el host recien encendido: arranca en el acto"
  else
    fallo "despliegue: no debia esperar (tardo ${s}s): $salida"
  fi

  # Caso C: la JVM se cae con el host encendido hace horas -> no espera.
  p=$(proc_falso 1000 7200)
  salida=$(ejecutar "$modo" "$p" ARRANQUE_CREADO_EN=900 ARRANQUE_TURNO=5 ARRANQUE_PASO_S=30)
  s=$(cat "$TMP/segundos")
  if ! echo "$salida" | grep -q "arranque escalonado" && echo "$salida" | grep -q "JAVA_ARRANCO" && [ "$s" -lt 5 ]; then
    ok "reinicio por caida a media tarde: arranca en el acto"
  else
    fallo "reinicio tardio: no debia esperar (tardo ${s}s): $salida"
  fi

  # Caso D: sin ARRANQUE_CREADO_EN (un compose up a mano) -> no espera nunca.
  p=$(proc_falso 1000 30)
  salida=$(ejecutar "$modo" "$p" ARRANQUE_TURNO=5 ARRANQUE_PASO_S=30)
  s=$(cat "$TMP/segundos")
  if ! echo "$salida" | grep -q "arranque escalonado" && echo "$salida" | grep -q "JAVA_ARRANCO" && [ "$s" -lt 5 ]; then
    ok "sin hora de creacion: no escalona por omision"
  else
    fallo "sin hora de creacion: no debia esperar (tardo ${s}s): $salida"
  fi

  # Caso E: /proc ilegible -> no se inventa una espera.
  salida=$(ejecutar "$modo" "$TMP/no-existe" ARRANQUE_CREADO_EN=900 ARRANQUE_TURNO=5 ARRANQUE_PASO_S=30)
  s=$(cat "$TMP/segundos")
  if echo "$salida" | grep -q "JAVA_ARRANCO" && [ "$s" -lt 5 ]; then
    ok "sin /proc legible: arranca en el acto"
  else
    fallo "sin /proc: no debia esperar (tardo ${s}s): $salida"
  fi

  # Caso F: `docker stop` durante la espera -> sale ya, sin arrancar java.
  # (Solo en local: el tiempo de docker run ensuciaria la medida.)
  if [ "$modo" = "local" ]; then
    p=$(proc_falso 1000 30)
    local inicio fin pid
    inicio=$(date +%s)
    env -i PATH="$TMP/bin:/usr/bin:/bin" ARRANQUE_PROC="$p" ARRANQUE_CREADO_EN=900 \
      ARRANQUE_TURNO=1 ARRANQUE_PASO_S=30 sh "$TMP/arranque.sh" > "$TMP/stop.log" 2>&1 &
    pid=$!
    sleep 1
    kill -TERM "$pid" 2>/dev/null
    wait "$pid"; local codigo=$?
    fin=$(date +%s)
    if [ $((fin - inicio)) -lt 10 ] && ! grep -q "JAVA_ARRANCO" "$TMP/stop.log" && [ "$codigo" -eq 143 ]; then
      ok "docker stop en plena espera: sale con 143 sin agotar los 30 s"
    else
      fallo "docker stop: tardo $((fin - inicio))s, codigo $codigo: $(cat "$TMP/stop.log")"
    fi
  fi
}

probar local
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  probar alpine
else
  echo
  echo "(sin Docker: no se repite con el sh de Alpine)"
fi

echo
if [ "$FALLOS" -gt 0 ]; then
  echo "::error::$FALLOS caso(s) del arranque escalonado fallaron."
  exit 1
fi
echo "OK: el arranque escalonado espera solo cuando el host vuelve del apagado."
