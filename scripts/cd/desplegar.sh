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
#                          Para services/contenido/* la etiqueta es POR
#                          SERVICIO (TAG_HEROES, TAG_INVENTARIO, ...):
#                          los de esta corrida usan TAG y los que no
#                          cambiaron conservan la que ya tienen
#                          desplegada. Ver resolver_etiquetas_contenido.
#   SERVICIOS_PUERTOS   -> ej: "comentarios:8081 correo:8082" (lista de
#                          servicios modificados en este push, con su puerto)
#   Las 16 variables de aplicacion listadas en .env.example, con el MISMO
#   nombre (APP_ENV, LOG_LEVEL, DB_RELACIONAL_URL,
#   DB_MEMORIA_URL, DIRECTORIO_ACTIVO_URL,
#   DIRECTORIO_ACTIVO_CLIENT_ID, DIRECTORIO_ACTIVO_CLIENT_SECRET, SMTP_HOST,
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
#   ms-identidad y ms-cumplimiento, ms-ecommerce tiene un context-path, y es
#   /ecommerce (application.properties linea 2) -- NO /api/v1, como decia
#   aqui hasta hoy. Su /actuator/health real vive en
#   /ecommerce/actuator/health. Ver "ruta_salud_de" en el paso 4.
#
# Este script NUNCA decide si hay que revertir: eso lo hace un step aparte en
# cd.yml (en los jobs de dev y de produccion) leyendo el archivo
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
# Override de ms-finanzas (Gradle, tambien equipo Cuentas). Mismo patron que
# los tres de arriba, aunque el servicio en si sea Gradle y no Maven -- el
# override de despliegue es igual para los cuatro. HU-PAG-002 #536,
# HU-JUE-012 #470. Nota de capacidad en docker-compose.ms-finanzas.yml y en
# cd.yml (FUERA_DEL_HOST_DEV, issue #571 punto 4): que este override exista
# no implica que quepa en el host de dev sin medirlo.
COMPOSE_MS_FINANZAS="$DIRECTORIO/docker-compose.ms-finanzas.yml"
# Override de los servicios de services/contenido/* (equipo Contenido):
# mismo mecanismo que el de cuentas -- se copia siempre, se agrega al
# comando solo si algun servicio de contenido viene en esta corrida.
COMPOSE_CONTENIDO="$DIRECTORIO/docker-compose.contenido.yml"
SERVICIOS_CONTENIDO="heroes inventario productos motor-combate"
# Ventana de espera del healthcheck: 36 x 5 s = 3 minutos por servicio.
# Eran 12 x 5 s = 60 s, pensados para un servidor holgado. En el host de dev
# real (t3.small: 2 vCPU con creditos de CPU "standard", 2 GiB) arrancar
# varias JVM de Spring Boot a la vez -- cada una con Flyway y Hibernate --
# pasa de 60 s, y el PRIMER servicio de la lista es el que peor lo pasa:
# se verifica cuando los demas todavia estan compitiendo por la CPU. Tres
# minutos siguen dando un fallo rapido si el servicio esta de verdad roto.
INTENTOS_SALUD=36
ESPERA_ENTRE_INTENTOS=5

