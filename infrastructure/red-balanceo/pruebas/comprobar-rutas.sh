#!/usr/bin/env bash
#
# Comprueba a que servicio y con que ruta llega cada peticion del borde.
#
#   cd infrastructure/red-balanceo/pruebas
#   docker compose up -d --wait
#   ./comprobar-rutas.sh
#   docker compose down -v
#
# Los servicios son ecos: responden "<nombre> <metodo> <ruta que recibieron>".
# Lo que se fija aqui es el REPARTO, no el comportamiento de cada servicio.
#
# ## Por que esto corre ahora en integracion continua
#
# Este guardian existia desde P3.1 y **nadie lo ejecutaba**. En esas dos
# semanas se colaron dos defectos que habria atrapado en el acto:
#
#   * `/api/v1/admin/auditoria`, un prefijo simple tapado por la regex de
#     `/admin`, dandose por arreglado en un comentario que describia mal la
#     precedencia de nginx;
#   * `/api/v1/cofres`, que sencillamente no tenia `location` y caia en el 404
#     generico, con la vista Mis cofres rota.
#
# Un guardian que no corre no es un guardian. Desde R8.4 lo ejecuta
# `.github/workflows/ci.yml` en cada cambio de `infrastructure/red-balanceo/`.
set -uo pipefail

BORDE="${BORDE:-http://localhost:8099}"
CONF="${CONF:-$(dirname "$0")/../borde-dev.conf}"
fallos=0

# comprobar <metodo> <ruta> <respuesta esperada>
comprobar() {
    local metodo="$1" ruta="$2" esperado="$3"
    local obtenido
    obtenido="$(curl -s -X "$metodo" "$BORDE$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok    %-6s %-40s -> %s\n' "$metodo" "$ruta" "$obtenido"
    else
        printf '  FALLA %-6s %-40s\n        esperado: %s\n        obtenido: %s\n' \
            "$metodo" "$ruta" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}

# codigo <metodo> <ruta> <codigo http esperado>
# Para las rutas que el borde contesta el mismo, sin reenviar a nadie.
codigo() {
    local metodo="$1" ruta="$2" esperado="$3"
    local obtenido
    obtenido="$(curl -s -o /dev/null -w '%{http_code}' -X "$metodo" "$BORDE$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok    %-6s %-40s -> %s\n' "$metodo" "$ruta" "$obtenido"
    else
        printf '  FALLA %-6s %-40s  esperado %s, obtenido %s\n' \
            "$metodo" "$ruta" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}

# enConfiguracion <descripcion> <patron grep -E>
# Para lo que el banco no puede probar con trafico: los upstreams por IP.
enConfiguracion() {
    local descripcion="$1" patron="$2"
    if grep -Eq "$patron" "$CONF"; then
        printf '  ok    %s\n' "$descripcion"
    else
        printf '  FALLA %s\n        no aparece en %s: %s\n' "$descripcion" "$CONF" "$patron"
        fallos=$((fallos + 1))
    fi
}

# fueraDeConfiguracion <descripcion> <patron grep -E>
# Lo contrario: lo que NO puede volver a aparecer en las lineas efectivas del
# fichero. Los comentarios no cuentan: el fichero explica, a proposito, lo que
# hubo y por que se quito.
fueraDeConfiguracion() {
    local descripcion="$1" patron="$2"
    if grep -v '^[[:space:]]*#' "$CONF" | grep -Eq "$patron"; then
        printf '  FALLA %s\n        aparece en %s: %s\n' "$descripcion" "$CONF" "$patron"
        fallos=$((fallos + 1))
    else
        printf '  ok    %s\n' "$descripcion"
    fi
}

echo "Cuentas — identidad, cumplimiento, finanzas y subastas"
comprobar POST /api/v1/auth/login          "identidad POST /api/v1/auth/login"
# B1 — verificacion del correo, recuperacion con preguntas y preguntas de
# seguridad (identidad 2.0.0): las cuatro son de ms-identidad por la regex de
# /auth, y la interfaz las llama por el borde.
comprobar POST /api/v1/auth/verificacion/confirmacion \
                                           "identidad POST /api/v1/auth/verificacion/confirmacion"
comprobar POST /api/v1/auth/verificacion/reenvio \
                                           "identidad POST /api/v1/auth/verificacion/reenvio"
comprobar POST /api/v1/auth/restablecer/preguntas \
                                           "identidad POST /api/v1/auth/restablecer/preguntas"
comprobar PUT  /api/v1/auth/preguntas-seguridad \
                                           "identidad PUT /api/v1/auth/preguntas-seguridad"
comprobar GET  /api/v1/perfiles/yo         "identidad GET /api/v1/perfiles/yo"
comprobar GET  /api/v1/rbac/roles          "identidad GET /api/v1/rbac/roles"
comprobar GET  /api/v1/admin/usuarios      "identidad GET /api/v1/admin/usuarios"
# R8.4 — la que llevaba dos semanas cayendo en IDENTIDAD por la regex de
# /admin. Si alguien quita el `^~` de borde-dev.conf, esta linea se pone roja.
comprobar GET  /api/v1/admin/auditoria     "cumplimiento GET /api/v1/admin/auditoria"
comprobar POST /api/v1/admin/auditoria/eventos \
                                           "cumplimiento POST /api/v1/admin/auditoria/eventos"
comprobar GET  /api/v1/creditos/saldo      "finanzas GET /api/v1/creditos/saldo"
comprobar GET  /api/v1/transacciones       "finanzas GET /api/v1/transacciones"
# R8.4 — no tenia location ninguna; la vista Mis cofres caia en el 404 generico.
comprobar GET  /api/v1/cofres/mios         "finanzas GET /api/v1/cofres/mios"
comprobar GET  /api/v1/subastas            "subastas GET /api/v1/subastas"
comprobar GET  /api/v1/mis-pujas           "subastas GET /api/v1/mis-pujas"
# R16.22 — ms-chatbot no tenia location: caia en el 404 generico.
comprobar GET  /api/v1/chat/historial      "chatbot GET /api/v1/chat/historial"
comprobar POST /api/v1/chat/mensajes       "chatbot POST /api/v1/chat/mensajes"
comprobar GET  /api/v1/chatbot/admin/analiticas "chatbot GET /api/v1/chatbot/admin/analiticas"

