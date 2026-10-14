#!/usr/bin/env bash
# Prueba de scripts/cd/certificado.sh (HTTPS del borde, 28-sep; 6-oct).
#
# El script decide si el borde publica HTTPS y, si se equivoca, deja a todos
# fuera: un fragmento TLS con un certificado que no existe tumba nginx, uno de
# PRUEBAS lleva a todo el mundo a un aviso de certificado no confiable, una
# emision contra un DNS que no apunta aqui gasta el limite de Let's Encrypt, y
# emitir sin el consentimiento de una persona acepta un acuerdo en su nombre.
# `docker`, `curl` y `getent` son falsos; certbot no llega a ejecutarse. Sin
# red ni Docker.
#
# El `docker` falso guarda lo que "emite" certbot en un almacen aparte y deja
# live/ sin permisos, como lo deja certbot (0700 de root): el script tiene que
# leer el certificado por el contenedor, no con un `[ -f ]` desde el host.
#
#   bash scripts/cd/pruebas/certificado.sh
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$RAIZ/scripts/cd/certificado.sh"
PLANTILLA="$RAIZ/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla"
TMP="$(mktemp -d)"
trap 'chmod -R u+rwx "$TMP" 2>/dev/null; rm -rf "$TMP"' EXIT
FALLOS=0
fallo() { FALLOS=$((FALLOS + 1)); echo "  FALLO: $*"; }
ok() { echo "  ok    $*"; }

mkdir -p "$TMP/bin"
cat > "$TMP/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$FALSO_LLAMADAS"
almacen="$FALSO_NEXUS/almacen-certbot"
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
    # Lector del certificado (python3 dentro del contenedor, como root).
    if [[ "$args" == *"--entrypoint python3"* ]]; then
      nombre="${@: -1}"
      [ -f "$almacen/$nombre" ] && cat "$almacen/$nombre"
      exit 0
    fi
    case "$args" in
      *" certonly "*)
        if [ "${FALSO_CERTBOT_OK:-1}" = "1" ]; then
          cn=$(echo "$args" | sed -n 's/.*--cert-name \([^ ]*\).*/\1/p')
          nombres=$(echo "$args" | grep -o -- '-d [^ ]*' | sed 's/^-d //' | sort | tr '\n' ' ' | sed 's/ $//')
          pruebas=0
          emisor="CN=E7,O=Let's Encrypt,C=US"
          if [[ "$args" == *"--test-cert"* ]]; then
            pruebas=1
            emisor="CN=(STAGING) Ersatz Edamame E1,O=(STAGING) Let's Encrypt,C=US"
          fi
          mkdir -p "$almacen" "$FALSO_NEXUS/letsencrypt/live/$cn"
          printf 'emisor=%s\nnombres=%s\ncaduca=2027-01-05T00:00:00Z\ndias=89\npruebas=%s\n' \
            "$emisor" "$nombres" "$pruebas" > "$almacen/$cn"
          # Como certbot: live/ solo para root.
          chmod 000 "$FALSO_NEXUS/letsencrypt/live"
          exit 0
        fi
        exit 1 ;;
      *" renew "*) [ "${FALSO_RENEW_OK:-1}" = "1" ] && exit 0; exit 1 ;;
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
# FALSO_DNS para todos los nombres; FALSO_DNS_<nombre con _> para uno solo.
clave="FALSO_DNS_$(echo "$2" | tr '.-' '__')"
ips="${!clave-${FALSO_DNS:-}}"
for ip in $ips; do echo "$ip      STREAM $2"; done
[ -n "$ips" ]
EOF
chmod +x "$TMP/bin/docker" "$TMP/bin/curl" "$TMP/bin/getent"

