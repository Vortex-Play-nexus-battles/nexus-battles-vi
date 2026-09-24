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
# (el override de cada servicio lo dice el catalogo; ver OVERRIDES mas abajo)

# ---------------------------------------------------------------------------
# CATALOGO DE DESPLIEGUE (fuente unica de verdad).
#
# Antes, cada servicio tenia que aparecer a mano en tres sitios de este
# archivo -- una variable COMPOSE_*, una variable INCLUYE_* y un bloque de
# secrets -- ademas de en cd.yml. Olvidar uno solo bastaba para que un
# servicio con CI en verde no llegara nunca a dev: le paso a ms-finanzas
# (HU-PAG-002 #536, HU-JUE-012 #470) y a ms-subastas (#571).
#
# Ahora el override de compose, los secrets obligatorios y la ruta de salud de
# cada servicio salen del mismo archivo que lee cd.yml. Se copia al servidor
# por SCP junto a los compose.
# ---------------------------------------------------------------------------
# En el servidor esta bajo /opt/nexus (lo copia el scp de cd.yml, conservando
# la ruta). Corriendo desde una copia del repositorio -- las pruebas de
# scripts/cd/pruebas/, o alguien leyendo el script en local -- esta al lado, y
# se busca ahi como respaldo en vez de morir pidiendo una ruta de servidor que
# en ese contexto no tiene sentido.
CATALOGO="$DIRECTORIO/infrastructure/despliegue/servicios.json"
if [ ! -f "$CATALOGO" ]; then
  _raiz_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd || true)"
  if [ -n "$_raiz_repo" ] && [ -f "$_raiz_repo/infrastructure/despliegue/servicios.json" ]; then
    CATALOGO="$_raiz_repo/infrastructure/despliegue/servicios.json"
  fi
fi

# Lector minimo del catalogo. jq no esta garantizado en el host; python3 si
# viene en Ubuntu Server. Se prefiere jq cuando existe.
#   campo_de <nombre-servicio> <campo>   -> valor, o vacio si no esta
#   servicios_con <campo> <valor>        -> nombres, uno por linea
if command -v jq >/dev/null 2>&1; then
  campo_de() { jq -r --arg n "$1" --arg c "$2" '.servicios[] | select(.nombre == $n) | .[$c] // "" | tostring' "$CATALOGO" 2>/dev/null; }
  campos_de() { jq -r --arg n "$1" --arg c "$2" '.servicios[] | select(.nombre == $n) | .[$c][]?' "$CATALOGO" 2>/dev/null; }
  servicios_con() { jq -r --arg c "$1" --argjson v "$2" '.servicios[] | select(.[$c] == $v) | .nombre' "$CATALOGO" 2>/dev/null; }
