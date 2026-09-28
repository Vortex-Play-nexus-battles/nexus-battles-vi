#!/usr/bin/env bash
#
# Simulacro de restauracion (B12): demuestra que el ultimo respaldo se puede
# restaurar, sin tocar ninguna base real.
#
#   simulacro-restauracion.sh --host plataforma                        # todos los volcados
#   simulacro-restauracion.sh --host plataforma --base plataforma-db/plataformadb
#   simulacro-restauracion.sh --host contenido
#
# Toma el respaldo completo mas reciente de /opt/nexus/respaldos/<host>/ (el
# que deja respaldar.sh) y:
#
# PostgreSQL — cada volcado (o solo el de --base) se restaura con pg_restore
#   en una base NUEVA del MISMO servidor, simulacro_<AAAAMMDD_HHMMSS>_<n>; se
#   cuentan sus tablas y sus filas, las tablas se comparan con las del indice
#   del volcado, y la base se borra al terminar, tambien si algo falla (trap).
#   Ninguna orden de este script nombra una base que no empiece por
#   `simulacro_`: el nombre se comprueba antes de crear, de restaurar y de
#   borrar, y si ya existiera una base con ese nombre no se toca y se para.
#
# MongoDB — el archivo se restaura en el MISMO servidor, pero cada base con
#   otro nombre: heroes -> simulacro_<AAAAMMDD_HHMMSS>_heroes (--nsFrom/--nsTo;
#   admin, config y local no se restauran). Antes, un `mongorestore --dryRun`
#   con los mismos argumentos dice a donde iria cada coleccion, y si UNA sola
#   no fuera a una base simulacro_ el simulacro se para sin escribir nada. Un
#   --dryRun a secas no basta como simulacro: solo lee la cabecera del archivo
#   (0 documentos), no los datos. Luego se cuentan colecciones y documentos, se
#   comparan con lo que dice mongorestore y las bases simulacro_ se borran,
#   tambien si algo falla (trap).
#
# Credenciales: igual que respaldar.sh, dentro del contenedor y de sus propias
# variables; nada en la linea de ordenes del host. mongosh las lee de
# process.env dentro del contenedor.
#
# Variables: RESPALDO_RAIZ (/opt/nexus/respaldos).
# Sale con 0 si todo lo restaurado cuadra, 1 si algo no, 2 si se le llamo mal.
set -euo pipefail
umask 077

RAIZ="${RESPALDO_RAIZ:-/opt/nexus/respaldos}"
HOST_LOGICO=""
SOLO=""

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST_LOGICO="${2:-}"; shift 2 ;;
        --base) SOLO="${2:-}"; shift 2 ;;
        -h | --help) sed -n '2,32p' "$0"; exit 0 ;;
        *) echo "Argumento desconocido: $1" >&2; exit 2 ;;
    esac
done
[ -n "$HOST_LOGICO" ] || HOST_LOGICO="$(hostname -s)"
case "$HOST_LOGICO" in
    "" | *[!A-Za-z0-9_-]*) echo "--host invalido: '$HOST_LOGICO'" >&2; exit 2 ;;
esac
if [ -n "$SOLO" ] && ! [[ "$SOLO" =~ ^[A-Za-z0-9_-]+/[A-Za-z0-9_-]+$ ]]; then
    echo "--base tiene la forma contenedor/base (letras, cifras, _ y -)" >&2
    exit 2
fi

if docker info >/dev/null 2>&1; then
    DOCKER=(docker)
elif sudo -n docker info >/dev/null 2>&1; then
    DOCKER=(sudo -n docker)
else
    echo "::error::No se puede hablar con Docker en este host." >&2
    exit 1
fi

DIR_HOST="$RAIZ/$HOST_LOGICO"
ULTIMO="$(find "$DIR_HOST" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
    | grep -E '^[0-9]{8}-[0-9]{4}$' | sort | tail -1 || true)"
if [ -z "$ULTIMO" ]; then
    echo "::error::No hay ningun respaldo completo en $DIR_HOST: nada que restaurar." >&2
    exit 1
fi
RESPALDO="$DIR_HOST/$ULTIMO"
echo "Simulacro de restauracion del respaldo $RESPALDO"

