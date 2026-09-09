#!/usr/bin/env bash
# Prueba de la resolucion de etiquetas de imagen por servicio de contenido
# (funcion resolver_etiquetas_contenido de scripts/cd/desplegar.sh).
#
# Nacio de la corrida 34307790392 (9-sep): al desplegar inventario, productos
# y motor con TAG=494bc19, "docker compose up" arrastro a srv-heroes (del que
# dependen inventario y motor) con esa misma etiqueta, que no existe porque
# heroes no cambio en ese push; Compose intento construirla en el servidor y
# el despliegue fallo. La regla que aqui se fija: cada servicio de contenido
# lleva su propia etiqueta; los que cambian en la corrida usan el TAG nuevo y
# los que no cambian conservan la etiqueta que ya tienen desplegada.
#
# Se corre en local, sin Docker: "bash scripts/cd/pruebas/desplegar-etiquetas-contenido.sh".
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/../desplegar.sh"

# Un "docker" falso en el PATH: solo entiende "inspect <contenedor>" y
# responde con la imagen que le diga la variable CONTENEDORES_FALSOS
# (formato "srv-heroes=ghcr.io/x/heroes:99472f3 srv-productos=..."); para
# cualquier otro contenedor falla como el docker real.
STUBS="$(mktemp -d)"
trap 'rm -rf "$STUBS"' EXIT
cat > "$STUBS/docker" <<'STUB'
#!/usr/bin/env bash
[ "$1" = "inspect" ] || { echo "docker falso: solo inspect" >&2; exit 2; }
contenedor="${@: -1}"
for par in ${CONTENEDORES_FALSOS:-}; do
  if [ "${par%%=*}" = "$contenedor" ]; then echo "${par#*=}"; exit 0; fi
done
echo "Error: No such object: $contenedor" >&2
exit 1
STUB
chmod +x "$STUBS/docker"
export PATH="$STUBS:$PATH"

# Cargar solo las funciones del script, sin ejecutar el despliegue.
DESPLEGAR_SOLO_FUNCIONES=1 source "$SCRIPT"

FALLOS=0
esperar() { # esperar <variable> <valor esperado> <descripcion>
  local real="${!1:-<sin definir>}"
  if [ "$real" = "$2" ]; then
    echo "  ok   $3 ($1=$real)"
  else
    echo "  FALLO $3: se esperaba $1=$2 y quedo $1=$real"
    FALLOS=$((FALLOS + 1))
  fi
}

echo "1) Solo inventario cambia; heroes ya esta desplegado con otra etiqueta"
unset TAG_HEROES TAG_INVENTARIO TAG_PRODUCTOS TAG_MOTOR_COMBATE
CONTENEDORES_FALSOS="srv-heroes=ghcr.io/vortex-play-nexus-battles/nexus-battles-vi-heroes:99472f3" \
  resolver_etiquetas_contenido "494bc19" "inventario:8102" >/dev/null
esperar TAG_INVENTARIO 494bc19 "el servicio de la corrida usa el TAG nuevo"
esperar TAG_HEROES 99472f3 "el servicio que no cambio conserva la etiqueta desplegada"

echo "2) Un servicio que no cambio y nunca se desplego cae al TAG de la corrida (fallo visible en el pull, no un build en el servidor)"
unset TAG_HEROES TAG_INVENTARIO TAG_PRODUCTOS TAG_MOTOR_COMBATE
CONTENEDORES_FALSOS="" resolver_etiquetas_contenido "494bc19" "inventario:8102" >/dev/null
esperar TAG_PRODUCTOS 494bc19 "sin contenedor previo se usa el TAG de la corrida"

echo "3) Varios servicios en la corrida, nombre con guion (motor-combate -> TAG_MOTOR_COMBATE)"
unset TAG_HEROES TAG_INVENTARIO TAG_PRODUCTOS TAG_MOTOR_COMBATE
CONTENEDORES_FALSOS="srv-heroes=ghcr.io/vortex-play-nexus-battles/nexus-battles-vi-heroes:99472f3" \
  resolver_etiquetas_contenido "494bc19" "inventario:8102 productos:8103 motor-combate:8104" >/dev/null
esperar TAG_MOTOR_COMBATE 494bc19 "motor-combate en la corrida"
esperar TAG_PRODUCTOS 494bc19 "productos en la corrida"
esperar TAG_HEROES 99472f3 "heroes fuera de la corrida conserva su etiqueta"

echo "4) La corrida de plataforma no toca las etiquetas de contenido (lista sin servicios de contenido)"
unset TAG_HEROES TAG_INVENTARIO TAG_PRODUCTOS TAG_MOTOR_COMBATE
CONTENEDORES_FALSOS="" resolver_etiquetas_contenido "abc1234" "comentarios:8081" >/dev/null
esperar TAG_HEROES abc1234 "sin contenedor previo, TAG de la corrida"

echo "5) Las etiquetas quedan exportadas para que docker compose las vea"
unset TAG_HEROES
CONTENEDORES_FALSOS="" resolver_etiquetas_contenido "abc1234" "heroes:8101" >/dev/null
if bash -c '[ "${TAG_HEROES:-}" = "abc1234" ]'; then echo "  ok   TAG_HEROES llega a un proceso hijo"; else echo "  FALLO TAG_HEROES no esta exportada"; FALLOS=$((FALLOS + 1)); fi

if [ "$FALLOS" -eq 0 ]; then echo "TODO OK"; else echo "$FALLOS fallo(s)"; exit 1; fi
