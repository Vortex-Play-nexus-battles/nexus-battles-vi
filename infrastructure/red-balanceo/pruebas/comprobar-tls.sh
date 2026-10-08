#!/usr/bin/env bash
# HTTPS del borde con trafico (28-sep; 6-oct).
#
# comprobar-rutas.sh prueba el borde tal como esta en DEV hoy: solo HTTP. Este
# prueba el MISMO borde-dev.conf con el fragmento TLS que dejaria
# scripts/cd/certificado.sh (renderizado desde la plantilla real, con HSTS
# pedido) y un certificado autofirmado para nexus.test. Lo que tiene que pasar:
#
#   - nginx acepta la configuracion (el include del fragmento dentro del server);
#   - por el 80, todo redirige con 301 a https://nexus.test/... salvo
#     /salud-borde (lo consultan desplegar.sh y la compuerta de capacidad), el
#     reto de ACME (la renovacion lo sirve por el 80) y /mailpit/ (los tuneles
#     SSH de smoke-dev y canarios la leen por http://localhost);
#   - desde internet (cliente-publico, 203.0.113.0/24) el 80 tambien redirige,
#     y /mailpit/ sigue negada (403), no redirigida;
#   - por el 443 contesta el mismo borde: salud, una ruta de API hasta su eco,
#     las cabeceras de seguridad y el HSTS pedido;
#   - por el 443 solo se sirve el dominio: www y la IP van con 301 a
#     https://nexus.test (7-oct), con ruta y consulta;
#   - TLS 1.2 y 1.3 si; TLS 1.1 no.
#
# Necesita el banco levantado (docker compose up -d --wait, en esta carpeta):
# reutiliza su configuracion derivada y sus redes. Segundos.
#
#   ./comprobar-tls.sh
set -uo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"
PROYECTO="$(docker compose -f "$AQUI/docker-compose.yml" ps --format '{{.Project}}' 2>/dev/null | head -1)"
PROYECTO="${PROYECTO:-$(basename "$AQUI")}"
RED="${PROYECTO}_borde"
RED_PUBLICA="${PROYECTO}_publico"
VOLUMEN="${PROYECTO}_conf-del-borde"
NOMBRE="borde-tls-prueba"
TMP="$(mktemp -d)"
trap 'docker rm -f "$NOMBRE" >/dev/null 2>&1; rm -rf "$TMP"' EXIT
fallos=0
ok() { printf '  ok    %s\n' "$*"; }
falla() { printf '  FALLA %s\n' "$*"; fallos=$((fallos + 1)); }

if ! docker volume inspect "$VOLUMEN" >/dev/null 2>&1; then
  echo "No existe $VOLUMEN: levanta antes el banco (docker compose up -d --wait)."
  exit 1
fi

mkdir -p "$TMP/tls" "$TMP/le/live/nexus.test" "$TMP/acme/.well-known/acme-challenge"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=nexus.test" \
  -keyout "$TMP/le/live/nexus.test/privkey.pem" -out "$TMP/le/live/nexus.test/fullchain.pem" \
  >/dev/null 2>&1 || { echo "No se pudo generar el certificado de prueba (¿openssl?)."; exit 1; }
HSTS_SEGUNDOS=300 PLANTILLA_TLS="$RAIZ/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla" \
  bash "$RAIZ/scripts/cd/certificado.sh" renderizar nexus.test "$TMP/tls/borde-tls.conf"
echo "reto-de-prueba" > "$TMP/acme/.well-known/acme-challenge/prueba"
chmod -R a+rX "$TMP"

docker run -d --name "$NOMBRE" --network "$RED" \
  -v "$VOLUMEN:/etc/nginx/conf.d:ro" \
  -v "$TMP/tls:/etc/nginx/nexus-tls:ro" \
  -v "$TMP/le:/etc/letsencrypt:ro" \
  -v "$TMP/acme:/srv/acme:ro" \
  -v "$RAIZ/frontend:/srv/nexus/frontend:ro" \
  -v "$RAIZ/shared:/srv/nexus/shared:ro" \
  -p 8098:80 -p 8443:443 nginx:1.27-alpine >/dev/null