# Etiqueta de imagen por servicio de contenido. docker-compose.contenido.yml
# lee ${TAG_HEROES}, ${TAG_INVENTARIO}, ${TAG_PRODUCTOS} y ${TAG_MOTOR_COMBATE}
# (con ${TAG} como respaldo). Hace falta porque el CD solo construye la imagen
# de los servicios que cambiaron en el push, pero "docker compose up" arrastra
# a las dependencias (inventario y motor dependen de srv-heroes): si todas
# compartieran el TAG de la corrida, la dependencia que no cambio apuntaria a
# una imagen que no existe y Compose intentaria construirla en el servidor
# (corrida 34307790392, 9-sep). Regla: el servicio que viene en esta corrida
# usa el TAG nuevo; el que no viene conserva la etiqueta del contenedor que ya
# esta corriendo; si nunca se desplego, usa el TAG nuevo y el pull fallara con
# un "not found" explicito (mejor eso que un build silencioso en el host).
#   $1 = TAG de la corrida, $2 = lista "servicio:puerto" de la corrida.
resolver_etiquetas_contenido() {
  local tag_corrida="$1" lista="$2" s par variable en_corrida tag_desplegada
  for s in $SERVICIOS_CONTENIDO; do
    variable="TAG_$(echo "$s" | tr 'a-z-' 'A-Z_')"
    en_corrida=0
    for par in $lista; do
      if [ "${par%%:*}" = "$s" ]; then en_corrida=1; fi
    done
    if [ "$en_corrida" -eq 1 ]; then
      export "$variable=$tag_corrida"
      echo "  $s: cambia en esta corrida -> $tag_corrida"
      continue
    fi
    tag_desplegada=$(docker inspect --format '{{.Config.Image}}' "srv-$s" 2>/dev/null | sed 's/^.*://' || true)
    if [ -n "$tag_desplegada" ]; then
      export "$variable=$tag_desplegada"
      echo "  $s: no cambia, conserva la etiqueta desplegada -> $tag_desplegada"
    else
      export "$variable=$tag_corrida"
      echo "  $s: no cambia y nunca se desplego; si otro servicio depende de el, el pull fallara con 'not found' (despliegalo primero)"
    fi
  done
}

# Las pruebas de scripts/cd/pruebas/ cargan este archivo solo por sus
# funciones; con esta variable no se toca el servidor.
if [ "${DESPLEGAR_SOLO_FUNCIONES:-0}" = "1" ]; then
  return 0 2>/dev/null || exit 0
fi

mkdir -p "$DIRECTORIO"
cd "$DIRECTORIO"

# R8.3 — area de intercambio antes de levantar nada.
#
# Va aqui, y no solo en el `user_data` de Terraform, porque `user_data` corre
# UNICAMENTE en el primer arranque: cambiarlo no hace nada en un host que ya
# existe, y forzar su re-ejecucion significaria recrear la instancia. (El
# Terraform se corrige igualmente, para cualquier reconstruccion futura.)
#
# `nexus-contenido-dev` nacio sin swap: 1,9 GiB de RAM contra 1.760 MB de
# `mem_limit`, margen declarado de -114 MB, y ahi viven los cuatro servicios
# de los que depende el combate. Sin swap un pico no degrada: mata un
# contenedor. En `nexus-plataforma-dev`, que ya la trae de su user_data, el
# script ve la swap activa y no toca nada.
#
# Es idempotente y NO es load-bearing: si no puede crearla, avisa y devuelve
# 0. Un despliegue no se cae por no poder crear swap.
if [ -f "$DIRECTORIO/scripts/cd/asegurar-swap.sh" ]; then
  echo "== 0) Comprobando el area de intercambio del host =="
  chmod +x "$DIRECTORIO/scripts/cd/asegurar-swap.sh" 2>/dev/null || true
  "$DIRECTORIO/scripts/cd/asegurar-swap.sh" || true
fi

echo "== 1) Generando .env efimero en el servidor (nunca se versiona) =="
# Mismo nombre de variable que en .env.example, valor real desde los
# secrets de GitHub Actions (llegan aqui ya como variables de entorno, ver
# el "envs:" del step de cd.yml -- nunca se escriben en texto plano en el
# yml del workflow).
cat > .env <<EOF
APP_ENV=${APP_ENV:-}
LOG_LEVEL=${LOG_LEVEL:-}
DB_RELACIONAL_URL=${DB_RELACIONAL_URL:-}
DB_MEMORIA_URL=${DB_MEMORIA_URL:-}
DIRECTORIO_ACTIVO_URL=${DIRECTORIO_ACTIVO_URL:-}
DIRECTORIO_ACTIVO_CLIENT_ID=${DIRECTORIO_ACTIVO_CLIENT_ID:-}
DIRECTORIO_ACTIVO_CLIENT_SECRET=${DIRECTORIO_ACTIVO_CLIENT_SECRET:-}
SMTP_HOST=${SMTP_HOST:-}
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
# Configuracion opcional de los servicios de plataforma (variables del
# entorno de GitHub, no secrets). Solo se escriben si llegan con valor: una
# linea "VARIABLE=" vacia en el .env llega a Spring como cadena vacia y
# ANULA el valor por defecto de ${VARIABLE:defecto} en application.yml;
# omitirla conserva ese valor por defecto.
for variable in SMTP_PORT LISTA_NEGRA_VERIFICAR_URL SALAS_WS_ORIGENES CHAT_WS_ORIGENES IDENTIDAD_JWKS_URL JWT_CLAVE_PRIVADA \
    CHAT_HISTORIAL_TAMANO NOTIFICACIONES_WS_ORIGENES COMENTARIOS_FORMATOS_IMAGEN \
    IDENTIDAD_CORS_ORIGENES; do
  valor="${!variable:-}"
  if [ -n "$valor" ]; then
    echo "$variable=$valor" >> .env
  fi
