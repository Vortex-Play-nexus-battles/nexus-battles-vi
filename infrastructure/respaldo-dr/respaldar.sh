#!/usr/bin/env bash
#
# Respaldo de las bases de datos de un host de DEV, en el propio host (B12).
#
#   respaldar.sh --host plataforma      # o --host contenido
#
# Lo lanza .github/workflows/respaldo-dev.yml (una vez al dia y a demanda) por
# SSH, como cd.yml. Tambien se puede lanzar a mano en el host.
#
# Que hace, en este orden:
#   1. Comprueba que queda sitio en disco (RESPALDO_ESPACIO_MINIMO_MB): un
#      respaldo que llena el disco tumba la base que queria proteger.
#   2. PostgreSQL: `pg_dump -Fc` de cada base de cada contenedor postgres en
#      marcha. Formato custom: comprimido y restaurable tabla a tabla. Cada
#      volcado se comprueba leyendo su indice con `pg_restore --list`.
#   3. MongoDB: `mongodump --archive --gzip` de cada contenedor mongo en
#      marcha, todas sus bases, comprobado con `gzip -t`.
#   4. MANIFIESTO con el sha256 y el tamano de cada fichero. El directorio se
#      prepara como .<sello>.parcial y solo al final pasa a llamarse <sello>
#      (AAAAMMDD-HHMM, en UTC): un respaldo a medias nunca parece completo.
#   5. Retencion: borra los respaldos de mas de RESPALDO_RETENCION_DIAS (7)
#      dias. Nunca el mas reciente ni el ultimo sin fallos, aunque sean viejos:
#      si los respaldos llevan una semana fallando, el ultimo bueno es justo el
#      que hace falta.
#
# Quedan en /opt/nexus/respaldos/<host>/<sello>/<contenedor>/:
#   <base>.dump                 (PostgreSQL, uno por base)
#   mongodump.archive.gz        (MongoDB, todas las bases del contenedor)
#
# Credenciales: ninguna pasa por la linea de ordenes del host ni se imprime.
# Cada volcado corre DENTRO de su contenedor (`docker exec ... sh -c`) y lee
# usuario y clave de las variables que el contenedor ya tiene
# (POSTGRES_USER/POSTGRES_PASSWORD, MONGO_INITDB_ROOT_USERNAME/PASSWORD). La
# clave de Postgres va en PGPASSWORD; la de Mongo, si algun dia tiene, en un
# fichero de configuracion 600 dentro del contenedor, nunca como argumento.
#
# Los respaldos viven en el MISMO disco que los datos: protegen de un error
# logico (una migracion mala, un borrado, un fallo de un servicio), no de
# perder la instancia. Ver infrastructure/respaldo-dr/README.md.
#
# Variables (todas opcionales):
#   RESPALDO_RAIZ                 /opt/nexus/respaldos
#   RESPALDO_RETENCION_DIAS       7
#   RESPALDO_ESPACIO_MINIMO_MB    1024
#   RESPALDO_CONTENEDORES         vacio = todos los postgres/mongo en marcha;
#                                 con nombres (separados por espacios), solo
#                                 esos: para respaldar una base concreta a mano
#                                 o para probar el script en un portatil sin
#                                 tocar otras bases que haya en marcha.
#
# Sale con 0 si todo se respaldo, 1 si algo fallo (el resto se conserva y el
# MANIFIESTO dice que falto) y 2 si se le llamo mal.
set -euo pipefail
umask 077

RAIZ="${RESPALDO_RAIZ:-/opt/nexus/respaldos}"
RETENCION_DIAS="${RESPALDO_RETENCION_DIAS:-7}"
ESPACIO_MINIMO_MB="${RESPALDO_ESPACIO_MINIMO_MB:-1024}"
HOST_LOGICO=""

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST_LOGICO="${2:-}"; shift 2 ;;
        -h | --help) sed -n '2,50p' "$0"; exit 0 ;;
        *) echo "Argumento desconocido: $1 (uso: respaldar.sh --host plataforma|contenido)" >&2; exit 2 ;;
    esac
done
[ -n "$HOST_LOGICO" ] || HOST_LOGICO="$(hostname -s)"
case "$HOST_LOGICO" in
    "" | *[!A-Za-z0-9_-]*) echo "--host invalido: '$HOST_LOGICO'" >&2; exit 2 ;;
esac
case "$RETENCION_DIAS$ESPACIO_MINIMO_MB" in
    *[!0-9]*) echo "RESPALDO_RETENCION_DIAS y RESPALDO_ESPACIO_MINIMO_MB tienen que ser enteros" >&2; exit 2 ;;
esac

# Docker como este usuario (grupo docker, lo normal en los hosts) o con sudo
# sin contrasena, igual que comprobar-capacidad.sh.
if docker info >/dev/null 2>&1; then
    DOCKER=(docker)
elif sudo -n docker info >/dev/null 2>&1; then
    DOCKER=(sudo -n docker)
else
    echo "::error::No se puede hablar con Docker en este host (ni como $(id -un) ni con sudo -n)." >&2
    exit 1
fi

