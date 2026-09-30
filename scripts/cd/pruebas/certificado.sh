#!/usr/bin/env bash
# Prueba de scripts/cd/certificado.sh (HTTPS del borde, 28-sep).
#
# El script decide si el borde publica HTTPS y, si se equivoca, deja a todos
# fuera: un fragmento TLS con un certificado que no existe tumba nginx, una
# emision contra un DNS que no apunta aqui gasta el limite de Let's Encrypt, y
# emitir sin el consentimiento de una persona acepta un acuerdo en su nombre.
# `docker`, `curl` y `getent` son falsos; certbot no llega a ejecutarse. Sin
# red ni Docker.
#
#   bash scripts/cd/pruebas/certificado.sh
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$RAIZ/scripts/cd/certificado.sh"
PLANTILLA="$RAIZ/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FALLOS=0
fallo() { FALLOS=$((FALLOS + 1)); echo "  FALLO: $*"; }
ok() { echo "  ok    $*"; }

mkdir -p "$TMP/bin"
cat > "$TMP/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$FALSO_LLAMADAS"
case "$1" in
  ps) [ "${FALSO_BORDE_ARRIBA:-1}" = "1" ] && echo "srv-borde"; exit 0 ;;
  exec)
    if [ "$3 $4" = "nginx -t" ]; then
      [ -f "$FALSO_NEXUS/tls/borde-tls.conf" ] && [ "${FALSO_NGINX_ROMPE_CON_TLS:-0}" = "1" ] && exit 1
      exit 0
    fi
    exit 0 ;;
  run)
    args="$*"
    case "$args" in
      *" certonly "*)
        if [ "${FALSO_CERTBOT_OK:-1}" = "1" ]; then
          d=$(echo "$args" | sed -n 's/.* -d \([^ ]*\).*/\1/p')
          mkdir -p "$FALSO_NEXUS/letsencrypt/live/$d"
          echo cert > "$FALSO_NEXUS/letsencrypt/live/$d/fullchain.pem"
          exit 0
        fi
        exit 1 ;;
      *" renew "*) exit 0 ;;
    esac
    exit 0 ;;
esac
exit 0
EOF
cat > "$TMP/bin/curl" <<'EOF'
#!/usr/bin/env bash
url=""
for a in "$@"; do url="$a"; done
case "$url" in
  */.well-known/acme-challenge/*)
    if [ "${FALSO_SONDA_OK:-1}" = "1" ]; then
      cat "$FALSO_NEXUS/acme/.well-known/acme-challenge/${url##*/}" 2>/dev/null | tr -d '\n'
    else
      printf '<html>404</html>'
    fi ;;
esac
EOF
cat > "$TMP/bin/getent" <<'EOF'
#!/usr/bin/env bash
for ip in ${FALSO_DNS:-}; do echo "$ip      STREAM $3"; done
[ -n "${FALSO_DNS:-}" ]
EOF
chmod +x "$TMP/bin/docker" "$TMP/bin/curl" "$TMP/bin/getent"

# Cada caso empieza con un /opt/nexus vacio (o con lo que el caso deje antes).
nuevo() { rm -rf "$TMP/nexus"; mkdir -p "$TMP/nexus"; : > "$TMP/llamadas"; }
correr() {
  env -i PATH="$TMP/bin:/usr/bin:/bin" NEXUS_DIR="$TMP/nexus" PLANTILLA_TLS="$PLANTILLA" \
    FALSO_NEXUS="$TMP/nexus" FALSO_LLAMADAS="$TMP/llamadas" IP_PUBLICA=35.168.124.119 "$@" \
    bash "$SCRIPT" > "$TMP/salida" 2>&1
}
fragmento="$TMP/nexus/tls/borde-tls.conf"

echo "== Sin DOMINIO_PUBLICO: solo HTTP =="
nuevo
correr
[ $? -eq 0 ] && ok "no rompe el despliegue" || fallo "codigo distinto de 0"
[ ! -f "$fragmento" ] && ok "sin fragmento TLS" || fallo "dejo un fragmento"
grep -q "certonly" "$TMP/llamadas" && fallo "llamo a certbot" || ok "no llama a certbot"

echo "== Habia TLS y se quita el dominio: vuelve a solo HTTP y recarga =="
nuevo; mkdir -p "$TMP/nexus/tls"; echo viejo > "$fragmento"
correr
[ ! -f "$fragmento" ] && ok "quita el fragmento" || fallo "el fragmento sigue"
grep -q "nginx -s reload" "$TMP/llamadas" && ok "recarga el borde" || fallo "no recargo"

echo "== Dominio sin consentimiento: no emite =="
nuevo
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin ACME_ACEPTA_TERMINOS" || ok "no emite sin consentimiento"
grep -q "Subscriber Agreement" "$TMP/salida" && ok "dice por que" || fallo "no explico el motivo"