done
chmod 600 .env

# Credenciales de servicio (ADR-001 via el emisor transitorio de ADR-005).
#
# No son secrets de GitHub: se generan UNA vez en el host y se persisten en
# secretos-servicios.env (fuera del .env efimero), de modo que cada
# despliegue reparta los mismos valores al emisor (ms-identidad lee
# AUTH_CLIENTES_SERVICIO) y a cada cliente (SECRETO_SERVICIO_<CLIENTE>, que
# docker-compose.deploy.yml inyecta como DIRECTORIO_ACTIVO_CLIENT_SECRET del
# servicio correspondiente). Rotar uno = borrar su linea de ese archivo y
# volver a desplegar. Nunca se imprimen.
SECRETOS_SERVICIOS="$DIRECTORIO/secretos-servicios.env"
CLIENTES_DE_SERVICIO="salas-partidas comentarios notificaciones ms-subastas ms-finanzas moderacion-sanciones torneos admin-parametros"
touch "$SECRETOS_SERVICIOS"
chmod 600 "$SECRETOS_SERVICIOS"
AUTH_CLIENTES_SERVICIO=""
for cliente in $CLIENTES_DE_SERVICIO; do
  clave="SECRETO_SERVICIO_$(echo "$cliente" | tr 'a-z-' 'A-Z_')"
  valor=$(grep "^${clave}=" "$SECRETOS_SERVICIOS" | head -n1 | cut -d= -f2- || true)
  if [ -z "$valor" ]; then
    if command -v openssl >/dev/null 2>&1; then
      valor=$(openssl rand -hex 24)
    else
      valor=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
    fi
    echo "${clave}=${valor}" >> "$SECRETOS_SERVICIOS"
    echo "  credencial de servicio generada para $cliente"
  fi
  echo "${clave}=${valor}" >> .env
  AUTH_CLIENTES_SERVICIO="${AUTH_CLIENTES_SERVICIO:+${AUTH_CLIENTES_SERVICIO};}${cliente}=${valor}"
done
echo "AUTH_CLIENTES_SERVICIO=${AUTH_CLIENTES_SERVICIO}" >> .env
# El emisor de esas credenciales es ms-identidad dentro de la red de compose
# (ADR-005), SIEMPRE, mientras ese ADR este vigente: en este host no hay
# Keycloak. El secret de GitHub DIRECTORIO_ACTIVO_URL viene de ADR-001 (la URL
# del realm) y se creo antes de ADR-005; respetarlo aqui mandaba a cada
# servicio a pedir su token a un Keycloak inexistente, la peticion moria
# antes de llegar a inventario/finanzas y crear una sala respondia 500
# (smoke de dev rojo desde c50452d). Cuando vuelva Keycloak, esta es la linea
# que cambia (ADR-005, "Como se revierte"), no el secret.
EMISOR_ADR_005="http://srv-ms-identidad:8089/api/v1/auth/token"
if [ -n "${DIRECTORIO_ACTIVO_URL:-}" ] && [ "${DIRECTORIO_ACTIVO_URL}" != "$EMISOR_ADR_005" ]; then
  echo "  DIRECTORIO_ACTIVO_URL del secret se ignora: bajo ADR-005 el emisor es ms-identidad (no se imprime el valor)"
fi
sed -i "s#^DIRECTORIO_ACTIVO_URL=.*#DIRECTORIO_ACTIVO_URL=${EMISOR_ADR_005}#" .env

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

