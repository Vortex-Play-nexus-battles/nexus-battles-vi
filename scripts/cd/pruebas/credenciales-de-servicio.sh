#!/usr/bin/env bash
# Prueba de repartir_credenciales_de_servicio (scripts/cd/desplegar.sh).
#
# 28-sep: misiones y ms-subastas pasan al host de contenido y el emisor de
# sus tokens (ms-identidad) sigue en el de plataforma. Cada host generaba su
# propio secreto para cada cliente, asi que el de contenido presentaba uno que
# el emisor no conocia: 401 en cada token, y todo lo que ese servicio hace con
# otro fallaba sin decir por que. Lo que aqui se fija:
#   - sin variable de entorno, todo como antes: se genera una vez y se reutiliza;
#   - con SECRETO_SERVICIO_<CLIENTE> en el entorno, manda en los dos hosts y el
#     archivo del host se alinea con ella (revertir.sh y el siguiente
#     despliegue leen el mismo valor);
#   - un valor que partiria la lista del emisor (";", "=", espacios) se rechaza;
#   - AUTH_CLIENTES_SERVICIO lleva a todos los clientes, misiones incluido;
#   - el archivo queda en 600 y nada secreto se imprime.
#
# Se corre en local, sin Docker ni red:
#   bash scripts/cd/pruebas/credenciales-de-servicio.sh
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
linea_de() { grep "^$2=" "$1" | head -n1 | cut -d= -f2-; }

ARCHIVO="$TMP/secretos-servicios.env"
ENV="$TMP/.env"
COMPARTIDO="compartido-entre-hosts-0123456789abcdef"

limpiar_entorno() {
  local c
  for c in $CLIENTES_DE_SERVICIO; do
    unset "SECRETO_SERVICIO_$(echo "$c" | tr 'a-z-' 'A-Z_')"
  done
}

correr() { # correr <n>: la funcion con su salida en salida-<n>.txt; 0/1 en resultado-<n>
  : >"$ENV"
  if repartir_credenciales_de_servicio "$ARCHIVO" "$ENV" >"$TMP/salida-$1.txt" 2>&1; then
    echo 0 >"$TMP/resultado-$1"
  else
    echo 1 >"$TMP/resultado-$1"
  fi
}
resultado() { [ "$(cat "$TMP/resultado-$1")" = "$2" ]; }

echo "0) misiones es cliente de servicio (lo exige su credencial en el host de contenido)"
comprobar "esta en CLIENTES_DE_SERVICIO" contiene <(printf '%s\n' $CLIENTES_DE_SERVICIO) "misiones"

echo "1) Host nuevo, sin variables: se genera una credencial por cliente"
limpiar_entorno
rm -f "$ARCHIVO"
correr 1
comprobar "termina bien" resultado 1 0
n_clientes=$(printf '%s\n' $CLIENTES_DE_SERVICIO | wc -l)
comprobar "una linea persistida por cliente" test "$(grep -c '^SECRETO_SERVICIO_' "$ARCHIVO")" -eq "$n_clientes"
comprobar "el archivo queda en 600" test "$(stat -c '%a' "$ARCHIVO")" = "600"
comprobar "el .env lleva la de misiones" contiene "$ENV" "SECRETO_SERVICIO_MISIONES="
comprobar "AUTH_CLIENTES_SERVICIO nombra a misiones" contiene "$ENV" "misiones="
PRIMERA_SUBASTAS=$(linea_de "$ARCHIVO" SECRETO_SERVICIO_MS_SUBASTAS)

echo "2) Segundo despliegue sin variables: reutiliza, no regenera"
correr 2
comprobar "misma credencial de ms-subastas" test "$(linea_de "$ARCHIVO" SECRETO_SERVICIO_MS_SUBASTAS)" = "$PRIMERA_SUBASTAS"
comprobar "no dice que genera nada" no_contiene "$TMP/salida-2.txt" "generada"
comprobar "sigue una linea por cliente" test "$(grep -c '^SECRETO_SERVICIO_' "$ARCHIVO")" -eq "$n_clientes"

