#!/usr/bin/env bash
# Prueba de la compuerta de capacidad del CD (scripts/cd/comprobar-capacidad.sh).
#
# La compuerta decide si un despliegue entra y, si al salir el host queda sin
# margen, que se apaga. Equivocarse en cualquiera de los dos sentidos sale
# caro: dejar entrar una JVM que no cabe colgo el host el 24-sep; bloquear
# un reemplazo dejo sin desplegar el logotipo de #712 (corrida 35961416881),
# y apagar un reemplazo dejaria al MVP sin un servicio que ya funcionaba.
#
# `free`, `docker` y `curl` son falsos: cada caso fija la memoria, lo que
# corre y lo que contestan los servicios. No necesita Docker ni red.
#
#   bash scripts/cd/pruebas/comprobar-capacidad.sh
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$RAIZ/scripts/cd/comprobar-capacidad.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FALLOS=0
fallo() { FALLOS=$((FALLOS + 1)); echo "  FALLO: $*"; }
ok() { echo "  ok    $*"; }

mkdir -p "$TMP/bin"
cat > "$TMP/bin/free" <<'EOF'
#!/bin/sh
echo "               total        used        free      shared  buff/cache   available"
echo "Mem:            1910        1596          73           9         239 ${FALSO_DISPONIBLE}"
echo "Swap:           2047 $((2047 - FALSO_SWAP_LIBRE)) ${FALSO_SWAP_LIBRE}"
EOF
cat > "$TMP/bin/docker" <<'EOF'
#!/bin/sh
# Solo entiende `docker ps --format '{{.Names}}'`.
for n in ${FALSO_EN_MARCHA}; do echo "$n"; done
EOF
cat > "$TMP/bin/curl" <<'EOF'
#!/bin/sh
# El ultimo argumento es la URL; contesta 200 salvo el puerto de FALSO_CAIDO.
url=""
for a in "$@"; do url="$a"; done
case "$url" in
  *":${FALSO_CAIDO:-0}/"*) printf '000' ;;
  *) printf '200' ;;
esac
EOF
chmod +x "$TMP/bin/free" "$TMP/bin/docker" "$TMP/bin/curl"

EN_MARCHA_HOY="srv-borde srv-ms-identidad srv-salas-partidas srv-comentarios srv-ms-finanzas plataforma-db"

# $1 = modo; resto = VAR=valor. Deja la salida en $TMP/salida y devuelve el codigo.
correr() {
  local modo="$1"; shift
  env -i PATH="$TMP/bin:/usr/bin:/bin" ESTADO_NUEVOS="$TMP/nuevos.txt" "$@" \
    bash "$SCRIPT" "$modo" > "$TMP/salida" 2>&1
}

echo "== ANTES =="

rm -f "$TMP/nuevos.txt"
correr antes FALSO_DISPONIBLE=140 FALSO_SWAP_LIBRE=129 FALSO_EN_MARCHA="$EN_MARCHA_HOY" \
  SERVICIOS_PUERTOS="ms-identidad:8089 salas-partidas:8084"
codigo=$?
if [ "$codigo" -eq 0 ] && [ -z "$(tr -d ' \n' < "$TMP/nuevos.txt")" ] && grep -q "no suma memoria" "$TMP/salida"; then
  ok "reemplazo de dos servicios que ya corren, con el margen de hoy (140/129): entra"
else
  fallo "reemplazo debia entrar (codigo $codigo): $(cat "$TMP/salida")"
fi

rm -f "$TMP/nuevos.txt"
correr antes FALSO_DISPONIBLE=140 FALSO_SWAP_LIBRE=129 FALSO_EN_MARCHA="$EN_MARCHA_HOY" SERVICIOS_PUERTOS=""
codigo=$?
if [ "$codigo" -eq 0 ] && grep -q "solo frontend y borde" "$TMP/salida"; then
  ok "corrida de solo frontend y borde (#712): entra"
else
  fallo "solo frontend debia entrar (codigo $codigo): $(cat "$TMP/salida")"
fi

rm -f "$TMP/nuevos.txt"
correr antes FALSO_DISPONIBLE=140 FALSO_SWAP_LIBRE=129 FALSO_EN_MARCHA="$EN_MARCHA_HOY" \
  SERVICIOS_PUERTOS="ms-identidad:8089 ms-subastas:8092"