# ---------------------------------------------------------------------------
# Simulacro de reversion (HU-CICD-002, CA-02). Con SIMULACRO_REVERSION=<servicio>
# (entrada `simulacro_reversion` de cd.yml, solo a demanda) el servicio indicado
# se despliega con la imagen BUENA de esta corrida pero con SERVER_PORT movido a
# un puerto que nadie publica: el contenedor arranca, el healthcheck del paso 4
# no lo encuentra donde debe, este script termina en fallo con
# ultimo-fallo.txt escrito, y el step "Reversion automatica por fallo de salud"
# de cd.yml lo devuelve al tag estable anterior. Es la unica forma honesta de
# ejercitar la reversion sin publicar una imagen rota a proposito: el fallo es
# de configuracion, reproducible, y no toca ni la imagen ni la base de datos.
#
# Solo un servicio por corrida, y tiene que venir en SERVICIOS_PUERTOS: si no,
# se aborta antes de tocar nada. La ventana de salud se acorta a 60 s porque
# aqui NO se espera que el servicio llegue a estar sano.
COMPOSE_SIMULACRO="$DIRECTORIO/docker-compose.simulacro.yml"
rm -f "$COMPOSE_SIMULACRO"
if [ -n "${SIMULACRO_REVERSION:-}" ]; then
  en_corrida=0
  for par in $SERVICIOS_PUERTOS; do
    if [ "${par%%:*}" = "$SIMULACRO_REVERSION" ]; then en_corrida=1; fi
  done
  if [ "$en_corrida" -ne 1 ]; then
    echo "SIMULACRO_REVERSION=$SIMULACRO_REVERSION no esta entre los servicios de esta corrida ($SERVICIOS_PUERTOS): se aborta sin tocar nada."
    exit 1
  fi
  if [ ! -f "ultimo-tag-estable-${SIMULACRO_REVERSION}.txt" ]; then
    echo "SIMULACRO_REVERSION=$SIMULACRO_REVERSION no tiene tag estable previo: sin a donde revertir, el simulacro no tiene sentido. Se aborta."
    exit 1
  fi
  cat > "$COMPOSE_SIMULACRO" <<EOF
# Generado por desplegar.sh SOLO durante un simulacro de reversion. No se
# versiona ni lo usa revertir.sh: al revertir, el servicio vuelve con su
# entorno normal.
services:
  srv-${SIMULACRO_REVERSION}:
    environment:
      SERVER_PORT: "8999"
EOF
  INTENTOS_SALUD=12
  echo "== SIMULACRO DE REVERSION: $SIMULACRO_REVERSION se despliega con SERVER_PORT=8999 (nadie lo publica); debe fallar la salud y revertirse al tag $(cat "ultimo-tag-estable-${SIMULACRO_REVERSION}.txt") =="
fi

echo "== 3) Desplegando TAG=$TAG para: $SERVICIOS_PUERTOS =="
export TAG
SERVICIOS_COMPOSE=""
INCLUYE_CUENTAS=0
INCLUYE_MS_CUMPLIMIENTO=0
INCLUYE_MS_ECOMMERCE=0
INCLUYE_MS_FINANZAS=0
INCLUYE_CONTENIDO=0
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
  if [ "$servicio" = "ms-finanzas" ]; then
    INCLUYE_MS_FINANZAS=1
  fi
  for s in $SERVICIOS_CONTENIDO; do
    if [ "$servicio" = "$s" ]; then
      INCLUYE_CONTENIDO=1
    fi
  done
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

# Mismo patron de "fallo visible" para ms-finanzas: sus 5 secrets son
# obligatorios si esta en esta corrida -- application.properties lee
# ${DB_HOST}/${DB_PORT}/${DB_NAME}/${DB_USER}/${DB_PASSWORD} literalmente
# (DB_PASSWORD sin default, igual que ms-cumplimiento/ms-ecommerce). Solo se
# llega aqui en un despliegue a demanda: en push normal a dev, ms-finanzas
# esta en FUERA_DEL_HOST_DEV y cd.yml nunca la incluye en SERVICIOS_PUERTOS.
if [ "$INCLUYE_MS_FINANZAS" -eq 1 ]; then
  FALTANTES=""
  [ -n "${MS_FINANZAS_DB_HOST:-}" ] || FALTANTES="$FALTANTES TODO_DB_HOST_MS_FINANZAS"
  [ -n "${MS_FINANZAS_DB_PORT:-}" ] || FALTANTES="$FALTANTES TODO_DB_PORT_MS_FINANZAS"
  [ -n "${MS_FINANZAS_DB_NAME:-}" ] || FALTANTES="$FALTANTES TODO_DB_NAME_MS_FINANZAS"
  [ -n "${MS_FINANZAS_DB_USER:-}" ] || FALTANTES="$FALTANTES TODO_DB_USER_MS_FINANZAS"
  [ -n "${MS_FINANZAS_DB_PASSWORD:-}" ] || FALTANTES="$FALTANTES TODO_DB_PASSWORD_MS_FINANZAS"
  if [ -n "$FALTANTES" ]; then
    echo "Faltan secrets de GitHub para ms-finanzas, crealos en Settings > Environments:$FALTANTES"
    exit 1
  fi