else
  campo_de() { python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
for s in d["servicios"]:
    if s["nombre"]==sys.argv[2]:
        v=s.get(sys.argv[3]);print("" if v is None else v);break' "$CATALOGO" "$1" "$2" 2>/dev/null; }
  campos_de() { python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
for s in d["servicios"]:
    if s["nombre"]==sys.argv[2]:
        for v in (s.get(sys.argv[3]) or []): print(v)
        break' "$CATALOGO" "$1" "$2" 2>/dev/null; }
  servicios_con() { python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
v=json.loads(sys.argv[3])
for s in d["servicios"]:
    if s.get(sys.argv[2])==v: print(s["nombre"])' "$CATALOGO" "$1" "$2" 2>/dev/null; }
fi

if [ ! -f "$CATALOGO" ]; then
  echo "No esta $CATALOGO en el servidor. cd.yml debe copiarlo con los compose."
  echo "Sin catalogo no se sabe que override de compose ni que ruta de salud usa cada servicio."
  exit 1
fi

# Servicios que viven en el host de contenido (lo dice el catalogo, ya no una
# lista escrita aqui): se usan para resolver sus etiquetas de imagen.
SERVICIOS_CONTENIDO=$(servicios_con claseHost '"contenido"' | tr '\n' ' ')
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
SMTP_USUARIO=${SMTP_USUARIO:-}
SMTP_CLAVE=${SMTP_CLAVE:-}
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
for variable in SMTP_PORT SMTP_TLS SMTP_AUTENTICA CORREO_REMITENTE CORREO_RESPONDER_A PUBLIC_BASE_URL \
    LISTA_NEGRA_VERIFICAR_URL SALAS_WS_ORIGENES CHAT_WS_ORIGENES IDENTIDAD_JWKS_URL JWT_CLAVE_PRIVADA \
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
# ...y TAMBIEN en el shell, que es la mitad que faltaba.
#
# Corregir solo el archivo arreglaba nada mas la mitad de los servicios, y por
# eso el defecto sobrevivio a su propia correccion durante semanas. Compose
# resuelve el valor de una variable en dos sitios distintos segun como se lo
# pida el compose:
#
#   env_file: [.env]                  -> lee el ARCHIVO (el sed de arriba)
#   environment: X: ${X:-por_omision} -> INTERPOLA, y ahi el shell gana al
#                                        archivo; ademas "environment:" pisa a
#                                        "env_file:" para esa clave
#
# appleboy/ssh-action exporta al shell, via "envs:", el secret tal cual. Los
# ocho servicios de plataforma declaran env_file, pero cuatro
# --admin-parametros, comentarios, moderacion-sanciones y torneos-- declaran
# ademas la variable en "environment:" de docker-compose.deploy.yml. Medido en
# el host el 23-sep 20:25 UTC (diagnostico 35915851276, leyendo la variable
# dentro de cada contenedor con docker exec):
#
#   srv-correo, srv-metricas-plataforma, srv-notificaciones, srv-salas-partidas
#       emisor de credenciales: http://srv-ms-identidad:8089/api/v1/auth/token
#   srv-admin-parametros, srv-comentarios, srv-moderacion-sanciones, srv-torneos
#       emisor de credenciales: http://keycloak:8180/realms/nexus-battles
#
# Esos cuatro pedian su credencial de servicio a un Keycloak que no existe en
# ningun entorno (ADR-005). No se cayeron, y por eso nadie lo vio: fallan hacia
# el lado abierto, asi que el sintoma no era un error sino una funcion que no
# ocurria -- torneos sin reservar los creditos de la inscripcion, comentarios
# sin consultar sanciones antes de publicar.
#
# DIRECTORIO_ACTIVO_URL es la unica variable del repositorio con esta sombra:
# la unica que el script recalcula, que algun compose interpola y que ademas
# viaja en "envs:". Comprobado cruzando las tres listas.
export DIRECTORIO_ACTIVO_URL="$EMISOR_ADR_005"

# Credencial de las bases que viven en ESTE host y que no tienen secret en
# GitHub (R16.22). Hoy solo la de ms-chatbot: su Postgres (chatbot-db) nace en
# este mismo host, dentro de la red de Compose, y nadie de fuera la usa. Su
# contrasena no es algo que solo una persona pueda aportar: se puede generar,
# igual que las credenciales de servicio de arriba. Se genera UNA vez, se
# guarda en secretos-bases.env (600, fuera del .env efimero) y cada despliegue
# reparte el mismo valor a la base y al servicio. Nunca se imprime.
#
# Un secret o una variable de GitHub con el mismo nombre, si algun dia existe,
# manda sobre lo generado. Rotar = borrar la linea Y el volumen de la base:
# Postgres solo toma POSTGRES_PASSWORD al inicializar el volumen.
SECRETOS_BASES="$DIRECTORIO/secretos-bases.env"
touch "$SECRETOS_BASES"
chmod 600 "$SECRETOS_BASES"
asegurar_credencial_de_base() {
  # $1 prefijo (MS_CHATBOT)  $2 host  $3 puerto  $4 base  $5 usuario
  local prefijo="$1" clave valor
  for par_valor in "HOST=$2" "PORT=$3" "NAME=$4" "USER=$5" "PASSWORD="; do
    clave="${prefijo}_DB_${par_valor%%=*}"
    valor=$(eval "printf '%s' \"\${$clave:-}\"")
    if [ -z "$valor" ]; then
      valor=$(grep "^${clave}=" "$SECRETOS_BASES" | head -n1 | cut -d= -f2- || true)
    fi
    if [ -z "$valor" ]; then
      valor="${par_valor#*=}"
      if [ -z "$valor" ]; then
        if command -v openssl >/dev/null 2>&1; then
          valor=$(openssl rand -hex 24)
        else
          valor=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
        fi
        echo "  credencial de base generada para ${prefijo} (no se imprime)"
      fi
      echo "${clave}=${valor}" >> "$SECRETOS_BASES"
    fi
    export "${clave}=${valor}"
    echo "${clave}=${valor}" >> .env
  done
}
asegurar_credencial_de_base MS_CHATBOT chatbot-db 5432 chatbot_db chatbot

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
# Overrides de compose que hay que agregar al comando, deducidos del catalogo:
# cada servicio de esta corrida aporta el suyo (composeExtra) si lo tiene, y
# uno repetido -- los cuatro de contenido comparten archivo -- se agrega una
# sola vez. Los servicios sin override viven en docker-compose.yml.
OVERRIDES=""
FALTANTES_SECRETS=""
for par in $SERVICIOS_PUERTOS; do
  servicio="${par%%:*}"
  SERVICIOS_COMPOSE="$SERVICIOS_COMPOSE srv-${servicio}"

  extra=$(campo_de "$servicio" composeExtra)
  if [ -n "$extra" ] && [ "$extra" != "null" ]; then
    case " $OVERRIDES " in
      *" $extra "*) : ;;
      *) OVERRIDES="$OVERRIDES $extra" ;;
    esac
  fi

  # Secrets obligatorios del servicio. Mismo patron de "fallo visible" que
  # habia escrito tres veces a mano: preferimos frenar aqui con el nombre
  # exacto que hay que crear en GitHub, a que se entere por un contenedor que
  # reinicia en bucle y tres minutos de healthcheck en rojo.
  for variable in $(campos_de "$servicio" secretsRequeridos); do
    valor=$(eval "printf '%s' \"\${$variable:-}\"")
    if [ -z "$valor" ]; then
      FALTANTES_SECRETS="$FALTANTES_SECRETS $servicio:$variable"
    fi
  done
done

if [ -n "$FALTANTES_SECRETS" ]; then
  echo "Faltan variables obligatorias para los servicios de esta corrida."
  echo "Crealas en GitHub (Settings > Environments) y vuelve a desplegar:"
  for f in $FALTANTES_SECRETS; do
    echo "  - ${f#*:}   (lo necesita ${f%%:*})"
  done
  exit 1
fi

# Los secrets obligatorios de cada servicio ya se comprobaron arriba, de una
# sola vez y leyendo "secretsRequeridos" del catalogo. Antes habia aqui tres
# bloques casi identicos escritos a mano -- uno por ms-identidad, otro por
# ms-cumplimiento y otro por ms-ecommerce -- y agregar un servicio nuevo
# significaba acordarse de escribir un cuarto. ms-finanzas y ms-subastas
# entran ahora sin tocar este archivo.

# Siempre el base + el de despliegue de plataforma combinados: el base (de
# desarrollo local, con "build:") nunca se usa solo. El de despliegue solo
# agrega "image:", y como aqui no pasamos --build, Compose usa esa imagen ya
# publicada en ghcr.io en vez de intentar construir nada en el servidor.
# Los overrides propios de cada servicio (ms-identidad, ms-cumplimiento,
# ms-ecommerce, ms-finanzas, ms-subastas, contenido) solo se agregan si ese
# servicio esta de verdad en esta corrida. Cual le toca a cada uno lo dice el
# catalogo, no una cadena de "if" que hay que ampliar a mano.
ARCHIVOS_COMPOSE=(-f "$COMPOSE_BASE" -f "$COMPOSE_DEPLOY")
for override in $OVERRIDES; do
  if [ ! -f "$DIRECTORIO/$override" ]; then
    echo "El catalogo pide $override y no llego al servidor."
    echo "Agregalo a la lista 'source:' del paso de copia (scp) en cd.yml."
    exit 1
  fi
  ARCHIVOS_COMPOSE+=(-f "$DIRECTORIO/$override")
done
if [ -f "$COMPOSE_SIMULACRO" ]; then
  ARCHIVOS_COMPOSE+=(-f "$COMPOSE_SIMULACRO")
fi

# ¿Viene algun servicio de contenido en esta corrida? Se usa para resolver sus
# etiquetas de imagen y para saber si hay que levantar el borde.
INCLUYE_CONTENIDO=0
for par in $SERVICIOS_PUERTOS; do
  for c in $SERVICIOS_CONTENIDO; do
    if [ "${par%%:*}" = "$c" ]; then INCLUYE_CONTENIDO=1; fi
  done
done

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

# R16.5b: los contenedores que se crean AHORA llevan la hora de su creacion.
# Su entrypoint (x-arranque-escalonado en docker-compose.deploy.yml) solo
# espera turno cuando el contenedor es anterior al ultimo arranque del host,
# es decir, cuando lo levanta Docker al volver del apagado. Lo que despliega
# esta corrida arranca en el acto, aunque el CD acabe de encender el host.
export ARRANQUE_CREADO_EN="$(date +%s)"
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
  # Sale del catalogo (campo "rutaSalud"), y el guardian
  # scripts/cd/comprobar-catalogo-servicios.sh comprueba en cada pull request
  # que coincide con el server.servlet.context-path real del servicio. Es la
  # tercera version de esta funcion: la primera era una ruta fija, la segunda
  # un "case" que ya se equivoco una vez por copiar la ruta de un comentario
  # en vez de mirar la configuracion (ms-ecommerce).
  local r
  r=$(campo_de "$1" rutaSalud)
  if [ -z "$r" ] || [ "$r" = "null" ]; then r="/actuator/health"; fi
  echo "$r"
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
# 4b) ¿Y desde fuera? Un contenedor sano no es una API accesible.
#
# Un servicio puede responder 200 en su /actuator/health de localhost y aun asi
# dar 502 por el borde: puerto mal publicado, nombre de contenedor que nginx no
# resuelve, o una ruta que apunta a otro sitio. Con solo el paso 4, el CD
# declaraba "success" mientras la vista seguia rota -- que es lo que ocurrio
# durante semanas con creditos y subastas, y nadie lo vio desde el pipeline.
#
# La ruta de comprobacion de cada servicio sale del catalogo (pruebaBorde). No
# se comprueba el codigo exacto, porque depende de la credencial: 401, 403 o
# 404 significan "hay alguien ahi detras", que es justo lo que se quiere
# saber. Solo el 502 (y el 000, sin respuesta) son fallo.
#
# Se salta entero en el host de contenido, que no tiene borde.
if [ "$INCLUYE_BORDE" -eq 1 ]; then
  echo "== 4b) Comprobando que el borde llega de verdad a lo desplegado =="
  FALLO_BORDE=0
  for par in $SERVICIOS_PUERTOS; do
    servicio="${par%%:*}"
    ruta=$(campo_de "$servicio" pruebaBorde)
    if [ -z "$ruta" ] || [ "$ruta" = "null" ]; then
      echo "  $servicio: sin ruta publica en el borde (servicio entre servicios); no aplica"
      continue
    fi
    codigo=""
    for intento in 1 2 3 4 5 6; do
      codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://localhost${ruta}" || echo "000")
      case "$codigo" in
        502|000) sleep 5 ;;
        *) break ;;
      esac
    done
    if [ "$codigo" = "502" ] || [ "$codigo" = "000" ]; then
      echo "  $servicio: el borde responde $codigo en $ruta -- el contenedor esta sano pero no se llega a el"
      FALLO_BORDE=1
    else
      echo "  $servicio: el borde responde $codigo en $ruta (hay alguien detras)"
    fi
  done
  if [ "$FALLO_BORDE" -eq 1 ]; then
    echo "El contenedor esta arriba pero el borde no llega. Revisa el puerto publicado,"
    echo "el nombre del contenedor y su 'location' en infrastructure/red-balanceo/borde-dev.conf."
    exit 1
  fi
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