# Cada caso empieza con un /opt/nexus vacio (o con lo que el caso deje antes).
nuevo() { chmod -R u+rwx "$TMP/nexus" 2>/dev/null; rm -rf "$TMP/nexus"; mkdir -p "$TMP/nexus"; : > "$TMP/llamadas"; }
correr() {
  : > "$TMP/llamadas"
  env -i PATH="$TMP/bin:/usr/bin:/bin" NEXUS_DIR="$TMP/nexus" PLANTILLA_TLS="$PLANTILLA" \
    FALSO_NEXUS="$TMP/nexus" FALSO_LLAMADAS="$TMP/llamadas" IP_PUBLICA=35.168.124.119 "$@" \
    bash "$SCRIPT" > "$TMP/salida" 2>&1
}
correr_accion() {
  local accion="$1"
  shift
  : > "$TMP/llamadas"
  env -i PATH="$TMP/bin:/usr/bin:/bin" NEXUS_DIR="$TMP/nexus" PLANTILLA_TLS="$PLANTILLA" \
    FALSO_NEXUS="$TMP/nexus" FALSO_LLAMADAS="$TMP/llamadas" IP_PUBLICA=35.168.124.119 "$@" \
    bash "$SCRIPT" "$accion" > "$TMP/salida" 2>&1
}
fragmento="$TMP/nexus/tls/borde-tls.conf"
D=nexus-battles-vi.duckdns.org
CONSENTIMIENTO=(ACME_ACEPTA_TERMINOS=true FALSO_DNS=35.168.124.119)

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
correr DOMINIO_PUBLICO=$D ACME_CORREO=equipo@example.com FALSO_DNS=35.168.124.119
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin ACME_ACEPTA_TERMINOS" || ok "no emite sin consentimiento"
grep -q "Subscriber Agreement" "$TMP/salida" && ok "dice por que" || fallo "no explico el motivo"
grep -q "TLS apagado: sin consentimiento" "$TMP/salida" && ok "y lo repite al quedarse en HTTP" || fallo "no dijo por que sigue en HTTP"

echo "== DNS que apunta a otro sitio: no emite =="
nuevo
correr DOMINIO_PUBLICO=$D ACME_ACEPTA_TERMINOS=true FALSO_DNS=203.0.113.9
grep -q "certonly" "$TMP/llamadas" && fallo "emitio con el DNS equivocado" || ok "no emite"
grep -q "apunta a 203.0.113.9" "$TMP/salida" && ok "dice a donde apunta" || fallo "no lo dijo"

echo "== El nombre aun no resuelve: no emite =="
nuevo
correr DOMINIO_PUBLICO=$D ACME_ACEPTA_TERMINOS=true
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin DNS" || ok "no emite"
grep -q "todavia no resuelve" "$TMP/salida" && ok "pide el registro A" || fallo "no lo dijo"

echo "== El reto no se sirve desde este host: no emite =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}" FALSO_SONDA_OK=0
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin servir el reto" || ok "no emite"
ls "$TMP/nexus/acme/.well-known/acme-challenge/" | grep -q sonda && fallo "dejo la sonda" || ok "borra la sonda"

echo "== Todo en orden: emite, activa TLS y recarga, aunque live/ sea solo de root =="
nuevo
correr DOMINIO_PUBLICO=Nexus-Battles-VI.duckdns.org ACME_CORREO=equipo@example.com "${CONSENTIMIENTO[@]}"
grep "certonly" "$TMP/llamadas" | grep -q -- "--agree-tos" && ok "certonly con --agree-tos (hay consentimiento)" || fallo "no emitio con --agree-tos"
grep "certonly" "$TMP/llamadas" | grep -q -- "-d $D" && ok "nombre en minusculas" || fallo "nombre mal pasado"
grep "certonly" "$TMP/llamadas" | grep -q -- "--cert-name $D" && ok "el linaje se llama como el dominio" || fallo "sin --cert-name"
grep "certonly" "$TMP/llamadas" | grep -q -- "--email equipo@example.com" && ok "con el correo de contacto" || fallo "sin correo"
grep "certonly" "$TMP/llamadas" | grep -q -- "--test-cert" && fallo "uso el entorno de pruebas sin pedirlo" || ok "entorno real"
[ -f "$fragmento" ] && ok "deja el fragmento TLS (lee el certificado por el contenedor)" || fallo "no dejo el fragmento: ¿volvio a mirar live/ desde el host?"
grep -q "return 301 https://$D\$request_uri" "$fragmento" && ok "redirige al dominio" || fallo "redireccion mal"
grep -q "live/$D/fullchain.pem" "$fragmento" && ok "apunta al certificado del dominio" || fallo "certificado mal"
grep -q "__DOMINIO__" "$fragmento" && fallo "quedo un __DOMINIO__ sin sustituir" || ok "plantilla sustituida"
grep -q '\^/mailpit/' "$fragmento" && ok "/mailpit/ no se redirige (tunel interno)" || fallo "la bandeja de pruebas quedaria detras de la redireccion"
grep -v '^ *#' "$fragmento" | grep -q "Strict-Transport-Security" && fallo "HSTS sin pedirlo" || ok "sin HSTS por omision"
grep -q "nginx -s reload" "$TMP/llamadas" && ok "recarga el borde" || fallo "no recargo"
grep -q "HTTPS activo" "$TMP/salida" && ok "lo anuncia" || fallo "no lo anuncio"

