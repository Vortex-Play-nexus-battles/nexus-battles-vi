#!/usr/bin/env bash
# Prueba de la clave estable del segundo factor de ms-identidad (funcion
# asegurar_clave_de_segundo_factor de scripts/cd/desplegar.sh, HU-AUT-007).
#
# Lo que aqui se fija: la clave se genera una vez y se reutiliza (si cambiara,
# los secretos TOTP ya guardados dejarian de descifrarse), un secret de GitHub
# manda y el archivo se alinea, son 32 bytes en Base64 (lo que acepta
# CifradoDeSecretos), el archivo queda en 600, nunca se imprime y nunca va al
# .env compartido.
#
# Se corre en local, sin Docker: "bash scripts/cd/pruebas/clave-de-segundo-factor.sh".
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
no_contiene() { ! grep -qF -- "$2" "$1"; }

ARCHIVO="$TMP/secretos-segundo-factor.env"

echo "1) Primer despliegue, sin secret ni clave guardada"
unset IDENTIDAD_2FA_CLAVE
asegurar_clave_de_segundo_factor "$ARCHIVO" >"$TMP/salida-1.txt"
PRIMERA="${IDENTIDAD_2FA_CLAVE:-}"
comprobar "deja la clave exportada para Compose" test -n "$PRIMERA"
comprobar "son 32 bytes en Base64 (AES-256, lo que lee CifradoDeSecretos)" \
  sh -c "[ \"\$(printf '%s' '$PRIMERA' | base64 -d | wc -c)\" -eq 32 ]"
comprobar "el archivo queda en 600" sh -c "[ \"\$(stat -c %a '$ARCHIVO')\" = 600 ]"
comprobar "la guarda en el archivo del host" grep -q "^IDENTIDAD_2FA_CLAVE=$PRIMERA\$" "$ARCHIVO"
comprobar "no la imprime" no_contiene "$TMP/salida-1.txt" "$PRIMERA"

echo "2) Siguiente despliegue: la misma clave, no una nueva"
unset IDENTIDAD_2FA_CLAVE
asegurar_clave_de_segundo_factor "$ARCHIVO" >"$TMP/salida-2.txt"
comprobar "reutiliza la guardada" test "${IDENTIDAD_2FA_CLAVE:-}" = "$PRIMERA"
comprobar "no la imprime" no_contiene "$TMP/salida-2.txt" "$PRIMERA"

echo "3) Un secret de GitHub manda y el archivo se alinea con el"
SECRETO=$(openssl rand -base64 32 | tr -d '\n')
export IDENTIDAD_2FA_CLAVE="$SECRETO"
asegurar_clave_de_segundo_factor "$ARCHIVO" >"$TMP/salida-3.txt"
comprobar "usa la del secret" test "${IDENTIDAD_2FA_CLAVE:-}" = "$SECRETO"
comprobar "el archivo queda con la del secret" grep -q "^IDENTIDAD_2FA_CLAVE=$SECRETO\$" "$ARCHIVO"
comprobar "no la imprime" no_contiene "$TMP/salida-3.txt" "$SECRETO"

echo "4) Nunca va al .env compartido"
comprobar "desplegar.sh no la escribe en .env" \
  sh -c "! grep -nE 'IDENTIDAD_2FA_CLAVE=.*>>[[:space:]]*\\.env' '$SCRIPT'"
comprobar "la lista de variables opcionales del .env no la nombra" \
  sh -c "! sed -n '/^for variable in /,/; do\$/p' '$SCRIPT' | grep -q IDENTIDAD_2FA_CLAVE"

echo "5) El paso 3.0 y el 3d la cargan junto a la clave de firma; revertir.sh tambien"
comprobar "paso 3.0" grep -q 'asegurar_clave_de_segundo_factor "\$DIRECTORIO/secretos-segundo-factor.env"' "$SCRIPT"
comprobar "paso 3d (sanar_emisor)" \
  sh -c "sed -n '/^sanar_emisor()/,/^}/p' '$SCRIPT' | grep -q asegurar_clave_de_segundo_factor"
comprobar "revertir.sh" grep -q 'secretos-segundo-factor.env' "$AQUI/../revertir.sh"

if [ "$FALLOS" -ne 0 ]; then
  echo "$FALLOS comprobacion(es) fallaron"
  exit 1
fi
echo "Todo en orden"
