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
#   PENDIENTE (TODO_*, ver cd.yml): 5 variables especificas de ms-identidad
#   -- MS_IDENTIDAD_DB_URL, MS_IDENTIDAD_DB_USER, MS_IDENTIDAD_DB_PASSWORD,
#   LISTA_NEGRA_URL, MS_IDENTIDAD_SPRING_PROFILES_ACTIVE (esta ultima solo
#   llega puesta cuando el ambiente es produccion). Nombres CON PREFIJO
#   "MS_IDENTIDAD_" a proposito: los nombres "DB_USER"/"DB_PASS" ya existen
#   en el .env de plataforma (los usa plataforma-db) -- si los reusamos aqui
#   se pisarian entre si. Ver docker-compose.cuentas.yml para el mapeo a los
#   nombres que Spring realmente espera (DB_URL, DB_USER, DB_PASSWORD).
#   PENDIENTE (TODO_*, ver cd.yml): 5 variables especificas de
#   ms-cumplimiento -- MS_CUMPLIMIENTO_DB_HOST, MS_CUMPLIMIENTO_DB_PORT,
#   MS_CUMPLIMIENTO_DB_NAME, MS_CUMPLIMIENTO_DB_USER,
#   MS_CUMPLIMIENTO_DB_PASSWORD. Mismo prefijo "MS_CUMPLIMIENTO_" a
#   proposito, por la misma razon: ms-cumplimiento usa un esquema distinto
#   (DB_HOST/DB_PORT/DB_NAME en vez de DB_URL) para su propia base, no
#   comparte nada con DB_USER/DB_PASS de plataforma ni con el DB_URL de
#   ms-identidad. Ver docker-compose.ms-cumplimiento.yml para el mapeo.
#   PENDIENTE (TODO_*, ver cd.yml): 5 variables especificas de
#   ms-ecommerce -- MS_ECOMMERCE_DB_HOST, MS_ECOMMERCE_DB_PORT,
#   MS_ECOMMERCE_DB_NAME, MS_ECOMMERCE_DB_USER, MS_ECOMMERCE_DB_PASSWORD.
#   Mismo prefijo por la misma razon que ms-cumplimiento. Ver
#   docker-compose.ms-ecommerce.yml para el mapeo. OJO: a diferencia de
#   ms-identidad y ms-cumplimiento, ms-ecommerce tiene
#   server.servlet.context-path=/api/v1 -- su /actuator/health real vive en
#   /api/v1/actuator/health, no en /actuator/health. Ver "ruta_salud_de" en
#   el paso 4 de este script.
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
# Override de ms-identidad (Maven, equipo Cuentas). Se copia siempre al
# servidor por SCP, pero solo se agrega al comando de "docker compose" mas
# abajo si ms-identidad viene en SERVICIOS_PUERTOS de esta corrida -- si
# nadie toco cuentas en este push, este archivo ni se menciona.
COMPOSE_CUENTAS="$DIRECTORIO/docker-compose.cuentas.yml"
# Override de ms-cumplimiento (Maven, tambien equipo Cuentas). Mismo patron
# que COMPOSE_CUENTAS: se copia siempre, solo se agrega al comando si
# ms-cumplimiento viene en esta corrida.
COMPOSE_MS_CUMPLIMIENTO="$DIRECTORIO/docker-compose.ms-cumplimiento.yml"
# Override de ms-ecommerce (Maven, tambien equipo Cuentas). Mismo patron.
COMPOSE_MS_ECOMMERCE="$DIRECTORIO/docker-compose.ms-ecommerce.yml"
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
MS_IDENTIDAD_DB_URL=${MS_IDENTIDAD_DB_URL:-}
MS_IDENTIDAD_DB_USER=${MS_IDENTIDAD_DB_USER:-}
MS_IDENTIDAD_DB_PASSWORD=${MS_IDENTIDAD_DB_PASSWORD:-}
LISTA_NEGRA_URL=${LISTA_NEGRA_URL:-}
MS_IDENTIDAD_SPRING_PROFILES_ACTIVE=${MS_IDENTIDAD_SPRING_PROFILES_ACTIVE:-}
MS_CUMPLIMIENTO_DB_HOST=${MS_CUMPLIMIENTO_DB_HOST:-}
MS_CUMPLIMIENTO_DB_PORT=${MS_CUMPLIMIENTO_DB_PORT:-}
MS_CUMPLIMIENTO_DB_NAME=${MS_CUMPLIMIENTO_DB_NAME:-}
MS_CUMPLIMIENTO_DB_USER=${MS_CUMPLIMIENTO_DB_USER:-}
MS_CUMPLIMIENTO_DB_PASSWORD=${MS_CUMPLIMIENTO_DB_PASSWORD:-}
MS_ECOMMERCE_DB_HOST=${MS_ECOMMERCE_DB_HOST:-}
MS_ECOMMERCE_DB_PORT=${MS_ECOMMERCE_DB_PORT:-}
MS_ECOMMERCE_DB_NAME=${MS_ECOMMERCE_DB_NAME:-}
MS_ECOMMERCE_DB_USER=${MS_ECOMMERCE_DB_USER:-}
MS_ECOMMERCE_DB_PASSWORD=${MS_ECOMMERCE_DB_PASSWORD:-}
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
INCLUYE_CUENTAS=0
INCLUYE_MS_CUMPLIMIENTO=0
INCLUYE_MS_ECOMMERCE=0
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  SERVICIOS_COMPOSE="$SERVICIOS_COMPOSE srv-${servicio}"
  if [ "$servicio" = "ms-identidad" ]; then
    INCLUYE_CUENTAS=1
  fi
  if [ "$servicio" = "ms-cumplimiento" ]; then
    INCLUYE_MS_CUMPLIMIENTO=1
  fi
  if [ "$servicio" = "ms-ecommerce" ]; then
    INCLUYE_MS_ECOMMERCE=1
  fi
