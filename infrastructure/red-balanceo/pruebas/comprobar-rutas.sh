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

echo
echo "Plataforma — los ocho servicios del bloque"
comprobar GET  /api/v1/salas               "salas GET /api/v1/salas"
comprobar GET  /api/v1/salas/s-1           "salas GET /api/v1/salas/s-1"
comprobar GET  /api/v1/partidas/p-1        "salas GET /api/v1/partidas/p-1"
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

echo
echo "Lo que el borde contesta el mismo"
codigo GET /salud-borde   200
# correo es servicio-entre-servicios (ADR-005): desde fuera NO se expone.
codigo GET /api/v1/correos          404
# prefijo inventado: tiene que caer en el 404 de "prefijo sin servicio", no
# colarse en ningun upstream.
codigo GET /api/v1/no-existe-esto   404

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
fueraDeConfiguracion "ninguna ruta se reparte por metodo" '\$request_method'

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
