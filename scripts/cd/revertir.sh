#!/usr/bin/env bash
# HU-CICD-002 (SCRUM-165) - CA-02: se ejecuta SOLO desde el step
# "Reversion automatica por fallo de salud" de cd.yml (jobs de dev y de
# produccion), y solo cuando desplegar.sh termino en fallo (dejo escrito
# /opt/nexus/ultimo-fallo.txt con lineas "servicio:tag_fallido:tag_anterior").
#
# Ejercitado de verdad en dev con el simulacro de desplegar.sh
# (SIMULACRO_REVERSION): ver docs/gobierno/SIMULACRO-REVERSION.md.
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
COMPOSE_MS_CUMPLIMIENTO="$DIRECTORIO/docker-compose.ms-cumplimiento.yml"
COMPOSE_MS_ECOMMERCE="$DIRECTORIO/docker-compose.ms-ecommerce.yml"
ARCHIVO_FALLO="$DIRECTORIO/ultimo-fallo.txt"

cd "$DIRECTORIO"

if [ ! -s "$ARCHIVO_FALLO" ]; then
  echo "No hay registro de fallo (ultimo-fallo.txt vacio o inexistente); nada que revertir."
  exit 0
fi

# Las imagenes de ghcr.io son privadas. Lo normal es que la imagen del tag
# estable anterior siga en el host (desplegar.sh conserva 72 h), y entonces no
# hace falta ni red ni credencial. Si no esta, se hace pull con el token de la
# corrida si llego (mismo mecanismo que desplegar.sh); sin token, el pull
# explicara el "unauthorized". Lo destapo el primer simulacro: un pull a
# ciegas fallaba por falta de sesion justo cuando mas falta hacia.
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:-github-actions}" --password-stdin
  trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
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
  if [ "$servicio" = "ms-cumplimiento" ]; then
    ARCHIVOS_COMPOSE+=(-f "$COMPOSE_MS_CUMPLIMIENTO")
  fi
  if [ "$servicio" = "ms-ecommerce" ]; then
    ARCHIVOS_COMPOSE+=(-f "$COMPOSE_MS_ECOMMERCE")
  fi

  imagen=$(docker compose "${ARCHIVOS_COMPOSE[@]}" config --images "srv-${servicio}" 2>/dev/null | head -1 || true)
  if [ -n "$imagen" ] && docker image inspect "$imagen" >/dev/null 2>&1; then
    echo "  la imagen $imagen sigue en el host: se reutiliza sin pull"
  else
    docker compose "${ARCHIVOS_COMPOSE[@]}" pull "srv-${servicio}"
  fi
  docker compose "${ARCHIVOS_COMPOSE[@]}" up -d "srv-${servicio}"
done < "$ARCHIVO_FALLO"
