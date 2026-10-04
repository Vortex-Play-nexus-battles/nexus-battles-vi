#!/usr/bin/env bash
# Prueba del paso 3d de scripts/cd/desplegar.sh (funcion sanar_emisor): el
# emisor de credenciales de servicio, ms-identidad, al dia cuando la corrida no
# lo trae.
#
# Nacio del 27-sep (fc226c05): la primera version del paso recreaba el emisor
# para que conociera a un cliente de servicio nuevo, pero SIN JWT_CLAVE_PRIVADA
# -- la clave solo se cargaba cuando ms-identidad venia en la corrida -- y
# arranco con un par RSA efimero: sesiones cerradas, credenciales de servicio
# rechazadas con 401 y el registro en 503 (smoke de dev 36357921316).
#
# Lo que aqui se fija:
#   - el emisor recreado lleva SIEMPRE la clave del host;
#   - se sana un emisor que corre sin ella, aunque la lista no haya cambiado;
#   - no se toca si esta al dia, si ms-identidad viene en la corrida, si la
#     corrida es del host de contenido o si no hay emisor;
#   - se recrea con la imagen que ya corre, no con el TAG de la corrida;
#   - nada secreto se imprime;
#   - si no vuelve sano, el paso falla.
#
# Se corre en local, sin Docker ni red:
#   bash scripts/cd/pruebas/emisor-de-credenciales.sh
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
contiene() { grep -qF -- "$2" "$1"; }
no_contiene() { ! grep -qF -- "$2" "$1"; }
vacio() { [ ! -s "$1" ]; }

# --- El host y la corrida -------------------------------------------------
CLAVE_DEL_HOST="clave-del-host-que-no-se-imprime"
SECRETO_A="secreto-de-salas-que-no-se-imprime"
SECRETO_B="secreto-de-ecommerce-que-no-se-imprime"
DIRECTORIO="$TMP/host"
mkdir -p "$DIRECTORIO"
printf 'JWT_CLAVE_PRIVADA=%s\n' "$CLAVE_DEL_HOST" >"$DIRECTORIO/secretos-firma.env"
LISTA_ANTERIOR="salas-partidas=$SECRETO_A"
LISTA_NUEVA="salas-partidas=$SECRETO_A;ms-ecommerce=$SECRETO_B"

# --- Dobles: nada sale de la maquina --------------------------------------
# ENTORNO_EMISOR son las variables del contenedor que corre; EMISOR_EXISTE y
# SANO, si hay emisor y si su salud responde UP. Cada recreacion se anota con
# la etiqueta y QUE clave llevaba (nunca la clave).
REGISTRO="$TMP/docker.log"
docker() {
  case "$*" in
    "inspect srv-ms-identidad")
      [ "$EMISOR_EXISTE" = 1 ]
      ;;
    *Config.Env*)
      printf '%s\n' "$ENTORNO_EMISOR"
      ;;
    *Config.Image*)
      echo "ghcr.io/vortex-play-nexus-battles/ms-identidad:a626bb75"
      ;;
    compose*)
      local clave="ninguna"
      if [ -n "${JWT_CLAVE_PRIVADA:-}" ] && [ "$JWT_CLAVE_PRIVADA" = "$CLAVE_DEL_HOST" ]; then
        clave="la-del-host"
      elif [ -n "${JWT_CLAVE_PRIVADA:-}" ]; then
        clave="otra"
      fi
      echo "RECREADO tag=${TAG:-} clave=$clave args=$*" >>"$REGISTRO"
      ;;
    *)
      echo "INESPERADO docker $*" >>"$REGISTRO"
      return 1
      ;;
  esac
}
curl() { if [ "$SANO" = 1 ]; then echo '{"status":"UP"}'; fi; }
sleep() { :; }

preparar() { # preparar <lista del contenedor> <clave del contenedor>
  : >"$REGISTRO"
  unset JWT_CLAVE_PRIVADA
  EMISOR_EXISTE=1
  SANO=1
  INCLUYE_CONTENIDO=0
  SERVICIOS_COMPOSE="srv-ms-ecommerce srv-admin-parametros"
  ARCHIVOS_COMPOSE=(-f "$DIRECTORIO/docker-compose.yml" -f "$DIRECTORIO/docker-compose.deploy.yml")
  AUTH_CLIENTES_SERVICIO="$LISTA_NUEVA"
  ENTORNO_EMISOR="SERVER_PORT=8089
AUTH_CLIENTES_SERVICIO=$1
JWT_CLAVE_PRIVADA=$2"
}

