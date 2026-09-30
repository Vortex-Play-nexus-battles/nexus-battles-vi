#!/usr/bin/env bash
# Prueba del paso 3b de scripts/cd/desplegar.sh (funcion bajar_imagenes):
# las imagenes se bajan de una en una y un corte se reintenta.
#
# Nacio del 28-sep (B7, corrida 36366483510): en el host de plataforma, con el
# swap lleno, bajar tres imagenes a la vez se corto con «short read ...
# unexpected EOF» y, en el intento siguiente, no termino en los diez minutos
# de la sesion SSH. El despliegue fallaba entero por un corte de red.
#
# Lo que aqui se fija:
#   - cada servicio se baja por separado y en el orden de la corrida;
#   - un fallo pasajero se reintenta y el despliegue sigue;
#   - tras INTENTOS_DE_BAJADA fallos la funcion falla (no se levanta nada a
#     medias con una imagen vieja);
#   - un servicio que baja bien no se vuelve a pedir.
#
# Se corre en local, sin Docker ni red:
#   bash scripts/cd/pruebas/bajar-imagenes.sh
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/../desplegar.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Cargar solo las funciones del script, sin ejecutar el despliegue.
DESPLEGAR_SOLO_FUNCIONES=1 source "$SCRIPT"

FALLOS=0
bien() { echo "  ok    $1"; }
mal() { echo "  FALLO $1"; FALLOS=$((FALLOS + 1)); }
comprobar() { # comprobar <descripcion> <comando...>
  local descripcion="$1"
  shift
  if "$@"; then bien "$descripcion"; else mal "$descripcion"; fi
}

# --- Dobles: nada sale de la maquina --------------------------------------
# FALLAN_ANTES[servicio] = cuantas veces falla el pull de ese servicio antes
# de salir bien. Cada llamada se anota en REGISTRO.
REGISTRO="$TMP/docker.log"
declare -A FALLAN_ANTES=()
declare -A LLAMADAS=()
docker() {
  local ultimo="${*: -1}"
  echo "$*" >>"$REGISTRO"
  case "$*" in
    *" pull --quiet "*)
      LLAMADAS[$ultimo]=$(( ${LLAMADAS[$ultimo]:-0} + 1 ))
      [ "${LLAMADAS[$ultimo]}" -gt "${FALLAN_ANTES[$ultimo]:-0}" ]
      ;;
    *) return 0 ;;
  esac
}
sleep() { :; }  # los reintentos no esperan en la prueba

ARCHIVOS_COMPOSE=(-f docker-compose.yml -f docker-compose.deploy.yml)
PAUSA_DE_BAJADA=0

preparar() {
  : >"$REGISTRO"
  FALLAN_ANTES=()
  LLAMADAS=()
}
pulls() { grep -c ' pull --quiet ' "$REGISTRO" || true; }
orden() { grep ' pull --quiet ' "$REGISTRO" | awk '{print $NF}' | tr '\n' ' '; }

echo "bajar_imagenes (paso 3b)"

# 1. Sin cortes: una llamada por servicio, en orden.
preparar
comprobar "sin cortes, baja todo" bajar_imagenes srv-salas-partidas srv-ms-finanzas srv-borde
comprobar "una llamada por servicio" [ "$(pulls)" = "3" ]
comprobar "de una en una y en el orden de la corrida" \
  [ "$(orden)" = "srv-salas-partidas srv-ms-finanzas srv-borde " ]
comprobar "con los archivos del compose de la corrida" \
  grep -qF -- "compose -f docker-compose.yml -f docker-compose.deploy.yml pull --quiet srv-salas-partidas" "$REGISTRO"

# 2. Un corte pasajero en el segundo servicio: se reintenta y sigue.
preparar
FALLAN_ANTES[srv-ms-finanzas]=1
comprobar "un corte pasajero no tumba el despliegue" bajar_imagenes srv-salas-partidas srv-ms-finanzas srv-borde
comprobar "el servicio cortado se pide dos veces y los demas una" [ "$(pulls)" = "4" ]
comprobar "tras el reintento sigue con el siguiente" \
  [ "$(orden)" = "srv-salas-partidas srv-ms-finanzas srv-ms-finanzas srv-borde " ]

# 3. Dos cortes seguidos: el tercer intento sale bien.
preparar
FALLAN_ANTES[srv-salas-partidas]=2
comprobar "dos cortes seguidos: al tercero baja" bajar_imagenes srv-salas-partidas
comprobar "tres intentos" [ "$(pulls)" = "3" ]

# 4. Siempre falla: la funcion falla tras INTENTOS_DE_BAJADA y no sigue.
preparar
FALLAN_ANTES[srv-salas-partidas]=99
salida="$TMP/salida.log"
if bajar_imagenes srv-salas-partidas srv-borde >"$salida"; then
  mal "si una imagen no baja, falla"
else
  bien "si una imagen no baja, falla"
fi
comprobar "lo intenta exactamente 3 veces" [ "$(pulls)" = "3" ]
comprobar "no sigue con el siguiente servicio" [ "$(orden)" = "srv-salas-partidas srv-salas-partidas srv-salas-partidas " ]
comprobar "dice cual no bajo" grep -qF "No se pudo bajar la imagen de srv-salas-partidas tras 3 intentos" "$salida"

# 5. INTENTOS_DE_BAJADA se respeta.
preparar
FALLAN_ANTES[srv-borde]=99
INTENTOS_DE_BAJADA=5
if bajar_imagenes srv-borde >/dev/null; then mal "con 5 intentos tambien falla"; else bien "con 5 intentos tambien falla"; fi
comprobar "y lo intenta 5 veces" [ "$(pulls)" = "5" ]
unset INTENTOS_DE_BAJADA

if [ "$FALLOS" -ne 0 ]; then
  echo "$FALLOS comprobacion(es) fallaron"
  exit 1
fi
echo "Todas las comprobaciones pasaron."