# Ninguna base cuyo nombre no sea simulacro_<AAAAMMDD>_<HHMMSS>[_<n>] pasa de
# aqui: ni para crearla, ni para restaurar en ella, ni para borrarla.
exigirSimulacro() {
    if ! [[ "$1" =~ ^simulacro_[0-9]{8}_[0-9]{6}(_[0-9]+)?$ ]]; then
        echo "::error::Negado: '$1' no es una base de simulacro." >&2
        exit 1
    fi
}

# psqlEn <contenedor> <base> : ejecuta el SQL de la entrada estandar.
psqlEn() {
    "${DOCKER[@]}" exec -i "$1" sh -c \
        'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec psql -X -q -A -t -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d "$1"' _ "$2"
}

TEMPORAL=""
CONTENEDOR_TEMPORAL=""
borrarTemporal() {
    if [ -n "$TEMPORAL" ]; then
        exigirSimulacro "$TEMPORAL"
        printf 'DROP DATABASE IF EXISTS "%s";\n' "$TEMPORAL" | psqlEn "$CONTENEDOR_TEMPORAL" postgres >/dev/null \
            || echo "::warning::No se pudo borrar la base temporal $TEMPORAL en $CONTENEDOR_TEMPORAL: borrarla a mano."
        TEMPORAL=""
    fi
}

# mongoshEn <contenedor> <prefijo> <contar|borrar>: cuenta las bases que
# empiezan por el prefijo (bases, colecciones, documentos) o las borra. Las
# credenciales, si las hay, las lee el propio mongosh de process.env dentro
# del contenedor; en la linea de ordenes solo va el prefijo, ya comprobado.
mongoshEn() {
    exigirSimulacro "$2"
    "${DOCKER[@]}" exec "$1" mongosh --nodb --quiet --eval "
        const prefijo = '$2_';
        const u = process.env.MONGO_INITDB_ROOT_USERNAME;
        const url = u
          ? 'mongodb://' + encodeURIComponent(u) + ':' +
            encodeURIComponent(process.env.MONGO_INITDB_ROOT_PASSWORD || '') +
            '@127.0.0.1:27017/admin?authSource=admin'
          : 'mongodb://127.0.0.1:27017/admin';
        const admin = connect(url);
        const nombres = admin.adminCommand({ listDatabases: 1, nameOnly: true }).databases
          .map((d) => d.name)
          .filter((n) => n.startsWith('simulacro_') && n.startsWith(prefijo));
        let colecciones = 0, documentos = 0;
        for (const n of nombres) {
          const base = admin.getSiblingDB(n);
          if ('$3' === 'borrar') { base.dropDatabase(); continue; }
          for (const c of base.getCollectionNames()) {
            colecciones++;
            documentos += base.getCollection(c).countDocuments({});
          }
        }
        print(nombres.length + ' ' + colecciones + ' ' + documentos);"
}

# restaurarMongo <contenedor> <prefijo> <ensayo|restaurar> < archivo
# mongorestore con cada base renombrada a <prefijo>_<base> (admin, config y
# local fuera); en "ensayo", con --dryRun. Credenciales como en respaldar.sh:
# la clave en un fichero 600 del contenedor, nunca como argumento.
restaurarMongo() {
    exigirSimulacro "$2"
    "${DOCKER[@]}" exec -i "$1" sh -c '
        set -e
        prefijo="$1"
        seco=""
        [ "$2" = ensayo ] && seco="--dryRun"
        case "$prefijo" in simulacro_[0-9]*) ;; *) echo "prefijo negado: $prefijo" >&2; exit 1 ;; esac
        if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then
            conf="$(mktemp)"
            trap "rm -f \"$conf\"" EXIT
            chmod 600 "$conf"
            clave="$(printf "%s" "${MONGO_INITDB_ROOT_PASSWORD:-}" | sed "s/[\\\\\"]/\\\\&/g")"
            printf "password: \"%s\"\n" "$clave" > "$conf"
            set -- --username "$MONGO_INITDB_ROOT_USERNAME" --authenticationDatabase admin --config "$conf"
        else
            set --
        fi
        mongorestore $seco -v "$@" --archive --gzip \
            --nsExclude "admin.*" --nsExclude "config.*" --nsExclude "local.*" \
            --nsFrom "\$db\$.\$coll\$" --nsTo "${prefijo}_\$db\$.\$coll\$" 2>&1' _ "$2" "$3"
}

