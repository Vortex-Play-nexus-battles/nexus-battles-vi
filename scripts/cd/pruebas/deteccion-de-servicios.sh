#!/usr/bin/env bash
# Prueba de la deteccion de servicios del CD (.github/workflows/cd.yml, paso
# "Calcular servicios modificados, su puerto y el tag de esta corrida").
#
# Nacio del CD 37302495419 (5-oct): la comprobacion de cada servicio era
# `echo "$ARCHIVOS" | grep -q "^$ruta/"` con `set -o pipefail`. Con una lista
# de archivos larga, grep encontraba pronto, cerraba la tuberia y echo moria
# con "Broken pipe": la tuberia fallaba y el servicio se daba por NO cambiado.
# Doce servicios se saltaron en silencio, productos entre ellos (el banner de
# la home respondia 401 en DEV con todo en verde).
#
# La regla que aqui se fija: la lista de archivos se lee sin tuberia
# (here-string), y un servicio que cambia al principio de una lista larga se
# detecta igual que uno del final.
#
# Se corre en local, sin red ni Docker: "bash scripts/cd/pruebas/deteccion-de-servicios.sh".
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CD="$AQUI/../../../.github/workflows/cd.yml"
FALLOS=0

ok() { echo "ok    - $1"; }
mal() { echo "FALLO - $1"; FALLOS=$((FALLOS + 1)); }

# 1) Ninguna comprobacion de pertenencia lee la lista por una tuberia.
if grep -nE 'echo "\$ARCHIVOS" *\| *grep -q' "$CD"; then
  mal 'cd.yml vuelve a leer $ARCHIVOS con echo | grep -q (ver la cabecera de esta prueba)'
else
  ok 'cd.yml no lee $ARCHIVOS con echo | grep -q'
fi

# 2) La forma que usa cd.yml esta ahi y detecta un servicio temprano en una
#    lista larga (mas que el bufer de una tuberia).
if grep -qF 'grep -q "^$ruta/" <<< "$ARCHIVOS"' "$CD"; then
  ok 'cd.yml comprueba cada servicio con un here-string'
else
  mal 'cd.yml ya no usa grep -q "^$ruta/" <<< "$ARCHIVOS"'
fi

ARCHIVOS=$( {
  echo "services/contenido/productos/src/main/java/Banner.java"
  for i in $(seq 1 5000); do
    printf 'tests/e2e/relleno-%05d/un-nombre-largo-para-llenar-la-tuberia.e2e.spec.js\n' "$i"
  done
  echo "services/plataforma/salas-partidas/build.gradle"
} | sort -u)

detectados=""
for ruta in services/contenido/productos services/plataforma/salas-partidas services/cuentas/ms-identidad; do
  grep -q "^$ruta/" <<< "$ARCHIVOS" || continue
  detectados="$detectados $ruta"
done
if [ "$detectados" = " services/contenido/productos services/plataforma/salas-partidas" ]; then
  ok "en una lista de $(wc -l <<< "$ARCHIVOS") archivos se detectan los dos que cambiaron y no el que no"
else
  mal "deteccion equivocada:${detectados:- ninguno}"
fi

# 3) Por que: con la forma antigua, en estas mismas condiciones, el servicio
#    del principio se perdia. Solo se informa (depende del bufer del sistema).
if echo "$ARCHIVOS" 2>/dev/null | grep -q "^services/contenido/productos/"; then
  echo "nota  - en este sistema la tuberia no llego a romperse"
else
  echo "nota  - con echo | grep -q se habria saltado productos (Broken pipe)"
fi

if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS comprobacion(es) fallaron."
  exit 1
fi
echo "Deteccion de servicios del CD: todo en orden."