echo
echo "Carrito — ms-ecommerce vive bajo /ecommerce, el navegador no se entera"
# La de abajo es la que estaba rota: el sufijo se perdia por el proxy_pass
# con variable Y uri, y anadir al carrito nunca llegaba a su endpoint.
comprobar GET  /api/v1/carrito             "ecommerce GET /ecommerce/api/v1/carrito"
comprobar POST /api/v1/carrito/items       "ecommerce POST /ecommerce/api/v1/carrito/items"
comprobar DELETE /api/v1/carrito/items/x   "ecommerce DELETE /ecommerce/api/v1/carrito/items/x"
# B5 (ecommerce-carrito 1.4.0): la cantidad de una linea y la moneda en la consulta.
comprobar PUT  /api/v1/carrito/items/x/cantidad \
                                           "ecommerce PUT /ecommerce/api/v1/carrito/items/x/cantidad"
comprobar GET  "/api/v1/carrito?moneda=USD" "ecommerce GET /ecommerce/api/v1/carrito?moneda=USD"

echo
echo "Compra — lista de deseos, pago y ordenes de ms-ecommerce (B5)"
# Las tres comparten una regex del borde. Si otra regex anterior se las
# quitara (el defecto de /admin/auditoria), el pago caeria en el 404 generico
# y la tienda diria «no se pudo pagar» sin que nada llegara a ms-ecommerce.
comprobar GET    /api/v1/lista-deseos      "ecommerce GET /ecommerce/api/v1/lista-deseos"
comprobar PUT    /api/v1/lista-deseos/p-1  "ecommerce PUT /ecommerce/api/v1/lista-deseos/p-1"
comprobar DELETE /api/v1/lista-deseos/p-1  "ecommerce DELETE /ecommerce/api/v1/lista-deseos/p-1"
comprobar POST   /api/v1/checkout          "ecommerce POST /ecommerce/api/v1/checkout"
comprobar GET    /api/v1/ordenes           "ecommerce GET /ecommerce/api/v1/ordenes"
comprobar GET    /api/v1/ordenes/o-1       "ecommerce GET /ecommerce/api/v1/ordenes/o-1"

echo
echo "Productos — un prefijo, un dueno: el catalogo, para todos los metodos (#421)"
# Hasta R16 el GET EXACTO de esta ruta se lo llevaba la vitrina de ms-ecommerce:
# el borde repartia por metodo, con dos `map $request_method` y una
# `location =`. productos.yaml 1.2.0 (#687) publica ahi el listado del
# catalogo, asi que el prefijo es entero de contenido/productos. La primera
# linea es la que cambio de dueno; si alguien devuelve el reparto por metodo,
# se pone roja.
#
# Antes el POST no se podia probar con trafico: su destino es 34.193.90.11:8103
# y la peticion salia hacia el host de contenido REAL (el mecanismo de #614).
# Ahora el banco sustituye ESE destino por el eco `srv-productos` (ver
# `borde-conf` en docker-compose.yml) y las cuatro van al eco. Que el fichero de
# despliegue siga apuntando a la IP se comprueba aparte, mas abajo.
comprobar GET  /api/v1/productos           "productos GET /api/v1/productos"
comprobar POST /api/v1/productos           "productos POST /api/v1/productos"
comprobar GET  /api/v1/productos/p-1       "productos GET /api/v1/productos/p-1"
comprobar GET  "/api/v1/productos?page=0&size=20" \
                                           "productos GET /api/v1/productos?page=0&size=20"

echo
echo "Vitrina — ms-ecommerce con prefijo propio; la consulta llega entera (R16)"
# Misma reescritura que el carrito. La pagina, el tamano y el tipo viajan en
# la consulta: si el borde la perdiera, la tienda ensenaria siempre la primera
# pagina, y sin error ninguno que lo delatara.
comprobar GET  "/api/v1/vitrina?page=0"    "ecommerce GET /ecommerce/api/v1/vitrina?page=0"
comprobar GET  "/api/v1/vitrina?page=1&size=16&tipo=ARMA" \
                                           "ecommerce GET /ecommerce/api/v1/vitrina?page=1&size=16&tipo=ARMA"
# B5: moneda, filtros y busqueda viajan igual; perderlos ensenaria pesos y todo el catalogo.
comprobar GET  "/api/v1/vitrina?moneda=USD&enPromocion=true&busqueda=espada" \
                                           "ecommerce GET /ecommerce/api/v1/vitrina?moneda=USD&enPromocion=true&busqueda=espada"

echo
echo "Plataforma — los ocho servicios del bloque"
comprobar GET  /api/v1/salas               "salas GET /api/v1/salas"
comprobar GET  /api/v1/salas/s-1           "salas GET /api/v1/salas/s-1"
comprobar GET  /api/v1/partidas/p-1        "salas GET /api/v1/partidas/p-1"
# B6 — mensajes privados: mismo servicio, prefijo propio. Sin su location caia
# en el 404 generico y la pestana de mensajes privados del chat no cargaba nada.
comprobar GET  /api/v1/mensajes-directos/conversaciones \
                                           "salas GET /api/v1/mensajes-directos/conversaciones"
comprobar POST /api/v1/mensajes-directos/conversaciones/u-1/mensajes \
                                           "salas POST /api/v1/mensajes-directos/conversaciones/u-1/mensajes"
comprobar GET  /api/v1/torneos             "torneos GET /api/v1/torneos"
comprobar GET  /api/v1/parametros          "parametros GET /api/v1/parametros"
comprobar GET  /api/v1/lista-negra         "moderacion GET /api/v1/lista-negra"
comprobar GET  /api/v1/sanciones           "moderacion GET /api/v1/sanciones"
comprobar GET  /api/v1/apelaciones         "moderacion GET /api/v1/apelaciones"
comprobar GET  /api/v1/users/u-1/notifications \
                                           "notificaciones GET /api/v1/users/u-1/notifications"