echo "3) Llega el secret compartido de ms-subastas: manda y el archivo se alinea"
limpiar_entorno
export SECRETO_SERVICIO_MS_SUBASTAS="$COMPARTIDO"
PRIMERA_SALAS=$(linea_de "$ARCHIVO" SECRETO_SERVICIO_SALAS_PARTIDAS)
correr 3
comprobar "termina bien" resultado 3 0
comprobar "el archivo guarda el valor del entorno" test "$(linea_de "$ARCHIVO" SECRETO_SERVICIO_MS_SUBASTAS)" = "$COMPARTIDO"
comprobar "una sola linea de ms-subastas en el archivo" test "$(grep -c '^SECRETO_SERVICIO_MS_SUBASTAS=' "$ARCHIVO")" -eq 1
comprobar "el .env reparte el valor del entorno" test "$(linea_de "$ENV" SECRETO_SERVICIO_MS_SUBASTAS)" = "$COMPARTIDO"
comprobar "el emisor lo recibe en AUTH_CLIENTES_SERVICIO" contiene "$ENV" "ms-subastas=$COMPARTIDO"
comprobar "la variable del shell coincide con el .env" test "AUTH_CLIENTES_SERVICIO=$AUTH_CLIENTES_SERVICIO" = "$(grep '^AUTH_CLIENTES_SERVICIO=' "$ENV")"
comprobar "los demas clientes no cambian" test "$(linea_de "$ARCHIVO" SECRETO_SERVICIO_SALAS_PARTIDAS)" = "$PRIMERA_SALAS"
comprobar "lo dice, sin el valor" contiene "$TMP/salida-3.txt" "compartida entre hosts"
comprobar "el archivo sigue en 600" test "$(stat -c '%a' "$ARCHIVO")" = "600"

echo "4) Mismo valor otra vez: nada que alinear"
correr 4
comprobar "no dice nada" test ! -s "$TMP/salida-4.txt"

echo "5) Sin la variable, el host conserva el valor compartido (lo que ve revertir.sh)"
limpiar_entorno
correr 5
comprobar "sigue el compartido" test "$(linea_de "$ENV" SECRETO_SERVICIO_MS_SUBASTAS)" = "$COMPARTIDO"

echo "6) Un valor que partiria la lista del emisor se rechaza sin tocar nada"
limpiar_entorno
cp "$ARCHIVO" "$TMP/antes.env"
export SECRETO_SERVICIO_MISIONES="con;punto-y-coma=roto-0123456789"
correr 6
comprobar "devuelve error" resultado 6 1
comprobar "lo dice" contiene "$TMP/salida-6.txt" "SECRETO_SERVICIO_MISIONES"
comprobar "no imprime el valor" no_contiene "$TMP/salida-6.txt" "punto-y-coma"
comprobar "el archivo no cambia" cmp -s "$ARCHIVO" "$TMP/antes.env"
export SECRETO_SERVICIO_MISIONES="corto"
correr 6b
comprobar "uno demasiado corto tambien se rechaza" resultado 6b 1

echo "7) Nada secreto sale por la bitacora"
limpiar_entorno
for n in 1 2 3 4 5 6 6b; do
  while IFS= read -r valor; do
    [ -n "$valor" ] || continue
    if ! no_contiene "$TMP/salida-$n.txt" "$valor"; then
      mal "la salida del caso $n imprime una credencial"
    fi
  done < <(cut -d= -f2- "$ARCHIVO")
done
bien "ninguna salida imprime credenciales"

echo
if [ "$FALLOS" -eq 0 ]; then
  echo "Las credenciales de servicio se reparten igual en los dos hosts."
else
  echo "$FALLOS comprobacion(es) fallaron."
  exit 1
fi