PREFIJO_MONGO=""
CONTENEDOR_MONGO=""
borrarTemporalMongo() {
    if [ -n "$PREFIJO_MONGO" ]; then
        mongoshEn "$CONTENEDOR_MONGO" "$PREFIJO_MONGO" borrar >/dev/null \
            || echo "::warning::No se pudieron borrar las bases ${PREFIJO_MONGO}_* en $CONTENEDOR_MONGO: borrarlas a mano."
        PREFIJO_MONGO=""
    fi
}
trap 'borrarTemporal; borrarTemporalMongo' EXIT

fallos=0
restaurados=0
n=0

# ---------------------------------------------------------------- PostgreSQL
for volcado in $(cd "$RESPALDO" && find . -mindepth 2 -maxdepth 2 -type f -name '*.dump' | sed 's|^\./||' | sort); do
    contenedor="${volcado%%/*}"
    base="$(basename "$volcado" .dump)"
    if [ -n "$SOLO" ] && [ "$SOLO" != "$contenedor/$base" ]; then
        continue
    fi
    if ! "${DOCKER[@]}" inspect -f '{{.State.Running}}' "$contenedor" 2>/dev/null | grep -qx true; then
        echo "  FALLO $contenedor/$base: el contenedor no esta en marcha, no hay donde restaurar"
        fallos=$((fallos + 1))
        continue
    fi
    n=$((n + 1))
    TEMPORAL="simulacro_$(date -u +%Y%m%d_%H%M%S)_$n"
    CONTENEDOR_TEMPORAL="$contenedor"
    exigirSimulacro "$TEMPORAL"

    existe="$(printf "SELECT 1 FROM pg_database WHERE datname = '%s';\n" "$TEMPORAL" | psqlEn "$contenedor" postgres)"
    if [ -n "$existe" ]; then
        echo "  FALLO $contenedor/$base: ya existe una base $TEMPORAL; no se toca"
        TEMPORAL=""
        fallos=$((fallos + 1))
        continue
    fi
    printf 'CREATE DATABASE "%s";\n' "$TEMPORAL" | psqlEn "$contenedor" postgres >/dev/null

    # Tablas que el volcado dice tener (entradas TABLE del indice, sin TABLE DATA).
    esperadas="$("${DOCKER[@]}" exec -i "$contenedor" pg_restore --list < "$RESPALDO/$volcado" \
        | awk '$4 == "TABLE" && $5 != "DATA"' | wc -l | tr -d ' ')"

    if ! "${DOCKER[@]}" exec -i "$contenedor" sh -c \
            'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_restore -U "${POSTGRES_USER:-postgres}" -d "$1" --no-owner --no-privileges --exit-on-error' \
            _ "$TEMPORAL" < "$RESPALDO/$volcado"; then
        echo "  FALLO $contenedor/$base: pg_restore no pudo restaurar el volcado en $TEMPORAL"
        borrarTemporal
        fallos=$((fallos + 1))
        continue
    fi

    # Filas exactas de cada tabla, en una sola consulta (query_to_xml).
    recuento="$(psqlEn "$contenedor" "$TEMPORAL" <<'SQL'
SELECT count(*) || ' ' || coalesce(sum((xpath('/row/c/text()',
       query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
                    false, true, '')))[1]::text::bigint), 0)
  FROM information_schema.tables
 WHERE table_type = 'BASE TABLE'
   AND table_schema NOT IN ('pg_catalog', 'information_schema');
SQL
)"
    tablas="${recuento%% *}"
    filas="${recuento##* }"
    borrarTemporal

    if [ "$tablas" = "$esperadas" ]; then
        echo "  ok    $contenedor/$base: $tablas tablas y $filas filas restauradas (el indice del volcado dice $esperadas tablas)"
        restaurados=$((restaurados + 1))
    else
        echo "  FALLO $contenedor/$base: se restauraron $tablas tablas y el indice del volcado dice $esperadas"
        fallos=$((fallos + 1))
    fi
done