comprobar GET  /api/v1/products/p-1/comments \
                                           "comentarios GET /api/v1/products/p-1/comments"
comprobar GET  /api/v1/latencia            "metricas GET /api/v1/latencia"
comprobar GET  /api/v1/disponibilidad      "metricas GET /api/v1/disponibilidad"
comprobar GET  /api/v1/tecnicas            "metricas GET /api/v1/tecnicas"
# Las dos lineas siguientes son el par que importa, y por eso van juntas:
# /api/v1/moderacion es de metricas (sus agregados, HU-MET) y
# /api/v1/comentarios/moderacion es la cola de comentarios de R10.1. Se
# parecen a proposito: si alguien colgara la cola de /api/v1/moderacion, este
# guion lo detectaria porque la primera linea empezaria a irse a comentarios.
comprobar GET  /api/v1/moderacion          "metricas GET /api/v1/moderacion"
comprobar GET  /api/v1/comentarios/moderacion \
                                           "comentarios GET /api/v1/comentarios/moderacion"
# B3 — calificaciones e imagenes de comentarios, en los prefijos que ya eran de
# comentarios. Las imagenes tienen location propia (limite de cuerpo): si se
# perdiera, seguirian llegando por /api/v1/comentarios, pero sin su limite.
comprobar GET  /api/v1/products/p-1/rating "comentarios GET /api/v1/products/p-1/rating"
comprobar GET  /api/v1/products/p-1/rating/mia \
                                           "comentarios GET /api/v1/products/p-1/rating/mia"
comprobar POST /api/v1/comentarios/imagenes \
                                           "comentarios POST /api/v1/comentarios/imagenes"
comprobar GET  /api/v1/comentarios/imagenes/i-1 \
                                           "comentarios GET /api/v1/comentarios/imagenes/i-1"

echo
echo "Limites de cuerpo (B3) — las imagenes de comentarios cortan a 3m en el borde"

# cuerpo <ruta> <bytes> <codigo esperado>
cuerpo() {
    local ruta="$1" tamano="$2" esperado="$3"
    local obtenido
    obtenido="$(head -c "$tamano" /dev/zero | curl -s -o /dev/null -w '%{http_code}' \
        -X POST -H 'Content-Type: application/octet-stream' --data-binary @- "$BORDE$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok    POST   %-40s %8s bytes -> %s\n' "$ruta" "$tamano" "$obtenido"
    else
        printf '  FALLA POST   %-40s %8s bytes  esperado %s, obtenido %s\n' \
            "$ruta" "$tamano" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}

# 2,5 MB pasa (el servicio corta a los 2 MB con su problem detail); 3,5 MB lo
# corta el borde. El techo general de 10m sigue valiendo para el resto.
cuerpo /api/v1/comentarios/imagenes 2621440 200
cuerpo /api/v1/comentarios/imagenes 3670016 413
cuerpo /api/v1/perfiles/yo          3670016 200

echo
echo "Mailpit — la bandeja de pruebas que leen el banco E2E y los canarios (B1)"
# Desde B1 las cuentas nuevas se verifican con un codigo que llega por correo,
# y las pruebas lo leen de la API de Mailpit por el borde, igual en el banco
# E2E que en DEV (tests/e2e/ayudantes/correo.js). La ruta viaja entera, con
# su consulta: sin ella, la busqueda devolveria todos los correos.
comprobar GET  "/mailpit/api/v1/search?query=to:ana@nexus.test" \
                                           "mailpit GET /mailpit/api/v1/search?query=to:ana@nexus.test"
comprobar GET  /mailpit/api/v1/message/abc "mailpit GET /mailpit/api/v1/message/abc"

echo
echo "Lo que el borde contesta el mismo"
codigo GET /salud-borde   200
# correo es servicio-entre-servicios (ADR-005): desde fuera NO se expone.
codigo GET /api/v1/correos          404
# prefijo inventado: tiene que caer en el 404 de "prefijo sin servicio", no
# colarse en ningun upstream.
codigo GET /api/v1/no-existe-esto   404

echo
echo "Direcciones limpias (R17) — se sirven, se anuncian y las antiguas redirigen"

# redirige <ruta> <location esperada>
redirige() {
    local ruta="$1" esperado="$2"
    local cabeceras codigoHttp destino
    cabeceras="$(curl -s -o /dev/null -D - "$BORDE$ruta" | tr -d '\r')"
    codigoHttp="$(printf '%s\n' "$cabeceras" | head -1 | awk '{print $2}')"
    destino="$(printf '%s\n' "$cabeceras" | grep -i '^location:' | sed 's/^[Ll]ocation: *//')"
    destino="${destino#"$BORDE"}"
    if [ "$codigoHttp" = "302" ] && [ "$destino" = "$esperado" ]; then
        printf '  ok    GET    %-40s -> 302 %s\n' "$ruta" "$destino"
    else
        printf '  FALLA GET    %-40s\n        esperado: 302 %s\n        obtenido: %s %s\n' \
            "$ruta" "$esperado" "$codigoHttp" "$destino"
        fallos=$((fallos + 1))
    fi
}

# sirve <ruta> <descripcion> <patron grep -E que debe aparecer en el cuerpo>
sirve() {
    local ruta="$1" descripcion="$2" patron="$3"
    local cuerpo estado
    estado="$(curl -s -o /dev/null -w '%{http_code}' "$BORDE$ruta")"
    cuerpo="$(curl -s "$BORDE$ruta")"
    if [ "$estado" = "200" ] && printf '%s' "$cuerpo" | grep -Eq "$patron"; then
        printf '  ok    GET    %-40s -> 200, %s\n' "$ruta" "$descripcion"
    else
        printf '  FALLA GET    %-40s -> %s, sin %s\n' "$ruta" "$estado" "$descripcion"
        fallos=$((fallos + 1))
    fi
}