# Tambien en la red "de internet" del banco, para verlo como lo ve un visitante.
docker network connect "$RED_PUBLICA" "$NOMBRE" >/dev/null 2>&1 || true

for _ in $(seq 1 20); do
  curl -s -o /dev/null http://localhost:8098/salud-borde && break
  sleep 0.5
done

# desdeFuera <argumentos de curl>: la peticion, hecha desde 203.0.113.0/24.
desdeFuera() {
  (cd "$AQUI" && MSYS_NO_PATHCONV=1 docker compose exec -T cliente-publico curl -s "$@")
}

echo "HTTPS del borde (fragmento renderizado desde la plantilla, certificado autofirmado)"
if docker exec "$NOMBRE" nginx -t >/dev/null 2>&1; then
  ok "nginx acepta borde-dev.conf con el fragmento TLS"
else
  falla "nginx -t rechaza la configuracion con el fragmento:"
  docker exec "$NOMBRE" nginx -t 2>&1 | tail -3 | sed 's/^/        /'
fi

r=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' http://localhost:8098/login)
[ "$r" = "301 https://nexus.test/login" ] && ok "80 -> 301 https://nexus.test/login" || falla "80 /login: '$r' (esperado 301 https://nexus.test/login)"

r=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "http://localhost:8098/api/v1/torneos?x=1")
[ "$r" = "301 https://nexus.test/api/v1/torneos?x=1" ] && ok "80 conserva ruta y consulta al redirigir" || falla "80 /api/v1/torneos?x=1: '$r'"

