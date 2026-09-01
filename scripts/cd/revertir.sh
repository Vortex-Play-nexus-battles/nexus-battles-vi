#!/usr/bin/env bash
# HU-CICD-002 (SCRUM-165) - CA-02: se ejecuta SOLO desde el step
# "Reversion automatica por fallo de salud" del job de produccion en cd.yml,
# y solo cuando desplegar.sh termino en fallo (dejo escrito
# /opt/nexus/ultimo-fallo.txt con lineas "servicio:tag_fallido:tag_anterior").
#
# Por que un archivo separado en vez de meter esto dentro de desplegar.sh:
# el criterio de aceptacion pide que la reversion quede como un evento propio
# y visible (un step con nombre explicito en GitHub Actions), no un efecto
# secundario escondido dentro del script de despliegue normal.

set -euo pipefail

DIRECTORIO=/opt/nexus
COMPOSE_BASE="$DIRECTORIO/docker-compose.yml"
COMPOSE_DEPLOY="$DIRECTORIO/docker-compose.deploy.yml"
COMPOSE_CUENTAS="$DIRECTORIO/docker-compose.cuentas.yml"
ARCHIVO_FALLO="$DIRECTORIO/ultimo-fallo.txt"

cd "$DIRECTORIO"

if [ ! -s "$ARCHIVO_FALLO" ]; then
  echo "No hay registro de fallo (ultimo-fallo.txt vacio o inexistente); nada que revertir."
  exit 0
fi

while IFS=: read -r servicio tag_fallido tag_anterior; do
  [ -n "$servicio" ] || continue

  if [ -z "$tag_anterior" ]; then
    echo "ADVERTENCIA: $servicio no tiene un tag estable previo conocido (posible primer despliegue) -- no se puede revertir automaticamente, requiere intervencion manual."
    continue
  fi

  echo "Reversion automatica por fallo de salud -> servicio: $servicio | tag fallido: $tag_fallido | revertido a: $tag_anterior"
  export TAG="$tag_anterior"

  ARCHIVOS_COMPOSE=(-f "$COMPOSE_BASE" -f "$COMPOSE_DEPLOY")
  if [ "$servicio" = "ms-identidad" ]; then
    ARCHIVOS_COMPOSE+=(-f "$COMPOSE_CUENTAS")
  fi

  docker compose "${ARCHIVOS_COMPOSE[@]}" pull "srv-${servicio}"
  docker compose "${ARCHIVOS_COMPOSE[@]}" up -d "srv-${servicio}"
done < "$ARCHIVO_FALLO"