# cabecera <ruta> <nombre> <patron grep -E del valor>; con patron vacio, que NO este
cabecera() {
    local ruta="$1" nombre="$2" patron="$3"
    local valor
    valor="$(curl -s -o /dev/null -D - "$BORDE$ruta" | tr -d '\r' | grep -i "^$nombre:" | sed "s/^[^:]*: *//")"
    if [ -z "$patron" ]; then
        if [ -z "$valor" ]; then
            printf '  ok    %-44s sin %s\n' "$ruta" "$nombre"
        else
            printf '  FALLA %-44s no deberia llevar %s: %s\n' "$ruta" "$nombre" "$valor"
            fallos=$((fallos + 1))
        fi
    elif printf '%s' "$valor" | grep -Eq "$patron"; then
        printf '  ok    %-44s %s: %s\n' "$ruta" "$nombre" "$(printf '%s' "$valor" | cut -c1-60)"
    else
        printf '  FALLA %-44s %s\n        esperado: %s\n        obtenido: %s\n' \
            "$ruta" "$nombre" "$patron" "$valor"
        fallos=$((fallos + 1))
    fi
}

redirige /                                            "/login"
for par in \
    login:cuentas/login.html registro:cuentas/registro.html \
    preparando:cuentas/preparando.html inicio:cuentas/index.html \
    cuenta:cuentas/perfil.html subastas:cuentas/subastas.html \
    inventario:contenido/inventario/inventario.html \
    jugar:plataforma/salas-partidas/batallas.html \
    torneos:plataforma/torneos/torneos.html \
    verificar:cuentas/verificar-cuenta.html \
    restablecer:cuentas/restablecer-confirmar.html; do
    limpia="/${par%%:*}"
    fichero="${par#*:}"
    carpeta="/frontend/app-web/src/${fichero%/*}/"
    sirve "$limpia" "base $carpeta y marca de rutas limpias" \
        "<head><base href=\"$carpeta\"><meta name=\"nexus-rutas\" content=\"limpias\">"
    redirige "/frontend/app-web/src/$fichero"        "$limpia"
done
# La consulta viaja con la redireccion: la vuelta al login no se pierde.
redirige "/frontend/app-web/src/cuentas/login.html?volver=%2Fjugar&motivo=caducada" \
                                                      "/login?volver=%2Fjugar&motivo=caducada"
redirige /jugar/                                      "/jugar"
# B1 — la verificacion conserva su motivo al pasar a la direccion limpia (el
# fragmento del enlace del correo lo conserva el navegador: no llega al borde).
redirige "/frontend/app-web/src/cuentas/verificar-cuenta.html?motivo=registro" \
                                                      "/verificar?motivo=registro"
redirige /restablecer/                                "/restablecer"
# Una vista SIN direccion limpia se sigue sirviendo donde estaba, con la marca.
sirve /frontend/app-web/src/plataforma/salas-partidas/crear-sala.html \
    "marca de rutas limpias" '<meta name="nexus-rutas" content="limpias">'
# Pedir el codigo de recuperacion no tiene direccion limpia: /restablecer es
# la del canje, y la regex de /restablecer no se la puede llevar.
sirve /frontend/app-web/src/cuentas/restablecer-solicitar.html \
    "marca de rutas limpias" '<meta name="nexus-rutas" content="limpias">'

echo
echo "Cabeceras de seguridad y cache (R17)"
cabecera /login                   Content-Security-Policy "script-src 'self' 'nonce-[0-9a-f]{32}'"
cabecera /login                   Content-Security-Policy "frame-ancestors 'none'"
cabecera /login                   Cache-Control           '^no-store$'
cabecera /login                   X-Content-Type-Options  '^nosniff$'
cabecera /login                   X-Frame-Options         '^DENY$'
cabecera /login                   Referrer-Policy         'strict-origin-when-cross-origin'
cabecera /frontend/app-web/src/plataforma/salas-partidas/crear-sala.html \
                                  Content-Security-Policy "nonce-[0-9a-f]{32}"
cabecera /frontend/app-web/src/cuentas/login.js \
                                  Cache-Control           '^no-cache$'
cabecera /shared/ui-kit/css/tokens.css \
                                  X-Content-Type-Options  '^nosniff$'
# La API no lleva politica de contenido ni cache del borde: la pone cada servicio.
cabecera /api/v1/salas            Content-Security-Policy ''
cabecera /api/v1/salas            Cache-Control           ''
cabecera /api/v1/salas            X-Content-Type-Options  '^nosniff$'
# Sin HTTPS no hay HSTS (ver borde-dev.conf).
cabecera /login                   Strict-Transport-Security ''

echo
echo "Nonce (R17) — cada <script> de la vista lleva el de su propia respuesta"
respuesta="$(curl -s -D - "$BORDE/login" | tr -d '\r')"
nonce="$(printf '%s\n' "$respuesta" | grep -i '^content-security-policy:' | grep -oE "nonce-[0-9a-f]{32}" | head -1 | sed 's/^nonce-//')"
scripts="$(printf '%s\n' "$respuesta" | grep -o '<script[^>]*>' || true)"
sinNonce="$(printf '%s\n' "$scripts" | grep -v "nonce=\"$nonce\"" | grep -c '<script' || true)"
if [ -n "$nonce" ] && [ -n "$scripts" ] && [ "$sinNonce" = "0" ]; then
    printf '  ok    los %s <script> de /login llevan el nonce de su cabecera\n' \
        "$(printf '%s\n' "$scripts" | grep -c '<script')"
else
    printf '  FALLA nonce de la cabecera: "%s"; <script> sin el: %s\n' "$nonce" "$sinNonce"
    fallos=$((fallos + 1))
fi
otro="$(curl -s -D - -o /dev/null "$BORDE/login" | tr -d '\r' | grep -i '^content-security-policy:' | grep -oE "nonce-[0-9a-f]{32}" | head -1)"
if [ -n "$otro" ] && [ "$otro" != "nonce-$nonce" ]; then
    printf '  ok    el nonce cambia en cada respuesta\n'