correr() { # correr <n>: sanar_emisor con su salida en salida-<n>.txt; 0/1 en resultado-<n>
  if sanar_emisor >"$TMP/salida-$1.txt" 2>&1; then
    echo 0 >"$TMP/resultado-$1"
  else
    echo 1 >"$TMP/resultado-$1"
  fi
}
resultado() { [ "$(cat "$TMP/resultado-$1")" = "$2" ]; }

echo "1) Cliente de servicio nuevo (el caso de B5): se recrea CON la clave del host"
preparar "$LISTA_ANTERIOR" "$CLAVE_DEL_HOST"
correr 1
comprobar "termina bien" resultado 1 0
comprobar "recrea srv-ms-identidad una vez" test "$(grep -c '^RECREADO' "$REGISTRO")" -eq 1
comprobar "con la clave de firma del host, no con un par efimero" contiene "$REGISTRO" "clave=la-del-host"
comprobar "con la imagen que ya corre, no con el TAG de la corrida" contiene "$REGISTRO" "tag=a626bb75"
comprobar "suma el compose de cuentas, que es el que interpola la clave" contiene "$REGISTRO" "docker-compose.cuentas.yml"
comprobar "solo ese contenedor y sin dependencias" contiene "$REGISTRO" "up -d --no-deps srv-ms-identidad"
comprobar "dice por que" contiene "$TMP/salida-1.txt" "cambio la lista de credenciales de servicio"

echo "2) El emisor corre SIN la clave (lo que dejo fc226c05): se sana aunque la lista este al dia"
preparar "$LISTA_NUEVA" ""
correr 2
comprobar "termina bien" resultado 2 0
comprobar "lo recrea" test "$(grep -c '^RECREADO' "$REGISTRO")" -eq 1
comprobar "con la clave del host" contiene "$REGISTRO" "clave=la-del-host"
comprobar "dice por que" contiene "$TMP/salida-2.txt" "corre sin la clave de firma del host"

echo "3) Emisor con otra clave (p. ej. la efimera de un arranque anterior): tambien se sana"
preparar "$LISTA_NUEVA" "una-clave-efimera"
correr 3
comprobar "lo recrea con la clave del host" contiene "$REGISTRO" "clave=la-del-host"

echo "4) Todo al dia: no se toca"
preparar "$LISTA_NUEVA" "$CLAVE_DEL_HOST"
correr 4
comprobar "termina bien" resultado 4 0
comprobar "no recrea nada" vacio "$REGISTRO"
comprobar "no dice nada" vacio "$TMP/salida-4.txt"

echo "5) ms-identidad viene en la corrida: ya se recreo con lista y clave (paso 3.0)"
preparar "$LISTA_ANTERIOR" ""
SERVICIOS_COMPOSE="srv-ms-identidad srv-ms-ecommerce"
correr 5
comprobar "no lo vuelve a recrear" vacio "$REGISTRO"

echo "6) Corrida del host de contenido: el emisor no vive alli"
preparar "$LISTA_ANTERIOR" ""
INCLUYE_CONTENIDO=1
correr 6
comprobar "no toca nada" vacio "$REGISTRO"

echo "7) Host sin emisor (todavia no se desplego ms-identidad)"
preparar "$LISTA_ANTERIOR" ""
EMISOR_EXISTE=0
correr 7
comprobar "termina bien" resultado 7 0
comprobar "no toca nada" vacio "$REGISTRO"

echo "8) El compose de cuentas ya viene en la corrida: no se repite"
preparar "$LISTA_ANTERIOR" "$CLAVE_DEL_HOST"
ARCHIVOS_COMPOSE+=(-f "$DIRECTORIO/docker-compose.cuentas.yml")
correr 8
comprobar "aparece una sola vez" test "$(grep -o 'docker-compose.cuentas.yml' "$REGISTRO" | wc -l)" -eq 1

echo "9) Si no vuelve sano, el paso falla"
preparar "$LISTA_ANTERIOR" "$CLAVE_DEL_HOST"
SANO=0
correr 9
comprobar "devuelve error" resultado 9 1
comprobar "y lo dice" contiene "$TMP/salida-9.txt" "no volvio sano"

echo "10) Nada secreto sale en la bitacora del despliegue"
for n in 1 2 3 4 5 6 7 8 9; do
  for secreto in "$CLAVE_DEL_HOST" "$SECRETO_A" "$SECRETO_B" "una-clave-efimera"; do
    if ! no_contiene "$TMP/salida-$n.txt" "$secreto"; then
      mal "la salida del caso $n imprime un secreto"
    fi
  done
done
bien "ninguna salida imprime la clave ni los secretos de los clientes"

echo
if [ "$FALLOS" -eq 0 ]; then
  echo "El paso 3d deja el emisor al dia y con la clave del host."
else
  echo "$FALLOS comprobacion(es) fallaron."
  exit 1
fi