r=$(curl -s http://localhost:8098/salud-borde)
[ "$r" = "UP" ] && ok "80 /salud-borde sigue contestando sin redirigir" || falla "80 /salud-borde: '$r'"

r=$(curl -s http://localhost:8098/.well-known/acme-challenge/prueba)
[ "$r" = "reto-de-prueba" ] && ok "80 sirve el reto de ACME sin redirigir" || falla "reto de ACME: '$r'"

r=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8098/mailpit/)
[ "$r" = "200" ] && ok "80 /mailpit/ desde un origen interno (tunel) llega a la bandeja, sin redirigir" || falla "80 /mailpit/ interno: '$r' (esperado 200)"

echo
echo "El mismo borde visto desde internet (cliente-publico)"
r=$(desdeFuera -o /dev/null -w '%{http_code} %{redirect_url}' "http://$NOMBRE/login")
[ "$r" = "301 https://nexus.test/login" ] && ok "fuera: 80 /login -> 301 https://nexus.test/login" || falla "fuera: 80 /login: '$r'"
r=$(desdeFuera -o /dev/null -w '%{http_code} %{redirect_url}' "http://$NOMBRE/")
[ "$r" = "301 https://nexus.test/" ] && ok "fuera: la raiz (y la IP) llevan al dominio" || falla "fuera: 80 /: '$r'"
r=$(desdeFuera -o /dev/null -w '%{http_code}' "http://$NOMBRE/mailpit/")
[ "$r" = "403" ] && ok "fuera: /mailpit/ sigue negada (403), no redirigida" || falla "fuera: 80 /mailpit/: '$r' (esperado 403)"
r=$(desdeFuera -k -H 'Host: nexus.test' -o /dev/null -w '%{http_code}' "https://$NOMBRE/mailpit/")
[ "$r" = "403" ] && ok "fuera: tampoco por el 443" || falla "fuera: 443 /mailpit/: '$r' (esperado 403)"

echo
echo "Por el 443"
# Con el nombre del certificado, como llega un navegador: el 443 solo sirve
# el dominio (las demas formas de llegar redirigen, ver mas abajo). Sin proxy:
# nexus.test no esta en el NO_PROXY de quien lo corra detras de uno.
DOMINIO443=(--noproxy '*' --resolve nexus.test:8443:127.0.0.1)
r=$(curl -sk "${DOMINIO443[@]}" https://nexus.test:8443/salud-borde)
[ "$r" = "UP" ] && ok "443 contesta el mismo borde" || falla "443 /salud-borde: '$r'"

r=$(curl -sk "${DOMINIO443[@]}" -o /dev/null -w '%{http_code}' https://nexus.test:8443/api/v1/torneos)
[ "$r" = "200" ] && ok "443 enruta la API hasta su servicio (eco de torneos)" || falla "443 /api/v1/torneos: '$r'"

r=$(curl -sk "${DOMINIO443[@]}" -o /dev/null -w '%{http_code}' https://nexus.test:8443/.well-known/acme-challenge/prueba)
[ "$r" = "200" ] && ok "443 tambien sirve el reto" || falla "443 reto: '$r'"

cabeceras="$(curl -sk "${DOMINIO443[@]}" -o /dev/null -D - https://nexus.test:8443/login | tr -d '\r')"
for c in "x-content-type-options: nosniff" "x-frame-options: DENY" "referrer-policy: strict-origin-when-cross-origin" \
         "strict-transport-security: max-age=300"; do
  printf '%s\n' "$cabeceras" | grep -qi "^$c$" && ok "443 /login lleva '$c'" || falla "443 /login sin '$c'"
done
printf '%s\n' "$cabeceras" | grep -i '^content-security-policy:' | grep -q "frame-ancestors 'none'" \
  && ok "443 /login lleva la politica de contenido" || falla "443 /login sin Content-Security-Policy"
printf '%s\n' "$cabeceras" | grep -i '^strict-transport-security:' | grep -qi 'includeSubDomains\|preload' \
  && falla "HSTS con includeSubDomains o preload" || ok "HSTS sin includeSubDomains ni preload"

echo
echo "Un solo origen por el 443 (7-oct)"
r=$(curl -sk --noproxy '*' --resolve www.nexus.test:8443:127.0.0.1 -o /dev/null -w '%{http_code} %{redirect_url}' "https://www.nexus.test:8443/login?x=1")
[ "$r" = "301 https://nexus.test/login?x=1" ] && ok "443 www -> 301 https://nexus.test/login?x=1" || falla "443 www /login?x=1: '$r'"
r=$(curl -sk -o /dev/null -w '%{http_code} %{redirect_url}' https://127.0.0.1:8443/jugar)
[ "$r" = "301 https://nexus.test/jugar" ] && ok "443 por la IP -> 301 https://nexus.test/jugar" || falla "443 IP /jugar: '$r'"
r=$(curl -sk "${DOMINIO443[@]}" -o /dev/null -w '%{http_code}' https://nexus.test:8443/login)
[ "$r" = "200" ] && ok "443 con el dominio sirve sin redirigir" || falla "443 dominio /login: '$r'"

echo
echo "Versiones de TLS"
tls() { echo | openssl s_client -connect localhost:8443 -servername nexus.test "$@" 2>/dev/null | grep -q '^ *Protocol *:\|^New, TLSv'; }
tls -tls1_3 && ok "TLS 1.3 aceptado" || falla "TLS 1.3 rechazado"
tls -tls1_2 && ok "TLS 1.2 aceptado" || falla "TLS 1.2 rechazado"
# SECLEVEL=0 para que el CLIENTE ofrezca TLS 1.1: quien tiene que negarse es
# el borde, no el openssl del runner.
tls -tls1_1 -cipher 'DEFAULT@SECLEVEL=0' && falla "TLS 1.1 aceptado" || ok "TLS 1.1 rechazado"
c=$(echo | openssl s_client -connect localhost:8443 -servername nexus.test -tls1_2 2>/dev/null | sed -n 's/^ *Cipher *: *//p' | head -1)
printf '%s' "$c" | grep -q 'GCM\|CHACHA20' && ok "TLS 1.2 negocia una suite AEAD ($c)" || falla "TLS 1.2 negocio '$c'"

echo
if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobacion(es) de HTTPS fallaron."
  exit 1
fi
echo "HTTPS del borde en orden."