else
    printf '  FALLA el nonce se repite entre respuestas: %s\n' "$otro"
    fallos=$((fallos + 1))
fi

echo
echo "Trafico publico (B12) — el mismo borde, visto desde una red de internet"
# Todo lo de arriba sale del anfitrion por localhost:8099, y el borde lo ve
# llegar desde la pasarela de Docker: una direccion PRIVADA, la misma clase de
# origen que el banco E2E y que un tunel SSH al host. Desde B12 el borde trata
# distinto a un origen privado y a uno publico (Mailpit, limite de
# frecuencia), asi que el banco necesita los dos. El publico es
# `cliente-publico` (docker-compose.yml): vive solo en 203.0.113.0/24
# (TEST-NET-3) y entra al borde por esa red.
PRUEBAS="$(cd "$(dirname "$0")" && pwd)"
BORDE_PUBLICO="${BORDE_PUBLICO:-http://borde}"

# desdeFuera <argumentos de curl>: la peticion, hecha desde internet.
# MSYS_NO_PATHCONV: en el bash de Git para Windows, sin esto un argumento como
# /dev/null le llegaria a docker convertido en una ruta de Windows.
desdeFuera() {
    (cd "$PRUEBAS" && MSYS_NO_PATHCONV=1 docker compose exec -T cliente-publico curl -s "$@")
}

# comprobarFuera / codigoFuera: `comprobar` y `codigo`, desde internet.
comprobarFuera() {
    local metodo="$1" ruta="$2" esperado="$3"
    local obtenido
    obtenido="$(desdeFuera -X "$metodo" "$BORDE_PUBLICO$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok    fuera %-6s %-34s -> %s\n' "$metodo" "$ruta" "$obtenido"
    else
        printf '  FALLA fuera %-6s %-34s\n        esperado: %s\n        obtenido: %s\n' \
            "$metodo" "$ruta" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}
codigoFuera() {
    local metodo="$1" ruta="$2" esperado="$3"
    local obtenido
    obtenido="$(desdeFuera -o /dev/null -w '%{http_code}' -X "$metodo" "$BORDE_PUBLICO$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok    fuera %-6s %-34s -> %s\n' "$metodo" "$ruta" "$obtenido"
    else
        printf '  FALLA fuera %-6s %-34s  esperado %s, obtenido %s\n' \
            "$metodo" "$ruta" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}

# El eco devuelve en X-Eco-Origen el $remote_addr que el borde le reenvia
# (X-Real-IP). Si Docker cambiara la red por la que publica el 8099, el
# anfitrion dejaria de ser privado y todo este bloque probaria otra cosa: por
# eso se comprueba en cada corrida, y no se da por hecho.
origenVisto() { printf '%s\n' "$1" | tr -d '\r' | grep -i '^x-eco-origen:' | sed 's/^[^:]*: *//'; }
origenAnfitrion="$(origenVisto "$(curl -s -o /dev/null -D - "$BORDE/api/v1/salas")")"
origenPublico="$(origenVisto "$(desdeFuera -o /dev/null -D - "$BORDE_PUBLICO/api/v1/salas")")"
if printf '%s' "$origenAnfitrion" | grep -Eq '^(127\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.|192\.168\.)'; then
    printf '  ok    el anfitrion llega al borde como origen privado (%s)\n' "$origenAnfitrion"
else
    printf '  FALLA el anfitrion deberia llegar como origen privado y llega como "%s"\n' "$origenAnfitrion"
    fallos=$((fallos + 1))
fi
if printf '%s' "$origenPublico" | grep -Eq '^203\.0\.113\.'; then
    printf '  ok    el cliente de internet llega como origen publico (%s)\n' "$origenPublico"
else
    printf '  FALLA el cliente de internet deberia llegar desde 203.0.113.0/24 y llega como "%s"\n' "$origenPublico"
    fallos=$((fallos + 1))
fi
# El reparto es el mismo para todo el mundo.
comprobarFuera GET  /api/v1/salas          "salas GET /api/v1/salas"
comprobarFuera POST /api/v1/auth/login     "identidad POST /api/v1/auth/login"
codigoFuera    GET  /salud-borde           200
# Mailpit (B12): la bandeja con los codigos de verificacion y de recuperacion
# no se sirve a internet; al anfitrion, que es privado, si. Las dos lineas de
# la seccion «Mailpit» de mas arriba salen tambien del anfitrion y siguen
# llegando al eco: los bancos y los tuneles de DEV no pierden la bandeja.
codigoFuera    GET  /mailpit/              403
codigoFuera    GET  "/mailpit/api/v1/search?query=to:ana@nexus.test" 403
codigoFuera    GET  /mailpit/api/v1/message/abc 403
codigo         GET  /mailpit/              200

echo
echo "Limite de frecuencia (B12) — lo que un bot haria en bucle, desde internet"
# Rafagas de verdad, una ejecucion por rafaga dentro del cliente publico para
# que las peticiones salgan seguidas. Los numeros del borde: acceso 30/min con
# rafaga de 20, escritura 120/min con rafaga de 60, ambos sin espera. Cada
# rafaga pasa de largo el cupo, asi que da igual lo que se haya liberado entre
# una ejecucion y la siguiente: si la ruta cuenta, aparece el 429.

