#!/usr/bin/env bash
# Prueba de abrir_origenes_al_dominio (scripts/cd/desplegar.sh), 6-oct.
#
# Con la pagina en https://<dominio> el navegador manda ese Origin en cada
# POST y en cada handshake de WebSocket, y los servicios (que detras del borde
# se ven como http://<host>:80) lo tratan como CORS: si https://<dominio> no
# esta en su lista, login, carrito, chatbot, pujas y los canales STOMP
# responden 403. Lo que aqui se fija: con DOMINIO_PUBLICO, las ocho listas
# llevan https://<dominio> SIN perder lo que tenian; sin el, nada cambia; una
# IP no cuenta como dominio; y todas llegan al .env y al entorno de Compose.
#
# En local, sin Docker: "bash scripts/cd/pruebas/origenes-del-dominio.sh".
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/../desplegar.sh"

# Cargar solo las funciones del script, sin ejecutar el despliegue.
DESPLEGAR_SOLO_FUNCIONES=1 source "$SCRIPT"
set +e

FALLOS=0
bien() { echo "  ok    $1"; }
mal() { echo "  FALLO $1"; FALLOS=$((FALLOS + 1)); }
igual() { # igual <descripcion> <obtenido> <esperado>
  if [ "$2" = "$3" ]; then bien "$1"; else mal "$1 (obtenido '$2', esperado '$3')"; fi
}

limpiar() {
  local v
  for v in $VARIABLES_DE_ORIGENES DOMINIO_PUBLICO PUBLIC_BASE_URL; do unset "$v"; done
}

echo "1) Sin DOMINIO_PUBLICO no se toca nada"
limpiar
SALAS_WS_ORIGENES=http://35.168.124.119
abrir_origenes_al_dominio > /dev/null
igual "SALAS_WS_ORIGENES intacta" "$SALAS_WS_ORIGENES" "http://35.168.124.119"
igual "IDENTIDAD_CORS_ORIGENES sigue sin valor (ms-identidad usa su lista de desarrollo)" "${IDENTIDAD_CORS_ORIGENES:-}" ""

echo "2) Con dominio: se suma https://<dominio> a lo que habia"
limpiar
PUBLIC_BASE_URL=http://35.168.124.119
DOMINIO_PUBLICO=Juego.Example.com
SALAS_WS_ORIGENES=http://35.168.124.119
CHAT_WS_ORIGENES=http://35.168.124.119
NOTIFICACIONES_WS_ORIGENES=http://35.168.124.119
SUBASTAS_WS_ORIGENES=http://35.168.124.119
SUBASTAS_CORS_ORIGENES=http://35.168.124.119
salida="$(abrir_origenes_al_dominio)"
abrir_origenes_al_dominio > /dev/null
for v in SALAS_WS_ORIGENES CHAT_WS_ORIGENES NOTIFICACIONES_WS_ORIGENES SUBASTAS_WS_ORIGENES SUBASTAS_CORS_ORIGENES; do
  igual "$v conserva la IP y suma el dominio (en minusculas)" "${!v}" "http://35.168.124.119,https://juego.example.com"
done
printf '%s' "$salida" | grep -q "https://juego.example.com en las listas" && bien "lo dice en la bitacora" || mal "no lo dijo"

echo "3) Las listas que nadie fijo parten del origen publico de hoy"
for v in IDENTIDAD_CORS_ORIGENES ECOMMERCE_CORS_ORIGENES CHATBOT_CORS_ORIGENES; do
  igual "$v" "${!v}" "http://35.168.124.119,https://juego.example.com"
done

echo "4) Todas quedan exportadas (Compose las interpola desde el entorno)"
for v in $VARIABLES_DE_ORIGENES; do
  if env | grep -q "^$v=.*https://juego.example.com"; then bien "$v exportada"; else mal "$v no exportada"; fi
done

echo "5) Sin repetidos, sin huecos, sin barra final; dos veces da lo mismo"
limpiar
DOMINIO_PUBLICO=https://juego.example.com/
SALAS_WS_ORIGENES="http://35.168.124.119/, ,https://juego.example.com"
abrir_origenes_al_dominio > /dev/null
abrir_origenes_al_dominio > /dev/null
igual "lista limpia e idempotente" "$SALAS_WS_ORIGENES" "http://35.168.124.119,https://juego.example.com"

echo "6) Cuando PUBLIC_BASE_URL ya es el dominio, no aparece la IP donde no estaba"
limpiar
DOMINIO_PUBLICO=juego.example.com
PUBLIC_BASE_URL=https://juego.example.com
abrir_origenes_al_dominio > /dev/null
igual "ECOMMERCE_CORS_ORIGENES" "$ECOMMERCE_CORS_ORIGENES" "https://juego.example.com"

echo "7) Una IP no es un dominio: no se toca nada y se avisa"
limpiar
DOMINIO_PUBLICO=35.168.124.119
SALAS_WS_ORIGENES=http://35.168.124.119
salida="$(abrir_origenes_al_dominio)"
abrir_origenes_al_dominio > /dev/null
igual "SALAS_WS_ORIGENES intacta" "$SALAS_WS_ORIGENES" "http://35.168.124.119"
printf '%s' "$salida" | grep -q "no es un nombre de dominio" && bien "avisa" || mal "no aviso"

echo "8) Las ocho listas van al .env (las lee tambien un compose sin este entorno, p. ej. revertir.sh)"
grep -q 'COMENTARIOS_FORMATOS_IMAGEN $VARIABLES_DE_ORIGENES; do' "$SCRIPT" \
  && bien "el bucle del .env recorre las ocho" || mal "las listas no llegan al .env"
grep -q 'CORS_ORIGENES: ${CHATBOT_CORS_ORIGENES' "$AQUI/../../../docker-compose.ms-chatbot.yml" \
  && bien "ms-chatbot lee CHATBOT_CORS_ORIGENES" || mal "ms-chatbot no tiene de donde leer su lista (se quedaria con localhost)"
grep -q 'CORS_ORIGENES: ${ECOMMERCE_CORS_ORIGENES' "$AQUI/../../../docker-compose.ms-ecommerce.yml" \
  && bien "ms-ecommerce lee ECOMMERCE_CORS_ORIGENES" || mal "ms-ecommerce no lee ECOMMERCE_CORS_ORIGENES"
grep -q 'IDENTIDAD_CORS_ORIGENES: ${IDENTIDAD_CORS_ORIGENES' "$AQUI/../../../docker-compose.cuentas.yml" \
  && bien "ms-identidad lee IDENTIDAD_CORS_ORIGENES" || mal "ms-identidad no lee IDENTIDAD_CORS_ORIGENES"

if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS fallo(s)."
  exit 1
fi
echo "Todo en orden."