echo "== Con el certificado ya emitido: renueva, no vuelve a emitir =="
correr DOMINIO_PUBLICO=$D FALSO_DNS=35.168.124.119
grep -q " renew " "$TMP/llamadas" && ok "llama a renew" || fallo "no renovo"
grep -q "certonly" "$TMP/llamadas" && fallo "volvio a emitir" || ok "no reemite"
[ -f "$fragmento" ] && ok "mantiene TLS aunque ya no llegue el consentimiento" || fallo "quito TLS al renovar"

echo "== renew falla: el certificado vigente se sigue publicando =="
correr DOMINIO_PUBLICO=$D FALSO_DNS=35.168.124.119 FALSO_RENEW_OK=0
[ $? -eq 0 ] && ok "no rompe el despliegue" || fallo "rompio el despliegue"
[ -f "$fragmento" ] && ok "sigue en HTTPS" || fallo "quito TLS por un renew fallido"
grep -q "certbot renew fallo" "$TMP/salida" && ok "lo avisa" || fallo "no aviso"

echo "== nginx rechaza el fragmento: se quita y el borde sigue en HTTP =="
correr DOMINIO_PUBLICO=$D FALSO_DNS=35.168.124.119 FALSO_NGINX_ROMPE_CON_TLS=1
[ $? -eq 0 ] && ok "no rompe el despliegue" || fallo "rompio el despliegue"
[ ! -f "$fragmento" ] && ok "quita el fragmento" || fallo "dejo un fragmento que rompe nginx"

echo "== ACME_PRUEBAS=1: emite en el entorno de pruebas y NO lo publica =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}" ACME_PRUEBAS=1
grep "certonly" "$TMP/llamadas" | grep -q -- "--test-cert" && ok "con --test-cert" || fallo "sin --test-cert"
grep "certonly" "$TMP/llamadas" | grep -q -- "--register-unsafely-without-email" && ok "sin correo: cuenta sin correo (ACME_CORREO es opcional)" || fallo "sin ACME_CORREO no paso --register-unsafely-without-email"
[ ! -f "$fragmento" ] && ok "un certificado de pruebas no se publica" || fallo "publico un certificado de PRUEBAS"
grep -q "quita ACME_PRUEBAS" "$TMP/salida" && ok "dice que el flujo funciona y como seguir" || fallo "no explico el siguiente paso"

echo "== Se quita ACME_PRUEBAS: se pide el real con --force-renewal y se publica =="
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}"
grep "certonly" "$TMP/llamadas" | grep -q -- "--force-renewal" && ok "certonly --force-renewal" || fallo "no forzo la emision real (certbot se quedaria con el de pruebas)"
grep "certonly" "$TMP/llamadas" | grep -q -- "--test-cert" && fallo "volvio al entorno de pruebas" || ok "contra el entorno real"
[ -f "$fragmento" ] && ok "ahora si publica HTTPS" || fallo "no publico el certificado real"

echo "== Con el real ya emitido, ACME_PRUEBAS=1 no lo cambia por uno de pruebas =="
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}" ACME_PRUEBAS=1
grep -q "certonly" "$TMP/llamadas" && fallo "pidio un certificado de pruebas encima del real" || ok "no reemite"
grep -q " renew " "$TMP/llamadas" && ok "solo renueva" || fallo "no renovo"
[ -f "$fragmento" ] && ok "sigue publicando el real" || fallo "quito TLS"

echo "== De pruebas y sin consentimiento: no se emite ni se publica =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}" ACME_PRUEBAS=1
correr DOMINIO_PUBLICO=$D FALSO_DNS=35.168.124.119
grep -q "certonly" "$TMP/llamadas" && fallo "emitio sin consentimiento" || ok "no emite"
[ ! -f "$fragmento" ] && ok "el de pruebas sigue sin publicarse" || fallo "publico el de pruebas"

echo "== DOMINIOS_ADICIONALES nuevo: se amplia el mismo linaje =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}"
correr DOMINIO_PUBLICO=$D DOMINIOS_ADICIONALES="WWW.$D" "${CONSENTIMIENTO[@]}"
grep "certonly" "$TMP/llamadas" | grep -q -- "--renew-with-new-domains" && ok "amplia con --renew-with-new-domains" || fallo "no amplio el certificado"
grep "certonly" "$TMP/llamadas" | grep -q -- "-d www.$D" && ok "con el nombre nuevo, en minusculas" || fallo "sin el nombre nuevo"
grep "certonly" "$TMP/llamadas" | grep -q -- "--cert-name $D" && ok "en el linaje del dominio principal" || fallo "linaje distinto"
[ -f "$fragmento" ] && ok "sigue en HTTPS" || fallo "quito TLS"