echo "== DNS que apunta a otro sitio: no emite =="
nuevo
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org ACME_ACEPTA_TERMINOS=true ACME_CORREO=equipo@example.com FALSO_DNS=203.0.113.9
grep -q "certonly" "$TMP/llamadas" && fallo "emitio con el DNS equivocado" || ok "no emite"
grep -q "apunta a 203.0.113.9" "$TMP/salida" && ok "dice a donde apunta" || fallo "no lo dijo"

echo "== El reto no se sirve desde este host: no emite =="
nuevo
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org ACME_ACEPTA_TERMINOS=true ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119 FALSO_SONDA_OK=0
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin servir el reto" || ok "no emite"
ls "$TMP/nexus/acme/.well-known/acme-challenge/" | grep -q sonda && fallo "dejo la sonda" || ok "borra la sonda"

echo "== Todo en orden: emite, activa TLS y recarga =="
nuevo
correr DOMINIO_PUBLICO=Nexus-Battles-VI.duckdns.org ACME_ACEPTA_TERMINOS=true ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119
grep "certonly" "$TMP/llamadas" | grep -q -- "--agree-tos" && ok "certonly con --agree-tos (hay consentimiento)" || fallo "no emitio con --agree-tos"
grep "certonly" "$TMP/llamadas" | grep -q -- "-d nexus-battles-vi.duckdns.org" && ok "nombre en minusculas" || fallo "nombre mal pasado"
grep "certonly" "$TMP/llamadas" | grep -q -- "--email equipo@example.com" && ok "con el correo de contacto" || fallo "sin correo"
grep "certonly" "$TMP/llamadas" | grep -q -- "--test-cert" && fallo "uso el entorno de pruebas sin pedirlo" || ok "entorno real"
[ -f "$fragmento" ] && ok "deja el fragmento TLS" || fallo "no dejo el fragmento"
grep -q "return 301 https://nexus-battles-vi.duckdns.org\$request_uri" "$fragmento" && ok "redirige al dominio" || fallo "redireccion mal"
grep -q "live/nexus-battles-vi.duckdns.org/fullchain.pem" "$fragmento" && ok "apunta al certificado del dominio" || fallo "certificado mal"
grep -q "__DOMINIO__" "$fragmento" && fallo "quedo un __DOMINIO__ sin sustituir" || ok "plantilla sustituida"
grep -q "nginx -s reload" "$TMP/llamadas" && ok "recarga el borde" || fallo "no recargo"
grep -q "HTTPS activo" "$TMP/salida" && ok "lo anuncia" || fallo "no lo anuncio"

echo "== Con el certificado ya emitido: renueva, no vuelve a emitir =="
: > "$TMP/llamadas"
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org FALSO_DNS=35.168.124.119
grep -q " renew " "$TMP/llamadas" && ok "llama a renew" || fallo "no renovo"
grep -q "certonly" "$TMP/llamadas" && fallo "volvio a emitir" || ok "no reemite"
[ -f "$fragmento" ] && ok "mantiene TLS aunque ya no llegue el consentimiento" || fallo "quito TLS al renovar"

echo "== nginx rechaza el fragmento: se quita y el borde sigue en HTTP =="
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org FALSO_DNS=35.168.124.119 FALSO_NGINX_ROMPE_CON_TLS=1
[ $? -eq 0 ] && ok "no rompe el despliegue" || fallo "rompio el despliegue"
[ ! -f "$fragmento" ] && ok "quita el fragmento" || fallo "dejo un fragmento que rompe nginx"

echo "== ACME_PRUEBAS=1: entorno de pruebas de Let's Encrypt =="
nuevo
correr DOMINIO_PUBLICO=nexus-battles-vi.duckdns.org ACME_ACEPTA_TERMINOS=true ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119 ACME_PRUEBAS=1
grep "certonly" "$TMP/llamadas" | grep -q -- "--test-cert" && ok "con --test-cert" || fallo "sin --test-cert"

echo "== Una IP no es un dominio =="
nuevo
correr DOMINIO_PUBLICO=35.168.124.119 ACME_ACEPTA_TERMINOS=true ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119
grep -q "certonly" "$TMP/llamadas" && fallo "emitio para una IP" || ok "no emite para una IP"

echo "== renderizar rechaza un nombre invalido =="
env -i PATH="$TMP/bin:/usr/bin:/bin" PLANTILLA_TLS="$PLANTILLA" bash "$SCRIPT" renderizar 'mal;dominio' "$TMP/x.conf" > "$TMP/salida" 2>&1
[ $? -ne 0 ] && ok "se niega" || fallo "acepto 'mal;dominio'"

if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS fallo(s)."
  exit 1
fi
echo "Todo en orden."