done

# Si ms-identidad esta en esta corrida, sus 3 secrets de base de datos son
# obligatorios -- application-prod.properties de ms-identidad YA lee
# ${DB_URL}/${DB_USER}/${DB_PASSWORD} literalmente, asi que desplegar con
# alguno vacio no es "degradado", es un contenedor que no arranca. Mismo
# patron de fallo visible que SONAR_ORGANIZATION en ci.yml: preferimos
# frenar aqui con un mensaje claro a que se entere por un CrashLoopBackOff
# en el healthcheck de mas abajo.
if [ "$INCLUYE_CUENTAS" -eq 1 ]; then
  FALTANTES=""
  [ -n "${MS_IDENTIDAD_DB_URL:-}" ] || FALTANTES="$FALTANTES TODO_DB_URL_MS_IDENTIDAD"
  [ -n "${MS_IDENTIDAD_DB_USER:-}" ] || FALTANTES="$FALTANTES TODO_DB_USER_MS_IDENTIDAD"
  [ -n "${MS_IDENTIDAD_DB_PASSWORD:-}" ] || FALTANTES="$FALTANTES TODO_DB_PASSWORD_MS_IDENTIDAD"
  if [ -n "$FALTANTES" ]; then
    echo "Faltan secrets de GitHub para ms-identidad, crealos en Settings > Environments:$FALTANTES"
    exit 1
  fi
fi

# Mismo patron de "fallo visible" que ms-identidad arriba, para
# ms-cumplimiento: sus 5 secrets son obligatorios si esta en esta corrida --
# application.properties lee ${DB_HOST}/${DB_PORT}/${DB_NAME}/${DB_USER}/
# ${DB_PASSWORD} literalmente (DB_PASSWORD sin default, ni siquiera arranca
# sin ella).
if [ "$INCLUYE_MS_CUMPLIMIENTO" -eq 1 ]; then
  FALTANTES=""
  [ -n "${MS_CUMPLIMIENTO_DB_HOST:-}" ] || FALTANTES="$FALTANTES TODO_DB_HOST_MS_CUMPLIMIENTO"
  [ -n "${MS_CUMPLIMIENTO_DB_PORT:-}" ] || FALTANTES="$FALTANTES TODO_DB_PORT_MS_CUMPLIMIENTO"
  [ -n "${MS_CUMPLIMIENTO_DB_NAME:-}" ] || FALTANTES="$FALTANTES TODO_DB_NAME_MS_CUMPLIMIENTO"
  [ -n "${MS_CUMPLIMIENTO_DB_USER:-}" ] || FALTANTES="$FALTANTES TODO_DB_USER_MS_CUMPLIMIENTO"
  [ -n "${MS_CUMPLIMIENTO_DB_PASSWORD:-}" ] || FALTANTES="$FALTANTES TODO_DB_PASSWORD_MS_CUMPLIMIENTO"
  if [ -n "$FALTANTES" ]; then
    echo "Faltan secrets de GitHub para ms-cumplimiento, crealos en Settings > Environments:$FALTANTES"
    exit 1
  fi