# Los contenedores de bases que declaran los compose de cada host, para decir
# en voz alta cual no esta en marcha. Lo que se respalda NO sale de esta lista
# sino de `docker ps`: cualquier contenedor postgres o mongo en marcha, este o
# no aqui (una base nueva queda respaldada el primer dia, no cuando alguien se
# acuerde de anadirla).
#   plataforma-db     docker-compose.yml + docker-compose.deploy.yml (plataformadb,
#                     con un esquema por servicio de plataforma)
#   identidad-db      docker-compose.cuentas.yml
#   cumplimiento-db, ecommerce-db, finanzas-db, subastas-db, chatbot-db
#                     docker-compose.ms-*.yml (solo si ese servicio cabe y esta
#                     desplegado: ver infrastructure/despliegue/CAPACIDAD.md)
#   contenido-mongo   docker-compose.contenido.yml (heroes, inventario, productos...)
# Obligatorias son las del nucleo que scripts/cd/apagar.sh protege: si una no
# esta en marcha, algo va mal y el respaldo sale en rojo. Las opcionales solo
# se anuncian.
case "$HOST_LOGICO" in
    plataforma)
        OBLIGATORIOS="plataforma-db identidad-db"
        OPCIONALES="cumplimiento-db ecommerce-db finanzas-db subastas-db chatbot-db" ;;
    contenido)
        OBLIGATORIOS="contenido-mongo"
        OPCIONALES="" ;;
    *)
        OBLIGATORIOS=""
        OPCIONALES="" ;;
esac

en_marcha="$("${DOCKER[@]}" ps --format '{{.Names}} {{.Image}}')"
if [ -n "${RESPALDO_CONTENEDORES:-}" ]; then
    en_marcha="$(printf '%s\n' "$en_marcha" | awk -v solo=" $RESPALDO_CONTENEDORES " 'index(solo, " " $1 " ") > 0')"
    OBLIGATORIOS="$RESPALDO_CONTENEDORES"
    OPCIONALES=""
fi
POSTGRES="$(printf '%s\n' "$en_marcha" | awk '$2 ~ /(^|\/)postgres(:|@|$)/ {print $1}' | sort)"
MONGO="$(printf '%s\n' "$en_marcha" | awk '$2 ~ /(^|\/)mongo(:|@|$)/ {print $1}' | sort)"

DIR_HOST="$RAIZ/$HOST_LOGICO"
mkdir -p "$DIR_HOST"
libre_mb="$(df -Pm "$DIR_HOST" | awk 'NR == 2 {print $4}')"
if [ "${libre_mb:-0}" -lt "$ESPACIO_MINIMO_MB" ]; then
    echo "::error::Quedan ${libre_mb:-?} MB libres en $DIR_HOST y el minimo es $ESPACIO_MINIMO_MB: no se respalda para no llenar el disco." >&2
    exit 1
fi

SELLO="$(date -u +%Y%m%d-%H%M)"
DESTINO="$DIR_HOST/$SELLO"
PARCIAL="$DIR_HOST/.$SELLO.parcial"
if [ -e "$DESTINO" ]; then
    echo "Ya hay un respaldo $DESTINO de este mismo minuto; no se pisa." >&2
    exit 1
fi
rm -rf "$PARCIAL"
mkdir -p "$PARCIAL"

fallos=0
faltas=""

tamano() { du -sh "$1" | cut -f1; }

echo "Respaldo de $HOST_LOGICO en $DESTINO (${libre_mb} MB libres)"

for esperado in $OBLIGATORIOS; do
    if ! printf '%s\n%s\n' "$POSTGRES" "$MONGO" | grep -qx "$esperado"; then
        echo "  FALLO $esperado: tendria que estar en marcha en este host y no lo esta"
        fallos=$((fallos + 1)); faltas="$faltas $esperado"
    fi
done
for esperado in $OPCIONALES; do
    if ! printf '%s\n%s\n' "$POSTGRES" "$MONGO" | grep -qx "$esperado"; then
        echo "  --    $esperado: no esta en marcha en este host (no desplegado aqui): se omite"
    fi
done

# ---------------------------------------------------------------- PostgreSQL
for contenedor in $POSTGRES; do
    mkdir -p "$PARCIAL/$contenedor"
    if ! bases="$(printf '%s\n' "SELECT datname FROM pg_database WHERE NOT datistemplate AND datname <> 'postgres' ORDER BY 1;" \
        | "${DOCKER[@]}" exec -i "$contenedor" sh -c \
            'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec psql -X -q -A -t -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d postgres')"; then
        echo "  FALLO $contenedor: no se pudieron listar sus bases"
        fallos=$((fallos + 1)); faltas="$faltas $contenedor"
        continue
    fi
    if [ -z "$bases" ]; then
        echo "  --    $contenedor: sin bases de aplicacion"
        continue
    fi
    for base in $bases; do
        case "$base" in
            *[!A-Za-z0-9_-]*)
                echo "  FALLO $contenedor/$base: nombre de base inesperado, no se respalda"
                fallos=$((fallos + 1)); faltas="$faltas $contenedor/$base"
                continue ;;
        esac
        fichero="$PARCIAL/$contenedor/$base.dump"
        if "${DOCKER[@]}" exec "$contenedor" sh -c \
               'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_dump -U "${POSTGRES_USER:-postgres}" -Fc "$1"' _ "$base" > "$fichero" \
           && [ -s "$fichero" ] \
           && "${DOCKER[@]}" exec -i "$contenedor" pg_restore --list < "$fichero" > /dev/null; then
            echo "  ok    $contenedor/$base.dump ($(tamano "$fichero"))"
        else
            echo "  FALLO $contenedor/$base: pg_dump o la lectura de su indice fallaron"
            rm -f "$fichero"
            fallos=$((fallos + 1)); faltas="$faltas $contenedor/$base"
        fi
    done
