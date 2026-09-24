#!/usr/bin/env bash
# Mide la memoria real de uno o varios servicios, cada uno solo con su base.
#
# Se usa desde .github/workflows/medir-memoria-servicios.yml, pero corre
# igual en local si los jars ya estan construidos:
#   bash scripts/cd/pruebas/medir-memoria.sh ms-finanzas
#
# Lo que mide y por que son tres numeros y no uno:
#   pico    - el maximo mientras arranca (Spring + Flyway + Hibernate a la
#             vez). Es el que decide si el despliegue sobrevive.
#   reposo  - lo que ocupa ya quieto. Decide si puede convivir a largo plazo.
#   base    - su Postgres, que tambien ocupa y casi siempre se olvida.
#
# El servicio corre con el MISMO techo y las mismas opciones de JVM que en el
# host de dev (mem_limit y JAVA_TOOL_OPTIONS del catalogo/compose): medir con
# un techo distinto daria un numero que no sirve para decidir nada, porque
# MaxRAMPercentage escala con el limite del contenedor.
set -uo pipefail

CATALOGO="infrastructure/despliegue/servicios.json"
LISTA="${1:-}"
[ -n "$LISTA" ] || LISTA="ms-finanzas,ms-subastas,ms-cumplimiento,ms-ecommerce"

INFORME="medicion-memoria.md"
{
  echo "# Memoria real por servicio"
  echo
  echo "Medido en un runner de GitHub el $(date -u '+%Y-%m-%d %H:%M UTC'), con el"
  echo "mismo \`mem_limit\` y las mismas opciones de JVM que usa el host de dev."
  echo
  echo "| Servicio | Pico de arranque | Reposo | Base de datos | Total en reposo | Arranque |"
  echo "|---|--:|--:|--:|--:|--:|"
} > "$INFORME"

mib() { awk -v b="$1" 'BEGIN{printf "%.0f", b/1024/1024}'; }

# Uso en MiB de un contenedor, leido del cgroup via docker stats.
uso_de() {
  local c="$1" v
  v=$(docker stats --no-stream --format '{{.MemUsage}}' "$c" 2>/dev/null | awk '{print $1}')
  [ -n "$v" ] || { echo 0; return; }
  case "$v" in
    *GiB) awk -v x="${v%GiB}" 'BEGIN{printf "%.0f", x*1024}' ;;
    *MiB) awk -v x="${v%MiB}" 'BEGIN{printf "%.0f", x}' ;;
    *KiB) awk -v x="${v%KiB}" 'BEGIN{printf "%.0f", x/1024}' ;;
    *B)   awk -v x="${v%B}"   'BEGIN{printf "%.0f", x/1024/1024}' ;;
    *)    echo 0 ;;
  esac
}

for servicio in $(echo "$LISTA" | tr ',' ' '); do
  echo
  echo "############################ $servicio ############################"
  ruta=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .ruta' "$CATALOGO")
  puerto=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .puertoContenedor' "$CATALOGO")
  salud=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .rutaSalud' "$CATALOGO")
  herramienta=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .herramienta' "$CATALOGO")
  limite=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .memLimitMiB // 384' "$CATALOGO")
  limite_db=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | .memLimitDbMiB // 192' "$CATALOGO")

  if [ "$herramienta" = "gradle" ]; then
    jar=$(ls "$ruta"/build*/libs/*.jar 2>/dev/null | grep -v plain | head -1)
  else
    jar=$(ls "$ruta"/target/*.jar 2>/dev/null | grep -v original | head -1)
  fi
  if [ -z "$jar" ]; then
    echo "  no hay jar construido en $ruta; se omite"
    echo "| \`$servicio\` | — | — | — | — | jar no construido |" >> "$INFORME"
    continue
  fi
  echo "  jar: $jar"

  red="medir-$servicio"
  cdb="medir-db-$servicio"
  capp="medir-app-$servicio"
  docker network create "$red" >/dev/null 2>&1 || true

  docker run -d --name "$cdb" --network "$red" --memory "${limite_db}m" \
    -e POSTGRES_DB=medicion -e POSTGRES_USER=medicion -e POSTGRES_PASSWORD=medicion \
    postgres:17-alpine >/dev/null
  for _ in $(seq 1 40); do
    docker exec "$cdb" pg_isready -U medicion -d medicion >/dev/null 2>&1 && break
    sleep 2
  done

  docker build -q -f tests/e2e/Dockerfile.servicio \
    --build-arg JAR="$jar" --build-arg PUERTO="$puerto" -t "medir/$servicio" . >/dev/null

  inicio=$(date +%s)
  docker run -d --name "$capp" --network "$red" --memory "${limite}m" \
    -e DB_HOST="$cdb" -e DB_PORT=5432 -e DB_NAME=medicion \
    -e DB_USER=medicion -e DB_PASSWORD=medicion \
    -e SPRING_FLYWAY_CREATE_SCHEMAS=true \
    -e IDENTIDAD_JWKS_URL="http://127.0.0.1:1/jwks" \
    "medir/$servicio" >/dev/null

  # Pico: se muestrea mientras arranca. Un solo "docker stats" al final se
  # perderia justo el momento que importa.
  pico=0; sano=0; segundos=0
  for _ in $(seq 1 90); do
    u=$(uso_de "$capp")
    [ "$u" -gt "$pico" ] && pico=$u
    if docker exec "$capp" curl -fsS "http://localhost:${puerto}${salud}" 2>/dev/null | grep -q '"status":"UP"'; then
      sano=1; segundos=$(( $(date +%s) - inicio )); break
    fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$capp" 2>/dev/null)" != "true" ]; then
      echo "  el contenedor se detuvo antes de estar sano; ultimas lineas:"
      docker logs --tail 25 "$capp" 2>&1 | sed 's/^/    | /'
      break
    fi
    sleep 2
  done

  if [ "$sano" -eq 1 ]; then
    echo "  sano en ${segundos}s (pico ${pico} MiB)"
    sleep 45   # que termine de asentarse antes de medir el reposo
    reposo=0
    for _ in 1 2 3; do u=$(uso_de "$capp"); [ "$u" -gt "$reposo" ] && reposo=$u; sleep 3; done
    db=$(uso_de "$cdb")
    total=$(( reposo + db ))
    echo "  reposo ${reposo} MiB | base ${db} MiB | total ${total} MiB"
    echo "| \`$servicio\` | ${pico} MiB | ${reposo} MiB | ${db} MiB | **${total} MiB** | ${segundos}s |" >> "$INFORME"
  else
    db=$(uso_de "$cdb")
    echo "| \`$servicio\` | ${pico} MiB | no arranco | ${db} MiB | — | no llego a sano |" >> "$INFORME"
  fi

  docker rm -f "$capp" "$cdb" >/dev/null 2>&1 || true
  docker network rm "$red" >/dev/null 2>&1 || true
done

echo
echo "================== RESUMEN =================="
cat "$INFORME"
{
  echo
  echo "El **pico** es el numero que decide si el despliegue sobrevive; el"
  echo "**total en reposo** es el que decide si el servicio puede convivir con"
  echo "los demas. El swap no cuenta como RAM."
} >> "$INFORME"