# ------------------------------------------------------------------- MongoDB
for archivo in $(cd "$RESPALDO" && find . -mindepth 2 -maxdepth 2 -type f -name 'mongodump.archive.gz' | sed 's|^\./||' | sort); do
    contenedor="${archivo%%/*}"
    if [ -n "$SOLO" ] && [ "${SOLO%%/*}" != "$contenedor" ]; then
        continue
    fi
    if ! "${DOCKER[@]}" inspect -f '{{.State.Running}}' "$contenedor" 2>/dev/null | grep -qx true; then
        echo "  FALLO $contenedor: el contenedor no esta en marcha, no hay donde restaurar"
        fallos=$((fallos + 1))
        continue
    fi
    prefijo="simulacro_$(date -u +%Y%m%d_%H%M%S)"
    exigirSimulacro "$prefijo"
    if [ "$(mongoshEn "$contenedor" "$prefijo" contar | cut -d' ' -f1)" != "0" ]; then
        echo "  FALLO $contenedor: ya hay bases ${prefijo}_*; no se tocan"
        fallos=$((fallos + 1))
        continue
    fi

    # 1) Ensayo: a donde iria cada coleccion. Si una sola no va a una base
    #    del simulacro, se para aqui, antes de escribir nada.
    if ! ensayo="$(restaurarMongo "$contenedor" "$prefijo" ensayo < "$RESPALDO/$archivo")"; then
        echo "  FALLO $contenedor: mongorestore --dryRun no pudo leer el archivo"
        printf '%s\n' "$ensayo" | tail -5 | sed 's/^/        /'
        fallos=$((fallos + 1))
        continue
    fi
    destinos="$(printf '%s\n' "$ensayo" | sed -n 's/.*bson to restore to `\([^`]*\)`.*/\1/p')"
    colecciones_archivo="$(printf '%s\n' "$destinos" | grep -c . || true)"
    ajenos="$(printf '%s\n' "$destinos" | grep -v "^${prefijo}_" | grep . || true)"
    if [ "$colecciones_archivo" -eq 0 ] || [ -n "$ajenos" ]; then
        echo "  FALLO $contenedor: el ensayo no manda todo a ${prefijo}_* (colecciones: $colecciones_archivo; fuera: ${ajenos:-ninguna}); no se restaura"
        fallos=$((fallos + 1))
        continue
    fi

    # 2) Restauracion de verdad, con los mismos argumentos.
    PREFIJO_MONGO="$prefijo"
    CONTENEDOR_MONGO="$contenedor"
    if ! salida="$(restaurarMongo "$contenedor" "$prefijo" restaurar < "$RESPALDO/$archivo")"; then
        echo "  FALLO $contenedor: mongorestore no pudo restaurar el archivo en ${prefijo}_*"
        printf '%s\n' "$salida" | tail -5 | sed 's/^/        /'
        borrarTemporalMongo
        fallos=$((fallos + 1))
        continue
    fi
    restaurados_segun_mongorestore="$(printf '%s\n' "$salida" | sed -n 's/.*\t\([0-9][0-9]*\) document(s) restored successfully.*/\1/p' | tail -1)"
    fallidos="$(printf '%s\n' "$salida" | sed -n 's/.* \([0-9][0-9]*\) document(s) failed to restore.*/\1/p' | tail -1)"
    read -r bases colecciones documentos <<< "$(mongoshEn "$contenedor" "$prefijo" contar)"
    borrarTemporalMongo

    if [ "$colecciones" = "$colecciones_archivo" ] && [ "${fallidos:-0}" = "0" ] \
       && [ "$documentos" = "${restaurados_segun_mongorestore:-x}" ]; then
        echo "  ok    $contenedor: $bases bases, $colecciones colecciones y $documentos documentos restaurados en ${prefijo}_* y borrados"
        restaurados=$((restaurados + 1))
    else
        echo "  FALLO $contenedor: no cuadra: $colecciones colecciones de $colecciones_archivo, $documentos documentos contados y ${restaurados_segun_mongorestore:-?} segun mongorestore (${fallidos:-?} fallidos)"
        fallos=$((fallos + 1))
    fi
done

if [ "$restaurados" -eq 0 ] && [ "$fallos" -eq 0 ]; then
    echo "::error::El respaldo $RESPALDO no tiene nada que restaurar${SOLO:+ para $SOLO}."
    exit 1
fi
if [ "$fallos" -gt 0 ]; then
    echo "::error::Simulacro de $HOST_LOGICO: $fallos fallo(s), $restaurados correcto(s)."
    exit 1
fi
echo "Simulacro de $HOST_LOGICO: $restaurados restauracion(es) correcta(s); ninguna base real tocada."
