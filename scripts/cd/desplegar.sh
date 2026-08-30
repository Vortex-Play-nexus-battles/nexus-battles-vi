#!/usr/bin/env bash
# HU-CICD-002 (SCRUM-165): script de despliegue que corre DENTRO del servidor
# de cada ambiente (dev, test, produccion), disparado por SSH desde
# .github/workflows/cd.yml. Es el MISMO archivo para los tres ambientes -- lo
# que cambia entre ellos son las variables de entorno con las que se invoca
# (host, credenciales, TAG), nunca el script. Asi evitamos mantener tres
# copias casi iguales que se puedan desincronizar con el tiempo.
#
# Variables de entorno que este script espera recibir ya puestas (las pone
# GitHub Actions via appleboy/ssh-action, ver cd.yml):
#   TAG                 -> tag de imagen a desplegar (sha corto del commit)
#   SERVICIOS_PUERTOS   -> ej: "comentarios:8081 correo:8082" (lista de
#                          servicios modificados en este push, con su puerto)
#   Las 16 variables de aplicacion listadas en .env.example, con el MISMO
#   nombre (APP_ENV, LOG_LEVEL, DB_RELACIONAL_URL, DB_NO_RELACIONAL_URL,
#   DB_MEMORIA_URL, COLA_MENSAJES_URL, DIRECTORIO_ACTIVO_URL,
#   DIRECTORIO_ACTIVO_CLIENT_ID, DIRECTORIO_ACTIVO_CLIENT_SECRET, SMTP_HOST,
#   SMTP_USER, SMTP_PASSWORD, PASARELA_PAGOS_API_KEY, WEBSOCKET_URL,
#   DB_USER, DB_PASS).
#
# Este script NUNCA decide si hay que revertir: eso lo hace un step aparte en
# cd.yml (solo en el job de produccion) leyendo el archivo
# ultimo-fallo.txt que este script deja escrito cuando algo no queda sano.
# Asi la reversion queda como un paso propio y visible en GitHub Actions, con
# nombre explicito, en vez de escondida dentro de este script.

set -euo pipefail

DIRECTORIO=/opt/nexus
COMPOSE_BASE="$DIRECTORIO/docker-compose.yml"
COMPOSE_DEPLOY="$DIRECTORIO/docker-compose.deploy.yml"
INTENTOS_SALUD=12
ESPERA_ENTRE_INTENTOS=5

mkdir -p "$DIRECTORIO"
cd "$DIRECTORIO"

echo "== 1) Generando .env efimero en el servidor (nunca se versiona) =="
# Mismo nombre de variable que en .env.example, valor real desde los
# secrets de GitHub Actions (llegan aqui ya como variables de entorno, ver
# el "envs:" del step de cd.yml -- nunca se escriben en texto plano en el
# yml del workflow).
cat > .env <<EOF
APP_ENV=${APP_ENV:-}
LOG_LEVEL=${LOG_LEVEL:-}
DB_RELACIONAL_URL=${DB_RELACIONAL_URL:-}
DB_NO_RELACIONAL_URL=${DB_NO_RELACIONAL_URL:-}
DB_MEMORIA_URL=${DB_MEMORIA_URL:-}
COLA_MENSAJES_URL=${COLA_MENSAJES_URL:-}
DIRECTORIO_ACTIVO_URL=${DIRECTORIO_ACTIVO_URL:-}
DIRECTORIO_ACTIVO_CLIENT_ID=${DIRECTORIO_ACTIVO_CLIENT_ID:-}
DIRECTORIO_ACTIVO_CLIENT_SECRET=${DIRECTORIO_ACTIVO_CLIENT_SECRET:-}
SMTP_HOST=${SMTP_HOST:-}
SMTP_USER=${SMTP_USER:-}
SMTP_PASSWORD=${SMTP_PASSWORD:-}
PASARELA_PAGOS_API_KEY=${PASARELA_PAGOS_API_KEY:-}
WEBSOCKET_URL=${WEBSOCKET_URL:-}
DB_USER=${DB_USER:-}
DB_PASS=${DB_PASS:-}
EOF
chmod 600 .env

echo "== 2) Guardando el tag estable actual de cada servicio, antes de tocarlo =="
# Si el servicio ya estaba corriendo con algun tag, lo guardamos en un
# archivo simple ANTES de sobreescribirlo. Si el servicio nunca se ha
# desplegado (contenedor no existe todavia), no hay nada que guardar -- y
# por lo tanto tampoco habra a donde revertir si este primer despliegue falla.
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  contenedor="srv-${servicio}"
  tag_actual=$(docker inspect --format '{{.Config.Image}}' "$contenedor" 2>/dev/null | sed 's/^.*://' || true)
  if [ -n "$tag_actual" ] && [ "$tag_actual" != "$contenedor" ]; then
    echo "$tag_actual" > "ultimo-tag-estable-${servicio}.txt"
    echo "  $servicio: tag estable previo = $tag_actual"
  else
    echo "  $servicio: no habia despliegue previo, no hay tag estable que guardar"
  fi
done

echo "== 3) Desplegando TAG=$TAG para: $SERVICIOS_PUERTOS =="
export TAG
SERVICIOS_COMPOSE=""
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  SERVICIOS_COMPOSE="$SERVICIOS_COMPOSE srv-${servicio}"
done

# Siempre los DOS archivos combinados: el base (de desarrollo local, con
# "build:") nunca se usa solo para desplegar. El de despliegue solo agrega
# "image:", y como aqui no pasamos --build, Compose usa esa imagen ya
# publicada en ghcr.io en vez de intentar construir nada en el servidor.
docker compose -f "$COMPOSE_BASE" -f "$COMPOSE_DEPLOY" pull $SERVICIOS_COMPOSE
docker compose -f "$COMPOSE_BASE" -f "$COMPOSE_DEPLOY" up -d $SERVICIOS_COMPOSE

echo "== 4) Verificando /actuator/health de cada servicio desplegado (con reintentos) =="
> ultimo-fallo.txt
HUBO_FALLO=0
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  puerto="${par##*:}"
  ok=0
  for intento in $(seq 1 "$INTENTOS_SALUD"); do
    if curl -fsS "http://localhost:${puerto}/actuator/health" | grep -q '"status":"UP"'; then
      echo "  $servicio (puerto $puerto): saludable en el intento $intento"
      ok=1
      break
    fi
    echo "  $servicio (puerto $puerto): intento $intento/$INTENTOS_SALUD sin exito, reintentando en ${ESPERA_ENTRE_INTENTOS}s"
    sleep "$ESPERA_ENTRE_INTENTOS"
  done
  if [ "$ok" -ne 1 ]; then
    HUBO_FALLO=1
    tag_anterior=""
    if [ -f "ultimo-tag-estable-${servicio}.txt" ]; then
      tag_anterior=$(cat "ultimo-tag-estable-${servicio}.txt")
    fi
    echo "${servicio}:${TAG}:${tag_anterior}" >> ultimo-fallo.txt
    echo "  $servicio: NO paso la verificacion de salud tras $INTENTOS_SALUD intentos"
  fi
done

if [ "$HUBO_FALLO" -eq 1 ]; then
  echo "Uno o mas servicios no pasaron /actuator/health. Detalle en $DIRECTORIO/ultimo-fallo.txt"
  exit 1
fi

echo "Despliegue de TAG=$TAG completado y saludable para: $SERVICIOS_PUERTOS"