# rafagaFuera <metodo> <ruta> <n>: n peticiones seguidas desde internet.
# Imprime "codigo NNN" por peticion y, al final, las cabeceras y el cuerpo del
# ULTIMO 429 de la rafaga, para mirar su forma. No los de la ultima peticion:
# el cubo se vacia a su ritmo (escritura, uno cada 500 ms) y a veces el hueco
# cae justo en la ultima, que entonces es un 200 del eco y no dice nada del
# 429 (develop 06a26164: rafaga de imagenes con su unico 200 al final).
rafagaFuera() {
    (cd "$PRUEBAS" && MSYS_NO_PATHCONV=1 docker compose exec -T cliente-publico sh -c '
        : > /tmp/ultimo-429
        i=0
        while [ "$i" -lt "$3" ]; do
            codigo="$(curl -s -D /tmp/cabeceras -o /tmp/cuerpo -w "%{http_code}" -X "$1" "$2")"
            echo "codigo $codigo"
            if [ "$codigo" = 429 ]; then
                { cat /tmp/cabeceras /tmp/cuerpo; echo; } > /tmp/ultimo-429
            fi
            i=$((i + 1))
        done
        cat /tmp/ultimo-429' _ "$1" "$BORDE_PUBLICO$2" "$3")
}
# cuantos <codigo> <salida de una rafaga>
cuantos() { printf '%s\n' "$2" | grep -c "^codigo $1\$"; }

# limitada <metodo> <ruta> <n> <minimo de 2xx> <Retry-After esperado>
# La rafaga deja pasar al menos el cupo y rechaza el resto con el 429 del
# borde: problem details, su `type`, Retry-After y las cabeceras de seguridad
# heredadas del server (la location con nombre no las pierde).
limitada() {
    local metodo="$1" ruta="$2" n="$3" minimo="$4" reintento="$5"
    local salida bien mal tipo tras nosniff cuerpo
    salida="$(rafagaFuera "$metodo" "$ruta" "$n" | tr -d '\r')"
    bien="$(cuantos 200 "$salida")"
    mal="$(cuantos 429 "$salida")"
    tipo="$(printf '%s\n' "$salida" | grep -i '^content-type:' | tail -1 | sed 's/^[^:]*: *//')"
    tras="$(printf '%s\n' "$salida" | grep -i '^retry-after:' | tail -1 | sed 's/^[^:]*: *//')"
    nosniff="$(printf '%s\n' "$salida" | grep -ic '^x-content-type-options: nosniff')"
    cuerpo="$(printf '%s\n' "$salida" | grep -c '"type":"https://nexusbattles.upb.edu.co/errors/demasiadas-peticiones".*"status":429')"
    if [ "$mal" -gt 0 ] && [ "$bien" -ge "$minimo" ] \
       && printf '%s' "$tipo" | grep -q '^application/problem+json' \
       && [ "$tras" = "$reintento" ] && [ "$nosniff" -ge 1 ] && [ "$cuerpo" -ge 1 ]; then
        printf '  ok    fuera %-6s %-40s %s x200, %s x429 (problem+json, Retry-After: %s)\n' \
            "$metodo" "$ruta" "$bien" "$mal" "$tras"
    else
        printf '  FALLA fuera %-6s %-40s\n        %s x200 (minimo %s), %s x429; Content-Type "%s"; Retry-After "%s" (esperado %s); nosniff %s; cuerpo problem details %s\n' \
            "$metodo" "$ruta" "$bien" "$minimo" "$mal" "$tipo" "$tras" "$reintento" "$nosniff" "$cuerpo"
        fallos=$((fallos + 1))
    fi
}

# libre <metodo> <ruta> <n>: n peticiones seguidas desde internet, ni un 429.
libre() {
    local metodo="$1" ruta="$2" n="$3"
    local salida bien
    salida="$(rafagaFuera "$metodo" "$ruta" "$n" | tr -d '\r')"
    bien="$(cuantos 200 "$salida")"
    if [ "$bien" -eq "$n" ]; then
        printf '  ok    fuera %-6s %-40s %s x200, sin limite\n' "$metodo" "$ruta" "$bien"
    else
        printf '  FALLA fuera %-6s %-40s solo %s de %s con 200 (%s x429)\n' \
            "$metodo" "$ruta" "$bien" "$n" "$(cuantos 429 "$salida")"
        fallos=$((fallos + 1))
    fi
}

# La lectura no se limita, ni siquiera en la misma ruta que si se limita al
# escribir; tampoco una escritura que no esta en la lista.
libre     GET  /api/v1/products/p-1/comments                  40
libre     GET  "/api/v1/vitrina?page=0"                       40
libre     POST /api/v1/salas                                  30
# acceso: la primera rafaga llena el cupo; las demas rutas de la clase
# comparten cupo (misma direccion, misma zona) y tienen que dar 429 igual.
limitada  POST /api/v1/auth/login                             30 20 2
limitada  POST /api/v1/auth/registro                          25  0 2
limitada  POST /api/v1/auth/verificacion/confirmacion         25  0 2
limitada  POST /api/v1/auth/restablecer/solicitar             25  0 2
# escritura: zona propia, asi que el login agotado no la toca.
limitada  POST /api/v1/products/p-1/comments                  80 60 1
limitada  POST /api/v1/products/p-1/rating                    25  0 1
limitada  POST /api/v1/products/p-1/comments/c-1/reportes     25  0 1
limitada  POST /api/v1/comentarios/imagenes                   25  0 1
limitada  POST /api/v1/subastas/s-1/pujas                     25  0 1
limitada  POST /api/v1/mensajes-directos/conversaciones/u-1/mensajes 25 0 1
# La compra (B5) se cuenta sobre la ruta ya reescrita, /ecommerce/api/v1/...
limitada  POST /api/v1/checkout                               25  0 1

# Desde el anfitrion (origen privado, como el banco E2E) el login no se
# limita: 40 seguidas, las 40 llegan a identidad.
privadas="$(for _ in $(seq 1 40); do
    curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BORDE/api/v1/auth/login"
done | grep -c '^200$')"
if [ "$privadas" -eq 40 ]; then
    printf '  ok    anfitrion POST /api/v1/auth/login x40: 40 x200, un origen privado no se limita\n'
else
    printf '  FALLA anfitrion POST /api/v1/auth/login x40: solo %s x200\n' "$privadas"
    fallos=$((fallos + 1))
fi

