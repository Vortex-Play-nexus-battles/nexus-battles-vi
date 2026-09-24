#!/usr/bin/env bash
#
# Apaga servicios en el host de dev para hacerle sitio a otro.
#
# ## Por que existe
#
# El despliegue por perfiles sabe ENCENDER ("Agrega sus servicios a los que ya
# corren; no apaga nada") pero no sabe apagar. En un host donde, medido, solo
# cabe UN servicio de economia a la vez, eso significa que la unica forma de
# probar el segundo era dejar los dos compitiendo por la memoria.
#
# Paso de verdad el 24-sep: con ms-finanzas sano y sirviendo /creditos,
# /transacciones y /cofres, se desplego ms-ecommerce. Los dos quedaron
# "arriba", el swap paso de 1995 a 2047 de 2047 MiB -lleno- y ms-finanzas se
# quedo dando vueltas al 168 % de CPU sin llegar a responder: 502 en sus tres
# rutas. Ningun contenedor murio y ningun healthcheck fallo en el momento del
# despliegue, asi que el CD lo dio por bueno. No habia forma de deshacerlo sin
# entrar a mano por SSH.
#
# ## Que hace
#
# Para cada servicio de la lista: para y elimina su contenedor y, si la tiene,
# el de su base de datos. El VOLUMEN de datos NO se toca -es un volumen con
# nombre y sobrevive a `docker rm`-, asi que volver a desplegar el servicio lo
# reencuentra con sus datos y sus migraciones aplicadas.
#
# No apaga nada del MVP: la lista de protegidos de abajo se rechaza siempre.
set -euo pipefail

SERVICIOS_APAGAR="${SERVICIOS_APAGAR:-}"

if [ -z "${SERVICIOS_APAGAR// /}" ]; then
  echo "Nada que apagar."
  exit 0
fi

# El borde, el emisor de identidad y las bases compartidas sostienen el MVP.
# Apagarlos desde aqui seria una forma muy rapida de tumbar el entorno entero
# con un dedazo en un campo de texto.
PROTEGIDOS="srv-borde srv-ms-identidad identidad-db plataforma-db plataforma-cache mailpit"

esta_protegido() {
  for p in $PROTEGIDOS; do [ "$1" = "$p" ] && return 0; done
  return 1
}

detener() {
  local contenedor="$1"
  if ! sudo docker inspect "$contenedor" >/dev/null 2>&1; then
    echo "    $contenedor: no existe, nada que hacer"
    return 0
  fi
  # `stop` antes que `rm` para que el servicio cierre sus conexiones; con
  # restart: unless-stopped, un stop explicito NO lo resucita.
  sudo docker stop "$contenedor" >/dev/null 2>&1 || true
  sudo docker rm -f "$contenedor" >/dev/null 2>&1 || true
  echo "    $contenedor: apagado y eliminado (su volumen de datos sigue intacto)"
}

echo "== Apagando servicios para liberar memoria =="
for servicio in ${SERVICIOS_APAGAR//,/ }; do
  servicio="${servicio// /}"
  [ -z "$servicio" ] && continue

  contenedor="srv-$servicio"
  if esta_protegido "$contenedor"; then
    echo "::error::$contenedor sostiene el MVP y no se apaga desde aqui."
    exit 1
  fi

  echo "  $servicio"
  detener "$contenedor"

  # Las bases de los servicios de Cuentas se llaman <nombre-sin-ms>-db
  # (ms-finanzas -> finanzas-db). Si no existe, `detener` lo dice y sigue.
  base="${servicio#ms-}-db"
  if ! esta_protegido "$base"; then
    detener "$base"
  fi
done

echo
echo "== Memoria despues de apagar =="
free -m | sed 's/^/  /'
swapon --show 2>/dev/null | sed 's/^/  /' || true