done

# ------------------------------------------------------------------- MongoDB
# Sin autenticacion (contenido-mongo hoy): mongodump a secas. Con ella, el
# usuario raiz del propio contenedor y su clave en un fichero 600 que se borra
# al salir: `--config` es la forma de mongodump de no llevar la clave en la
# linea de ordenes, que `ps` ensena a cualquiera en el host.
VOLCADO_MONGO='
set -e
if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then
    conf="$(mktemp)"
    trap "rm -f \"$conf\"" EXIT
    chmod 600 "$conf"
    clave="$(printf "%s" "${MONGO_INITDB_ROOT_PASSWORD:-}" | sed "s/[\\\\\"]/\\\\&/g")"
    printf "password: \"%s\"\n" "$clave" > "$conf"
    mongodump --quiet --archive --gzip --username "$MONGO_INITDB_ROOT_USERNAME" \
        --authenticationDatabase admin --config "$conf"
else
    mongodump --quiet --archive --gzip
fi'
for contenedor in $MONGO; do
    mkdir -p "$PARCIAL/$contenedor"
    fichero="$PARCIAL/$contenedor/mongodump.archive.gz"
    if "${DOCKER[@]}" exec "$contenedor" sh -c "$VOLCADO_MONGO" > "$fichero" \
       && [ -s "$fichero" ] && gzip -t "$fichero"; then
        echo "  ok    $contenedor/mongodump.archive.gz ($(tamano "$fichero"))"
    else
        echo "  FALLO $contenedor: mongodump o su comprobacion fallaron"
        rm -f "$fichero"
        fallos=$((fallos + 1)); faltas="$faltas $contenedor"
    fi
done

if [ -z "$POSTGRES$MONGO" ]; then
    echo "  --    no hay ningun contenedor postgres ni mongo en marcha"
fi

# ---------------------------------------------------------------- Manifiesto
{
    echo "host: $HOST_LOGICO"
    echo "sello_utc: $SELLO"
    echo "fallos: $fallos${faltas:+ (${faltas# })}"
    echo "# sha256  bytes  fichero"
    (cd "$PARCIAL" && find . -type f ! -name MANIFIESTO | sort | while read -r f; do
        printf '%s  %s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$(wc -c < "$f" | tr -d ' ')" "${f#./}"
    done)
} > "$PARCIAL/MANIFIESTO"
mv "$PARCIAL" "$DESTINO"
echo "Respaldo publicado: $DESTINO ($(tamano "$DESTINO"))"

# ----------------------------------------------------------------- Retencion
# Solo directorios con forma de sello; lo demas de la carpeta no se toca.
LIMITE="$(date -u -d "-$RETENCION_DIAS days" +%Y%m%d-%H%M)"
SELLOS="$(find "$DIR_HOST" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | grep -E '^[0-9]{8}-[0-9]{4}$' | sort || true)"
MAS_RECIENTE="$(printf '%s\n' "$SELLOS" | tail -1)"
# Y tampoco el ultimo sin fallos: si una base lleva dias sin poder volcarse,
# su ultima copia buena esta ahi.
MAS_RECIENTE_COMPLETO=""
for sello in $SELLOS; do
    if grep -qx 'fallos: 0' "$DIR_HOST/$sello/MANIFIESTO" 2>/dev/null; then
        MAS_RECIENTE_COMPLETO="$sello"
    fi
done
for sello in $SELLOS; do
    if [ "$sello" != "$MAS_RECIENTE" ] && [ "$sello" != "$MAS_RECIENTE_COMPLETO" ] \
       && [[ "$sello" < "$LIMITE" ]]; then
        rm -rf "${DIR_HOST:?}/${sello:?}"
        echo "  retencion: borrado $sello (mas de $RETENCION_DIAS dias)"
    fi
done
# Los .parcial de corridas que murieron a medias, pasado un dia.
find "$DIR_HOST" -mindepth 1 -maxdepth 1 -type d -name '.*.parcial' -mtime +0 -exec rm -rf {} + 2>/dev/null || true

if [ "$fallos" -gt 0 ]; then
    echo "::error::Respaldo de $HOST_LOGICO con $fallos fallo(s):$faltas. Lo demas quedo en $DESTINO."
    exit 1
fi
echo "Respaldo de $HOST_LOGICO completo."