echo
echo "Trazas (regla 5, B12) — el borde no se come traceparent ni X-Trace-Id"
# nginx reenvia las cabeceras de la peticion salvo las que tienen guion bajo
# (underscores_in_headers off) y las que pisa un proxy_set_header; y devuelve
# las de la respuesta salvo unas pocas suyas. Ninguna de las de traza cae en
# esos casos, pero eso se comprueba, no se supone: en una location normal, en
# una que reescribe la ruta (vitrina) y en una de WebSocket, que declara sus
# propios proxy_set_header. El eco devuelve lo que le llego y pone su propio
# traceparent en la respuesta, como FiltroDeTraza en cada servicio.
TRAZA='00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'
for ruta in /api/v1/salas "/api/v1/vitrina?page=0" /ws/notificaciones; do
    cab="$(curl -s -o /dev/null -D - -H "traceparent: $TRAZA" -H 'X-Trace-Id: traza-del-banco' "$BORDE$ruta" | tr -d '\r')"
    ida="$(printf '%s\n' "$cab" | grep -i '^x-eco-traceparent:' | sed 's/^[^:]*: *//')"
    idaId="$(printf '%s\n' "$cab" | grep -i '^x-eco-trace-id:' | sed 's/^[^:]*: *//')"
    vuelta="$(printf '%s\n' "$cab" | grep -i '^traceparent:' | sed 's/^[^:]*: *//')"
    if [ "$ida" = "$TRAZA" ] && [ "$idaId" = "traza-del-banco" ] && [ -n "$vuelta" ]; then
        printf '  ok    %-26s traceparent y X-Trace-Id llegan al servicio; su traceparent vuelve\n' "$ruta"
    else
        printf '  FALLA %-26s ida traceparent "%s", X-Trace-Id "%s"; vuelta traceparent "%s"\n' \
            "$ruta" "$ida" "$idaId" "$vuelta"
        fallos=$((fallos + 1))
    fi
done

echo
echo "Contenido — no se puede suplantar una IP, se comprueba el fichero"
enConfiguracion "heroes va al host de contenido"     'heroes.*\n?.*34\.193\.90\.11:8101|34\.193\.90\.11:8101'
enConfiguracion "inventario va al host de contenido" '34\.193\.90\.11:8102'
enConfiguracion "productos va al host de contenido"  '34\.193\.90\.11:8103'