fi

# Siempre el base + el de despliegue de plataforma combinados: el base (de
# desarrollo local, con "build:") nunca se usa solo. El de despliegue solo
# agrega "image:", y como aqui no pasamos --build, Compose usa esa imagen ya
# publicada en ghcr.io en vez de intentar construir nada en el servidor.
# Los overrides de Cuentas (ms-identidad, ms-cumplimiento, ms-ecommerce,
# ms-finanzas) solo se agregan si de verdad estan entre los servicios de esta
# corrida -- si no, ni se mencionan en el comando.
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
if [ "$INCLUYE_MS_FINANZAS" -eq 1 ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_MS_FINANZAS")
fi
if [ "$INCLUYE_CONTENIDO" -eq 1 ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_CONTENIDO")
fi
if [ -f "$COMPOSE_SIMULACRO" ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_SIMULACRO")
fi

# Las imagenes de ghcr.io son privadas (paquetes de la organizacion): el
# servidor tiene que iniciar sesion antes del pull. Usa el token de la propia
# corrida (GITHUB_TOKEN, permiso packages:read, valido solo mientras dura el
# job) que cd.yml reexporta como GHCR_TOKEN; nunca una credencial guardada en
# el servidor. Si no llega el token (p. ej. corrida a mano), se intenta sin
# sesion y el pull explica el "unauthorized" como hasta ahora.
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "== 3a) Iniciando sesion en ghcr.io con el token de la corrida =="
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:-github-actions}" --password-stdin
  trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
fi

if [ "$INCLUYE_CONTENIDO" -eq 1 ]; then
  echo "== 3b) Etiqueta de imagen por servicio de contenido =="
  resolver_etiquetas_contenido "$TAG" "$SERVICIOS_PUERTOS"
fi

# El borde (srv-borde, docker-compose.deploy.yml) vive solo en el host de
# plataforma: sirve el frontend y enruta /api/v1/* a los servicios. Se
# levanta en cada corrida de plataforma/cuentas para que recoja el frontend y
# la configuracion recien copiados a /opt/nexus/web. En el host de contenido
# no existe (su corrida solo trae servicios de contenido).
INCLUYE_BORDE=0
if [ "$INCLUYE_CONTENIDO" -eq 0 ] && [ -d "$DIRECTORIO/web/infrastructure/red-balanceo" ]; then
  INCLUYE_BORDE=1
  SERVICIOS_COMPOSE="$SERVICIOS_COMPOSE srv-borde"
fi

docker compose "${ARCHIVOS_COMPOSE[@]}" pull $SERVICIOS_COMPOSE
docker compose "${ARCHIVOS_COMPOSE[@]}" up -d $SERVICIOS_COMPOSE

if [ "$INCLUYE_BORDE" -eq 1 ]; then
  echo "== 3c) Recargando el borde con la configuracion copiada en esta corrida =="
  # up -d no reinicia un contenedor cuya definicion no cambio, pero el
  # archivo montado si pudo cambiar: se valida y recarga nginx sin cortar
  # conexiones. Si la configuracion es invalida, falla aqui con el detalle.
  docker exec srv-borde nginx -t
  docker exec srv-borde nginx -s reload
  if ! curl -fsS http://localhost/salud-borde | grep -q UP; then
    echo "El borde no responde en http://localhost/salud-borde"
    exit 1
  fi
  echo "  borde: saludable"
fi

echo "== 4) Verificando /actuator/health de cada servicio desplegado (con reintentos) =="
# Ruta de salud por servicio: todos los servicios de plataforma y
# ms-identidad/ms-cumplimiento NO tienen server.servlet.context-path, asi
# que su Actuator vive en la raiz (/actuator/health). ms-ecommerce es la
# UNICA excepcion confirmada hasta ahora: su application.properties declara
# server.servlet.context-path=/ecommerce (linea 2), entonces Spring monta
# TODOS sus endpoints -Actuator incluido- bajo ese prefijo, y su salud real
# esta en /ecommerce/actuator/health. Se resuelve por funcion (no con un
# valor fijo) para no romper el healthcheck generico de los demas servicios,
# que siguen usando la ruta sin prefijo.
#
# Hasta hoy aqui ponia /api/v1/actuator/health, copiado de un comentario de
# docker-compose.ms-ecommerce.yml que afirmaba un context-path que el
# servicio nunca tuvo. El servicio habria arrancado bien y el despliegue lo
# habria dado por muerto tras tres minutos de reintentos. Lo fija ahora
# ArranqueDeLaAplicacionIT de ms-ecommerce, que comprueba las dos rutas.
ruta_salud_de() {
  case "$1" in
    ms-ecommerce) echo "/ecommerce/actuator/health" ;;
    # Confirmado en application.properties de ms-finanzas (linea 2):
    # server.servlet.context-path=/api/v1 -- a diferencia de ms-identidad y
    # ms-cumplimiento, que no tienen context-path y viven en la raiz.
    ms-finanzas) echo "/api/v1/actuator/health" ;;
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
    # Fallo visible: el estado del contenedor y sus ultimas lineas quedan en
    # el log de la corrida, para diagnosticar desde GitHub sin entrar al host.
    # OOMKilled=true significa que el mem_limit del compose se quedo corto.
    contenedor="srv-${servicio}"
    echo "  ---- estado de $contenedor ----"
    docker inspect --format '  estado={{.State.Status}} salida={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} reinicios={{.RestartCount}} inicio={{.State.StartedAt}}' "$contenedor" 2>/dev/null || echo "  (el contenedor no existe)"
    echo "  ---- ultimas 60 lineas de $contenedor ----"
    docker logs --tail 60 "$contenedor" 2>&1 | sed 's/^/  | /' || true
    echo "  ---- fin de $contenedor ----"
  fi
done

if [ "$HUBO_FALLO" -eq 1 ]; then
  echo "Uno o mas servicios no pasaron /actuator/health. Detalle en $DIRECTORIO/ultimo-fallo.txt"
  exit 1
fi

# ---------------------------------------------------------------------------
# Higiene de disco. SOLO despues de que todo este saludable: mientras algun
# servicio pueda necesitar una reversion, su imagen anterior no se toca.
#
# Cada despliegue deja una imagen nueva por servicio y la anterior se queda.
# Medido el 20 de septiembre con el workflow de diagnostico: 60 imagenes, 7,26
# GB, de los cuales 6,06 GB recuperables, sobre un disco de 20 GB al 60 %. A
# ese ritmo el disco se llena, y un host sin espacio no arranca contenedores
# ni escribe en la base de datos: seria una caida de verdad, no como la del 19
# de septiembre, que fue una instancia apagada.
#
# `--filter until=72h` conserva lo de los ultimos tres dias, que cubre de
# sobra la ventana de reversion. No se usa `-a` sin filtro: eso borraria las
# imagenes base y el siguiente despliegue tendria que bajarlas otra vez.
echo "== 5) Limpiando imagenes viejas (conserva las ultimas 72 h) =="
antes=$(docker system df --format '{{.Size}}' 2>/dev/null | head -1 || echo "?")
docker image prune -af --filter "until=72h" 2>&1 | tail -3 || true
despues=$(docker system df --format '{{.Size}}' 2>/dev/null | head -1 || echo "?")
echo "  Imagenes: $antes -> $despues"
df -h / | tail -1 | awk '{print "  Disco: " $4 " libres de " $2 " (" $5 " usado)"}'

echo "Despliegue de TAG=$TAG completado y saludable para: $SERVICIOS_PUERTOS"