echo "== El nombre adicional aun no apunta aqui: no amplia, pero sigue con el vigente =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}"
correr DOMINIO_PUBLICO=$D DOMINIOS_ADICIONALES="www.$D" ACME_ACEPTA_TERMINOS=true FALSO_DNS=35.168.124.119 FALSO_DNS_www_nexus_battles_vi_duckdns_org=203.0.113.9
grep -q "certonly" "$TMP/llamadas" && fallo "amplio contra un DNS equivocado" || ok "no amplia"
[ -f "$fragmento" ] && ok "el certificado vigente se sigue publicando" || fallo "quito HTTPS por un nombre adicional"
grep -q "Se sigue publicando" "$TMP/salida" && ok "avisa de por que no amplio" || fallo "no aviso"

echo "== HSTS_SEGUNDOS=300: HSTS gradual =="
nuevo
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}" HSTS_SEGUNDOS=300
grep -q 'add_header Strict-Transport-Security "max-age=300" always;' "$fragmento" && ok "max-age=300" || fallo "sin HSTS"
grep -v '^ *#' "$fragmento" | grep -qi "includeSubDomains\|preload" && fallo "includeSubDomains o preload sin pedirlo" || ok "sin includeSubDomains ni preload"

echo "== HSTS_SEGUNDOS invalido: sin HSTS y lo dice =="
correr DOMINIO_PUBLICO=$D FALSO_DNS=35.168.124.119 HSTS_SEGUNDOS='1;preload'
grep -v '^ *#' "$fragmento" | grep -q "Strict-Transport-Security" && fallo "publico un HSTS invalido" || ok "sin HSTS"
grep -q "no es un numero de segundos" "$TMP/salida" && ok "avisa" || fallo "no aviso"

echo "== Una IP no es un dominio =="
nuevo
correr DOMINIO_PUBLICO=35.168.124.119 "${CONSENTIMIENTO[@]}"
grep -q "certonly" "$TMP/llamadas" && fallo "emitio para una IP" || ok "no emite para una IP"

echo "== estado: emisor, nombres y fechas; nunca la llave =="
nuevo
correr_accion estado DOMINIO_PUBLICO=$D
grep -q "sin certificado de $D" "$TMP/salida" && ok "sin certificado lo dice" || fallo "no lo dijo"
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}"
correr_accion estado DOMINIO_PUBLICO=$D
grep -q "emisor=CN=E7" "$TMP/salida" && ok "dice el emisor" || fallo "sin emisor"
grep -q "nombres=$D" "$TMP/salida" && ok "dice los nombres" || fallo "sin nombres"
grep -q "fragmento TLS: presente" "$TMP/salida" && ok "dice si esta publicado" || fallo "no dijo si esta publicado"
grep -qi "privkey\|PRIVATE KEY" "$TMP/salida" && fallo "menciono la llave" || ok "ni rastro de la llave"

echo "== probar-renovacion: certbot renew --dry-run del linaje =="
nuevo
correr_accion probar-renovacion DOMINIO_PUBLICO=$D
[ $? -ne 0 ] && ok "sin certificado falla (no hay nada que probar)" || fallo "dijo OK sin certificado"
correr DOMINIO_PUBLICO=$D "${CONSENTIMIENTO[@]}"
correr_accion probar-renovacion DOMINIO_PUBLICO=$D
[ $? -eq 0 ] && ok "con certificado pasa" || fallo "fallo con certificado"
grep " renew " "$TMP/llamadas" | grep -q -- "--dry-run" && ok "con --dry-run" || fallo "sin --dry-run: habria renovado de verdad"
grep " renew " "$TMP/llamadas" | grep -q -- "--cert-name $D" && ok "del linaje del dominio" || fallo "sin --cert-name"
correr_accion probar-renovacion DOMINIO_PUBLICO=$D FALSO_RENEW_OK=0
[ $? -ne 0 ] && ok "si la simulacion falla, falla" || fallo "tapo un fallo de renovacion"

echo "== renderizar rechaza un nombre invalido =="
env -i PATH="$TMP/bin:/usr/bin:/bin" PLANTILLA_TLS="$PLANTILLA" bash "$SCRIPT" renderizar 'mal;dominio' "$TMP/x.conf" > "$TMP/salida" 2>&1
[ $? -ne 0 ] && ok "se niega" || fallo "acepto 'mal;dominio'"

if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS fallo(s)."
  exit 1
fi
echo "Todo en orden."
