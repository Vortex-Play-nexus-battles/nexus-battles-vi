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

# El override de cada servicio sale del mismo catalogo que usa desplegar.sh
# (infrastructure/despliegue/servicios.json). Antes habia aqui una tercera
# copia de la lista -- una en cd.yml, otra en desplegar.sh y esta -- y la de
# aqui solo conocia tres servicios: revertir ms-finanzas o ms-subastas habria
# levantado el contenedor sin su override, o sea sin su base de datos, justo
# en el momento en que algo ya habia fallado.
CATALOGO="$DIRECTORIO/infrastructure/despliegue/servicios.json"
if [ ! -f "$CATALOGO" ]; then
  _raiz_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd || true)"
  if [ -n "$_raiz_repo" ] && [ -f "$_raiz_repo/infrastructure/despliegue/servicios.json" ]; then
    CATALOGO="$_raiz_repo/infrastructure/despliegue/servicios.json"
  fi
fi
if command -v jq >/dev/null 2>&1; then
  compose_extra_de() { jq -r --arg n "$1" '.servicios[] | select(.nombre == $n) | .composeExtra // ""' "$CATALOGO" 2>/dev/null; }
else
  compose_extra_de() { python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
for s in d["servicios"]:
    if s["nombre"]==sys.argv[2]:
        print(s.get("composeExtra") or "");break' "$CATALOGO" "$1" 2>/dev/null; }
fi
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
  extra=$(compose_extra_de "$servicio")
  if [ -n "$extra" ] && [ "$extra" != "null" ]; then
    if [ -f "$DIRECTORIO/$extra" ]; then
      ARCHIVOS_COMPOSE+=(-f "$DIRECTORIO/$extra")
    else
      echo "  ADVERTENCIA: el catalogo pide $extra y no esta en el servidor; se revierte sin su override."
    fi
  fi

  imagen=$(docker compose "${ARCHIVOS_COMPOSE[@]}" config --images "srv-${servicio}" 2>/dev/null | head -1 || true)
  if [ -n "$imagen" ] && docker image inspect "$imagen" >/dev/null 2>&1; then
    echo "  la imagen $imagen sigue en el host: se reutiliza sin pull"
  else
    docker compose "${ARCHIVOS_COMPOSE[@]}" pull "srv-${servicio}"
  fi
  # La clave de firma de ms-identidad no va en el .env (asegurar_clave_de_firma
  # en desplegar.sh). Sin esto, revertir ms-identidad lo levantaria con una
  # clave efimera y cerraria todas las sesiones justo cuando algo ya fallo.
  if [ "$extra" = "docker-compose.cuentas.yml" ] && [ -z "${JWT_CLAVE_PRIVADA:-}" ] \
     && [ -s "$DIRECTORIO/secretos-firma.env" ]; then
    JWT_CLAVE_PRIVADA=$(grep '^JWT_CLAVE_PRIVADA=' "$DIRECTORIO/secretos-firma.env" | head -n1 | cut -d= -f2- || true)
    export JWT_CLAVE_PRIVADA
  fi
  # R16.5b: igual que en desplegar.sh. Sin esto el contenedor revertido
  # quedaria sin hora de creacion y no esperaria su turno en el proximo
  # arranque del host (el valor por omision no escalona).
  export ARRANQUE_CREADO_EN="$(date +%s)"
  docker compose "${ARCHIVOS_COMPOSE[@]}" up -d "srv-${servicio}"
done < "$ARCHIVO_FALLO"
