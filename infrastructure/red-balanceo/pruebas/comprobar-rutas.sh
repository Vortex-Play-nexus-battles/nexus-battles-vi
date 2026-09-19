#!/usr/bin/env bash
#
# Comprueba a que servicio y con que ruta llega cada peticion del borde.
#
#   cd infrastructure/red-balanceo/pruebas
#   docker compose up -d && sleep 5
#   ./comprobar-rutas.sh
#
# Los servicios son ecos: responden "<nombre> <metodo> <ruta que recibieron>".
# Lo que se fija aqui es el REPARTO, no el comportamiento de cada servicio.
set -uo pipefail

BORDE="${BORDE:-http://localhost:8099}"
fallos=0

# comprobar <metodo> <ruta> <respuesta esperada>
comprobar() {
    local metodo="$1" ruta="$2" esperado="$3"
    local obtenido
    obtenido="$(curl -s -X "$metodo" "$BORDE$ruta")"
    if [ "$obtenido" = "$esperado" ]; then
        printf '  ok   %-6s %-32s -> %s\n' "$metodo" "$ruta" "$obtenido"
    else
        printf '  FALLA %-6s %-32s\n       esperado: %s\n       obtenido: %s\n' \
            "$metodo" "$ruta" "$esperado" "$obtenido"
        fallos=$((fallos + 1))
    fi
}

echo "Carrito — ms-ecommerce vive bajo /ecommerce, el navegador no se entera"
# La de abajo es la que estaba rota: el sufijo se perdia por el proxy_pass
# con variable Y uri, y anadir al carrito nunca llegaba a su endpoint.
comprobar GET  /api/v1/carrito           "ecommerce GET /ecommerce/api/v1/carrito"
comprobar POST /api/v1/carrito/items     "ecommerce POST /ecommerce/api/v1/carrito/items"
comprobar DELETE /api/v1/carrito/items/x "ecommerce DELETE /ecommerce/api/v1/carrito/items/x"

echo
echo "Productos — prefijo compartido por dos servicios, repartido por metodo"
comprobar GET  /api/v1/productos         "ecommerce GET /ecommerce/api/v1/productos"
comprobar POST /api/v1/productos         "productos POST /api/v1/productos"
comprobar GET  /api/v1/productos/p-1     "productos GET /api/v1/productos/p-1"
comprobar GET  /api/v1/productos/estadisticas "productos GET /api/v1/productos/estadisticas"

echo
echo "Regresion: lo que ya funcionaba sigue igual"
comprobar GET  /api/v1/salas             "salas GET /api/v1/salas"
comprobar GET  /api/v1/salas/s-1         "salas GET /api/v1/salas/s-1"

echo
if [ "$fallos" -eq 0 ]; then
    echo "Todo el reparto es el esperado."
else
    echo "$fallos ruta(s) no van donde deben."
fi
exit "$fallos"