fi

# Mismo patron de "fallo visible" para ms-ecommerce: sus 5 secrets son
# obligatorios si esta en esta corrida -- application.properties lee
# ${DB_HOST}/${DB_PORT}/${DB_NAME}/${DB_USER}/${DB_PASSWORD} literalmente
# (DB_PASSWORD sin default, igual que ms-cumplimiento).
if [ "$INCLUYE_MS_ECOMMERCE" -eq 1 ]; then
  FALTANTES=""
  [ -n "${MS_ECOMMERCE_DB_HOST:-}" ] || FALTANTES="$FALTANTES TODO_DB_HOST_MS_ECOMMERCE"
  [ -n "${MS_ECOMMERCE_DB_PORT:-}" ] || FALTANTES="$FALTANTES TODO_DB_PORT_MS_ECOMMERCE"
  [ -n "${MS_ECOMMERCE_DB_NAME:-}" ] || FALTANTES="$FALTANTES TODO_DB_NAME_MS_ECOMMERCE"
  [ -n "${MS_ECOMMERCE_DB_USER:-}" ] || FALTANTES="$FALTANTES TODO_DB_USER_MS_ECOMMERCE"
  [ -n "${MS_ECOMMERCE_DB_PASSWORD:-}" ] || FALTANTES="$FALTANTES TODO_DB_PASSWORD_MS_ECOMMERCE"
  if [ -n "$FALTANTES" ]; then
    echo "Faltan secrets de GitHub para ms-ecommerce, crealos en Settings > Environments:$FALTANTES"
    exit 1
  fi
fi

# Siempre el base + el de despliegue de plataforma combinados: el base (de
# desarrollo local, con "build:") nunca se usa solo. El de despliegue solo
# agrega "image:", y como aqui no pasamos --build, Compose usa esa imagen ya
# publicada en ghcr.io en vez de intentar construir nada en el servidor.
# Los overrides de Cuentas (ms-identidad, ms-cumplimiento, ms-ecommerce)
# solo se agregan si de verdad estan entre los servicios de esta corrida --
# si no, ni se mencionan en el comando.
ARCHIVOS_COMPOSE=(-f "$COMPOSE_BASE" -f "$COMPOSE_DEPLOY")
if [ "$INCLUYE_CUENTAS" -eq 1 ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_CUENTAS")
fi
if [ "$INCLUYE_MS_CUMPLIMIENTO" -eq 1 ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_MS_CUMPLIMIENTO")
fi
if [ "$INCLUYE_MS_ECOMMERCE" -eq 1 ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_MS_ECOMMERCE")
fi

docker compose "${ARCHIVOS_COMPOSE[@]}" pull $SERVICIOS_COMPOSE
docker compose "${ARCHIVOS_COMPOSE[@]}" up -d $SERVICIOS_COMPOSE

echo "== 4) Verificando /actuator/health de cada servicio desplegado (con reintentos) =="
# Ruta de salud por servicio: todos los servicios de plataforma y
# ms-identidad/ms-cumplimiento NO tienen server.servlet.context-path, asi
# que su Actuator vive en la raiz (/actuator/health). ms-ecommerce es la
# UNICA excepcion confirmada hasta ahora: su application.properties declara
# server.servlet.context-path=/api/v1, entonces Spring monta TODOS sus
# endpoints (incluido Actuator) bajo ese prefijo -- su salud real esta en
# /api/v1/actuator/health. Se resuelve por funcion (no con un valor fijo)
# para no romper el healthcheck generico de los demas servicios, que siguen
# usando la ruta sin prefijo.
ruta_salud_de() {
  case "$1" in
    ms-ecommerce) echo "/api/v1/actuator/health" ;;
    *) echo "/actuator/health" ;;
  esac
}

> ultimo-fallo.txt
HUBO_FALLO=0
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  puerto="${par##*:}"
  ruta_salud=$(ruta_salud_de "$servicio")
  ok=0
  for intento in $(seq 1 "$INTENTOS_SALUD"); do
    if curl -fsS "http://localhost:${puerto}${ruta_salud}" | grep -q '"status":"UP"'; then
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
