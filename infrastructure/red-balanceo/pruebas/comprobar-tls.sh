#!/usr/bin/env bash
# HTTPS del borde con trafico (28-sep).
#
# comprobar-rutas.sh prueba el borde tal como esta en DEV hoy: solo HTTP. Este
# prueba el MISMO borde-dev.conf con el fragmento TLS que dejaria
# scripts/cd/certificado.sh (renderizado desde la plantilla real) y un
# certificado autofirmado para nexus.test. Lo que tiene que pasar:
#
#   - nginx acepta la configuracion (el include del fragmento dentro del server);
#   - por el 80, todo redirige con 301 a https://nexus.test/... salvo
#     /salud-borde (lo consultan desplegar.sh y la compuerta de capacidad) y el
#     reto de ACME (la renovacion lo sirve por el 80);
#   - por el 443 contesta el mismo borde: salud y una ruta de API hasta su eco.
#
# Necesita el banco levantado (docker compose up -d --wait, en esta carpeta):
# reutiliza su configuracion derivada y su red de ecos. Segundos.
#
#   ./comprobar-tls.sh
set -uo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"
PROYECTO="$(docker compose -f "$AQUI/docker-compose.yml" ps --format '{{.Project}}' 2>/dev/null | head -1)"
PROYECTO="${PROYECTO:-$(basename "$AQUI")}"
RED="${PROYECTO}_borde"
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
PLANTILLA_TLS="$RAIZ/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla" \
  bash "$RAIZ/scripts/cd/certificado.sh" renderizar nexus.test "$TMP/tls/borde-tls.conf"
echo "reto-de-prueba" > "$TMP/acme/.well-known/acme-challenge/prueba"
chmod -R a+rX "$TMP"

docker run -d --name "$NOMBRE" --network "$RED" \
  -v "$VOLUMEN:/etc/nginx/conf.d:ro" \
  -v "$TMP/tls:/etc/nginx/nexus-tls:ro" \
  -v "$TMP/le:/etc/letsencrypt:ro" \
  -v "$TMP/acme:/srv/acme:ro" \
  -p 8098:80 -p 8443:443 nginx:1.27-alpine >/dev/null

for _ in $(seq 1 20); do
  curl -s -o /dev/null http://localhost:8098/salud-borde && break
  sleep 0.5
done

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

r=$(curl -sk https://localhost:8443/salud-borde)
[ "$r" = "UP" ] && ok "443 contesta el mismo borde" || falla "443 /salud-borde: '$r'"

r=$(curl -sk -o /dev/null -w '%{http_code}' https://localhost:8443/api/v1/torneos)
[ "$r" = "200" ] && ok "443 enruta la API hasta su servicio (eco de torneos)" || falla "443 /api/v1/torneos: '$r'"

r=$(curl -sk -o /dev/null -w '%{http_code}' https://localhost:8443/.well-known/acme-challenge/prueba)
[ "$r" = "200" ] && ok "443 tambien sirve el reto" || falla "443 reto: '$r'"

echo
if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobacion(es) de HTTPS fallaron."
  exit 1
fi
echo "HTTPS del borde en orden."