codigo=$?
if [ "$codigo" -ne 0 ] && [ "$(tr -d '\n' < "$TMP/nuevos.txt")" = "ms-subastas" ] && grep -q "ms-subastas" "$TMP/salida"; then
  ok "una JVM nueva (ms-subastas) sin margen: NO entra, y queda apuntada como nueva"
else
  fallo "ms-subastas no debia entrar (codigo $codigo, nuevos='$(cat "$TMP/nuevos.txt" 2>/dev/null)'): $(cat "$TMP/salida")"
fi

rm -f "$TMP/nuevos.txt"
correr antes FALSO_DISPONIBLE=400 FALSO_SWAP_LIBRE=600 FALSO_EN_MARCHA="$EN_MARCHA_HOY" \
  SERVICIOS_PUERTOS="ms-subastas:8092"
codigo=$?
if [ "$codigo" -eq 0 ] && grep -q "Hay sitio" "$TMP/salida"; then
  ok "una JVM nueva con margen de sobra (400/600): entra"
else
  fallo "con margen debia entrar (codigo $codigo): $(cat "$TMP/salida")"
fi

# Un servicio cuyo nombre es prefijo de otro que corre no cuenta como en marcha.
rm -f "$TMP/nuevos.txt"
correr antes FALSO_DISPONIBLE=140 FALSO_SWAP_LIBRE=129 FALSO_EN_MARCHA="srv-ms-finanzas-viejo" \
  SERVICIOS_PUERTOS="ms-finanzas:8093"
codigo=$?
if [ "$codigo" -ne 0 ] && [ "$(tr -d '\n' < "$TMP/nuevos.txt")" = "ms-finanzas" ]; then
  ok "el nombre se compara completo: srv-ms-finanzas-viejo no es srv-ms-finanzas"
else
  fallo "coincidencia parcial de nombre (codigo $codigo): $(cat "$TMP/salida")"
fi

echo
echo "== DESPUES =="

printf '\n' > "$TMP/nuevos.txt"
correr despues FALSO_DISPONIBLE=60 FALSO_SWAP_LIBRE=40 FALSO_EN_MARCHA="$EN_MARCHA_HOY"
codigo=$?
if [ "$codigo" -eq 0 ] && grep -q "no se exige" "$TMP/salida"; then
  ok "reemplazo con poco margen y el MVP en pie: no se marca fallo (nada que apagar)"
else
  fallo "tras un reemplazo no debia fallar (codigo $codigo): $(cat "$TMP/salida")"
fi

printf 'ms-subastas\n' > "$TMP/nuevos.txt"
correr despues FALSO_DISPONIBLE=60 FALSO_SWAP_LIBRE=40 FALSO_EN_MARCHA="$EN_MARCHA_HOY"
codigo=$?
if [ "$codigo" -ne 0 ] && grep -q "ms-subastas" "$TMP/salida"; then
  ok "servicio nuevo y host sin margen: falla y nombra lo que se debe apagar"
else
  fallo "con un servicio nuevo sin margen debia fallar (codigo $codigo): $(cat "$TMP/salida")"
fi

printf '\n' > "$TMP/nuevos.txt"
correr despues FALSO_DISPONIBLE=300 FALSO_SWAP_LIBRE=300 FALSO_EN_MARCHA="$EN_MARCHA_HOY" FALSO_CAIDO=8089
codigo=$?
if [ "$codigo" -ne 0 ] && grep -q "ms-identidad no contesta" "$TMP/salida"; then
  ok "el nucleo del MVP caido se detecta siempre, tambien tras un reemplazo"
else
  fallo "identidad caida debia fallar (codigo $codigo): $(cat "$TMP/salida")"
fi

rm -f "$TMP/nuevos.txt"
correr despues FALSO_DISPONIBLE=60 FALSO_SWAP_LIBRE=40 FALSO_EN_MARCHA="$EN_MARCHA_HOY"
codigo=$?
if [ "$codigo" -ne 0 ]; then
  ok "sin el apunte de ANTES (host sin la version nueva): se aplican los umbrales por prudencia"
else
  fallo "sin apunte debia aplicar umbrales (codigo $codigo): $(cat "$TMP/salida")"
fi

echo
if [ "$FALLOS" -gt 0 ]; then
  echo "::error::$FALLOS caso(s) de la compuerta de capacidad fallaron."
  exit 1
fi
echo "OK: la compuerta solo frena y apaga lo que suma memoria."