echo
echo "Quien atiende una ruta lo dice su contrato, no el metodo (#421)"
# La capa de adaptacion de #421 repartia /api/v1/productos por metodo. Se quito
# en R16 por decision de los duenos del prefijo: que nginx no decida la
# semantica de una ruta. Si vuelve a aparecer un reparto por metodo, es que
# alguien reabrio la colision sin pasar por un contrato.
#
# B12 — desde el limite de frecuencia el fichero SI lee $request_method: un
# POST de login cuenta y un GET no. Pero solo para decidir la clave con la que
# se cuenta. Lo prohibido sigue siendo lo mismo, ahora dicho con precision:
# ninguna variable que dependa del metodo -directamente o a traves de otros
# `map`- puede llegar a un set, proxy_pass, return, rewrite, if, try_files,
# alias o root, que es donde se decide a quien va una peticion.
destinosPorMetodo() {
    awk '
        /^[[:space:]]*#/ { next }
        { lineas[NR] = $0 }
        /^[[:space:]]*map[[:space:]]/ {
            cabecera = $0
            sub(/^[[:space:]]*map[[:space:]]+/, "", cabecera)
            salida = cabecera; sub(/[[:space:]]*\{.*$/, "", salida); sub(/^.*\$/, "", salida)
            fuente = cabecera; sub(/[[:space:]]+\$[A-Za-z0-9_]+[[:space:]]*\{.*$/, "", fuente)
            mapas++; fuentes[mapas] = fuente; salidas[mapas] = salida; esMapa[NR] = 1
        }
        END {
            tocadas["request_method"] = 1
            do {
                cambio = 0
                for (i = 1; i <= mapas; i++) {
                    if (salidas[i] in tocadas) continue
                    for (t in tocadas)
                        if (index(fuentes[i], "$" t) > 0) { tocadas[salidas[i]] = 1; cambio = 1; break }
                }
            } while (cambio)
            for (n in lineas) {
                if (n in esMapa) continue
                l = lineas[n]
                if (l !~ /(^|[{;])[[:space:]]*(set|proxy_pass|return|rewrite|if|try_files|alias|root)[[:space:](]/) continue
                for (t in tocadas)
                    if (match(l, "\\$\\{?" t "([^A-Za-z0-9_]|$)")) { printf "%d: %s\n", n, l; break }
            }
        }' "$1"
}
metodoEnDestino="$(destinosPorMetodo "$CONF")"
if [ -z "$metodoEnDestino" ]; then
    printf '  ok    ninguna ruta se reparte por metodo (el metodo solo elige la clave del limite)\n'
else
    printf '  FALLA el metodo decide un destino en %s:\n%s\n' "$CONF" "$metodoEnDestino"
    fallos=$((fallos + 1))
fi
# Un guardian que no sabe ponerse rojo no guarda nada: el reparto de #421,
# reconstruido en un fichero aparte, tiene que saltar.
reparto="$(mktemp)"
cat > "$reparto" <<'CONF_DE_PRUEBA'
map $request_method $destino_productos {
    GET     srv-ms-ecommerce:8090;
    default 34.193.90.11:8103;
}
server {
    location = /api/v1/productos { set $destino $destino_productos; proxy_pass http://$destino; }
}
CONF_DE_PRUEBA
if [ -n "$(destinosPorMetodo "$reparto")" ]; then
    printf '  ok    el guardian del metodo se pone rojo con el reparto de #421 (autoprueba)\n'
else
    printf '  FALLA el guardian del metodo NO detecta el reparto de #421 (autoprueba)\n'
    fallos=$((fallos + 1))
fi
rm -f "$reparto"

echo
echo "Limite de frecuencia (B12) — que ninguna location se salga de la herencia"
# limit_req, limit_req_status y error_page se heredan del server SOLO si la
# location no declara los suyos (como add_header). Una que los declarara se
# quedaria sin limite, o con un 429 en HTML en vez de problem details. Se
# recorre el fichero caracter a caracter contando llaves, despues de quitar lo
# que va entre comillas (hay llaves dentro de regex y de cuerpos JSON), y cada
# directiva se mira al llegar a su `;`: tambien las de una location escrita
# en una sola linea.
fueraDelServer() {
    awk '
        /^[[:space:]]*#/ { next }
        {
            linea = $0
            gsub(/\047[^\047]*\047/, "", linea)
            gsub(/"[^"]*"/, "", linea)
            sub(/#.*$/, "", linea)
            for (i = 1; i <= length(linea); i++) {
                c = substr(linea, i, 1)
                if (c == "{") { nivel++; texto = "" }
                else if (c == "}") { nivel--; texto = "" }
                else if (c == ";") {
                    directiva = texto
                    sub(/^[[:space:]]+/, "", directiva)
                    split(directiva, partes, /[[:space:]]+/)
                    if ((partes[1] == "limit_req" || partes[1] == "limit_req_status" \
                         || partes[1] == "error_page") && nivel != 1)
                        printf "%d: %s\n", NR, $0
                    texto = ""
                }
                else texto = texto c
            }
            texto = texto " "
        }' "$1"
}
enLocations="$(fueraDelServer "$CONF")"
if [ -z "$enLocations" ] && [ "$(grep -c '^[[:space:]]*limit_req zone=' "$CONF")" -eq 2 ] \
   && grep -q '^[[:space:]]*error_page 429 = @demasiadas_peticiones;' "$CONF"; then
    printf '  ok    limit_req (acceso y escritura), limit_req_status y error_page 429 solo en el server\n'
else
    printf '  FALLA limite de frecuencia fuera del server o incompleto:\n%s\n' "${enLocations:-        (faltan los dos limit_req zone= o el error_page 429)}"
    fallos=$((fallos + 1))
fi
herencia="$(mktemp)"
cat > "$herencia" <<'CONF_DE_PRUEBA'
server {
    limit_req zone=acceso burst=20 nodelay;
    location /api/v1/auth { error_page 404 /404.html; proxy_pass http://x; }
    location /api/v1/x {
        limit_req zone=escritura;
    }
}
CONF_DE_PRUEBA
if [ "$(fueraDelServer "$herencia" | grep -c .)" -eq 2 ]; then
    printf '  ok    el guardian de la herencia se pone rojo con un error_page y un limit_req en location (autoprueba)\n'
else
    printf '  FALLA el guardian de la herencia no detecta error_page/limit_req en una location (autoprueba)\n'
    fallos=$((fallos + 1))
fi
rm -f "$herencia"
# Los origenes internos son los mismos para el limite (geo) y para Mailpit
# (allow): dos listas que dicen lo mismo, y que no se pueden separar sin que
# esto lo diga.
internosGeo="$(awk '/^[[:space:]]*geo[[:space:]]+\$origen_interno/ {g = 1; next}
                    g && /\}/ {g = 0} g && $2 == "1;" {print $1}' "$CONF" | sort | tr '\n' ' ')"
internosMailpit="$(awk '/location \/mailpit\/ \{/ {m = 1; next}
                        m && /\}/ {m = 0} m && $1 == "allow" {sub(/;$/, "", $2); print $2}' "$CONF" | sort | tr '\n' ' ')"
if [ -n "$internosGeo" ] && [ "$internosGeo" = "$internosMailpit" ]; then
    printf '  ok    los mismos origenes internos en la geo del limite y en /mailpit/: %s\n' "$internosGeo"
else
    printf '  FALLA origenes internos distintos\n        geo:     %s\n        mailpit: %s\n' "$internosGeo" "$internosMailpit"
    fallos=$((fallos + 1))
fi

echo
echo "Que promete el borde que dev hoy no puede dar (inventario de 502)"
# Este bloque no comprueba el reparto: comprueba la OTRA mitad del problema.
#
# Todo lo de arriba pasa contra servidores de eco, asi que una ruta puede
# estar perfectamente repartida y devolver 502 en dev igualmente, porque
# detras no hay nadie. Fue el caso de /api/v1/(creditos|transacciones|cofres)
# y /api/v1/(subastas|mis-pujas) durante semanas: el reparto correcto, el
# guardian en verde, y la vista rota (#571).
#
# Se cruza el borde con el catalogo de despliegue: un upstream cuyo servicio
# esta marcado desplegableDev:false dara 502 en dev, y eso no es un defecto
# -- es una decision de capacidad medida -- pero tiene que estar a la vista y
# no descubrirse el dia de la demo. Un upstream que no existe en el catalogo
# si es un fallo: nadie lo va a desplegar nunca.
CATALOGO="${CATALOGO:-$(dirname "$0")/../../despliegue/servicios.json}"
if [ -f "$CATALOGO" ] && command -v jq >/dev/null 2>&1; then
    for servicio in $(grep -oE 'srv-[a-z-]+:[0-9]+' "$CONF" | sed 's/^srv-//;s/:[0-9]*$//' | sort -u); do
        entrada=$(jq -r --arg s "$servicio" '.servicios[] | select(.nombre == $s) | "\(.desplegableDev)"' "$CATALOGO")
        prefijos=$(grep -B4 "srv-${servicio}:" "$CONF" | grep -oE 'location [^{]+' | sed 's/location //' | tr -d ' ' | tr '\n' ' ')
        if [ -z "$entrada" ]; then
            printf '  FALLA %-22s el borde lo enruta y NO esta en el catalogo de despliegue\n' "$servicio"
            printf '        rutas afectadas: %s\n' "${prefijos:-(no identificadas)}"
            fallos=$((fallos + 1))
        elif [ "$entrada" = "false" ]; then
            printf '  502   %-22s fuera del host de dev por capacidad -> sus rutas dan 502\n' "$servicio"
            printf '        rutas afectadas: %s\n' "${prefijos:-(no identificadas)}"
        else
            printf '  ok    %-22s desplegable en dev\n' "$servicio"
        fi
    done
else
    echo "  (se omite: no se encontro $CATALOGO o falta jq)"
fi

echo
if [ "$fallos" -eq 0 ]; then
    echo "Todo el reparto es el esperado."
else
    echo "$fallos ruta(s) no van donde deben."
fi
exit "$fallos"
