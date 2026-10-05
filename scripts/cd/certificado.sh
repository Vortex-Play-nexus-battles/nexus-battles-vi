#!/usr/bin/env bash
#
# HTTPS del borde con Let's Encrypt — 28-sep. USD 0.
#
# ## Que hace
#
# Deja el borde (srv-borde) en uno de dos estados, y nunca en uno roto:
#
#   SOLO HTTP   sin /opt/nexus/tls/borde-tls.conf: el `include` de
#               borde-dev.conf no encuentra nada y el borde es el de siempre.
#   HTTP + TLS  con el fragmento (plantilla en
#               infrastructure/red-balanceo/tls/): el mismo server escucha en
#               el 443 con el certificado y el 80 redirige a https salvo
#               /salud-borde y el reto de ACME.
#
# Pasa a HTTP + TLS solo si se cumplen TODAS estas condiciones, y si falta
# alguna lo dice y se queda (o vuelve) en SOLO HTTP sin romper el despliegue:
#
#   1. DOMINIO_PUBLICO tiene un nombre (no una IP).
#   2. ACME_ACEPTA_TERMINOS=true: el consentimiento EXPLICITO del Subscriber
#      Agreement de Let's Encrypt. Lo da una persona; el script no lo supone.
#   3. ACME_CORREO: el contacto de los avisos de caducidad.
#   4. El DNS del nombre apunta a ESTE host (IP publica del servicio de
#      metadatos de EC2).
#   5. Este host se sirve a si mismo el reto por http://DOMINIO/.well-known/...
#      (sonda propia antes de molestar a Let's Encrypt: los fallos de
#      validacion cuentan contra un limite por hora).
#
# Con el certificado ya emitido, cada ejecucion hace `certbot renew` (no hace
# nada si faltan mas de 30 dias) y recarga nginx. Lo llaman desplegar.sh en
# cada despliegue de plataforma y certificado-dev.yml una vez al dia.
#
# ## Uso
#
#   certificado.sh                               # asegurar (lo normal)
#   certificado.sh renderizar DOMINIO DESTINO    # solo escribe el fragmento
#
# Variables: DOMINIO_PUBLICO, DOMINIOS_ADICIONALES (mas nombres en el mismo
# certificado, separados por espacios), ACME_ACEPTA_TERMINOS, ACME_CORREO,
# ACME_PRUEBAS=1 (entorno de pruebas de Let's Encrypt), NEXUS_DIR
# (/opt/nexus), CERTBOT_IMAGEN, PLANTILLA_TLS, IP_PUBLICA (para las pruebas).
set -euo pipefail

DIR="${NEXUS_DIR:-/opt/nexus}"
PLANTILLA="${PLANTILLA_TLS:-$DIR/web/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla}"
FRAGMENTO="$DIR/tls/borde-tls.conf"
IMAGEN="${CERTBOT_IMAGEN:-certbot/certbot:v5.8.0}"
BORDE="${CONTENEDOR_BORDE:-srv-borde}"

aviso() { echo "::warning::$*"; }

# Un nombre DNS en minusculas con al menos un punto; nunca una IP (para la IP
# esta la plantilla de la sonda, no la del borde).
nombre_valido() {
  local n="$1"
  [[ "$n" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || return 1
  [[ ! "$n" =~ ^[0-9.]+$ ]]
}

renderizar() {
  local dominio="$1" destino="$2"
  if ! nombre_valido "$dominio"; then
    echo "::error::'$dominio' no es un nombre de dominio valido."
    return 1
  fi
  if [ ! -f "$PLANTILLA" ]; then
    echo "::error::No esta la plantilla $PLANTILLA."
    return 1
  fi
  mkdir -p "$(dirname "$destino")"
  sed "s/__DOMINIO__/$dominio/g" "$PLANTILLA" > "$destino.tmp"
  mv "$destino.tmp" "$destino"
}

recargar_borde() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$BORDE"; then
    echo "  (el borde no esta en marcha: se aplicara al arrancar)"
    return 0
  fi
  if docker exec "$BORDE" nginx -t >/dev/null 2>&1; then
    docker exec "$BORDE" nginx -s reload >/dev/null
    return 0
  fi
  # Configuracion invalida con el fragmento: se quita y el borde vuelve a solo
  # HTTP. Nunca se deja un nginx que no recarga.
  if [ -f "$FRAGMENTO" ]; then
    aviso "nginx -t falla con el fragmento TLS; se quita y el borde sigue solo en HTTP."
    docker exec "$BORDE" nginx -t 2>&1 | tail -3 | sed 's/^/    /' || true
    rm -f "$FRAGMENTO"
    docker exec "$BORDE" nginx -t >/dev/null 2>&1 && docker exec "$BORDE" nginx -s reload >/dev/null
    return 1
  fi
  echo "::error::nginx -t falla sin fragmento TLS: el problema no es este script."
  docker exec "$BORDE" nginx -t 2>&1 | tail -3 | sed 's/^/    /' || true
  return 1
}

solo_http() {
  local motivo="$1"
  echo "  TLS apagado: $motivo"
  if [ -f "$FRAGMENTO" ]; then
    rm -f "$FRAGMENTO"
    echo "  se quita el fragmento TLS que habia; el borde vuelve a solo HTTP"
    recargar_borde || true
  fi
}

ip_de_este_host() {
  if [ -n "${IP_PUBLICA:-}" ]; then
    echo "$IP_PUBLICA"
    return 0
  fi
  local token
  token=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60" --max-time 3 || true)
  curl -s --max-time 3 ${token:+-H "X-aws-ec2-metadata-token: $token"} \
    "http://169.254.169.254/latest/meta-data/public-ipv4" || true
}

certbot() {
  docker run --rm \
    -v "$DIR/letsencrypt:/etc/letsencrypt" \
    -v "$DIR/acme:/var/www/acme" \
    "$IMAGEN" "$@"
}

asegurar() {
  mkdir -p "$DIR/tls" "$DIR/letsencrypt" "$DIR/acme/.well-known/acme-challenge"
  local dominio="${DOMINIO_PUBLICO:-}"
  dominio="$(printf '%s' "$dominio" | tr 'A-Z' 'a-z' | sed 's|^https\?://||; s|/.*$||')"

  if [ -z "$dominio" ]; then
    solo_http "sin DOMINIO_PUBLICO (variable del entorno dev en GitHub)"
    return 0
  fi
  if ! nombre_valido "$dominio"; then
    aviso "DOMINIO_PUBLICO='$dominio' no es un nombre de dominio valido."
    solo_http "nombre no valido"
    return 0
  fi

  local vivo="$DIR/letsencrypt/live/$dominio/fullchain.pem"
  if [ -f "$vivo" ]; then
    echo "  certificado de $dominio presente: renovar si toca (a menos de 30 dias)"
    if ! certbot renew --webroot -w /var/www/acme --non-interactive --quiet; then
      aviso "certbot renew fallo; el certificado actual sigue valiendo hasta su fecha."
    fi
  else
    if [ "${ACME_ACEPTA_TERMINOS:-}" != "true" ]; then
      aviso "Falta ACME_ACEPTA_TERMINOS=true: el consentimiento del Subscriber Agreement de Let's Encrypt lo da una persona."
      solo_http "sin consentimiento para emitir"
      return 0
    fi
    if [ -z "${ACME_CORREO:-}" ]; then
      aviso "Falta ACME_CORREO: el buzon que recibe los avisos de caducidad."
      solo_http "sin correo de contacto"
      return 0
    fi

    local propia resuelve
    propia="$(ip_de_este_host)"
    resuelve="$(getent ahostsv4 "$dominio" 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')"
    if [ -z "$resuelve" ]; then
      solo_http "$dominio todavia no resuelve (¿se creo el registro A?)"
      return 0
    fi
    if [ -n "$propia" ] && ! printf '%s\n' $resuelve | grep -qx "$propia"; then
      solo_http "$dominio apunta a $resuelve y este host es $propia"
      return 0
    fi

    # Sonda propia del reto: si este host no se sirve a si mismo el fichero
    # por el nombre, Let's Encrypt tampoco podra, y un fallo alli cuenta
    # contra su limite por hora.
    local sonda contenido
    sonda="sonda-$(date +%s)-$$"
    printf 'nexus-%s\n' "$sonda" > "$DIR/acme/.well-known/acme-challenge/$sonda"
    contenido="$(curl -s --max-time 10 "http://$dominio/.well-known/acme-challenge/$sonda" || true)"
    rm -f "$DIR/acme/.well-known/acme-challenge/$sonda"
    if [ "$contenido" != "nexus-$sonda" ]; then
      solo_http "http://$dominio/.well-known/acme-challenge/ no sirve el reto desde este host (¿puerto 80, borde, DNS?)"
      return 0
    fi

    local nombres=(-d "$dominio") extra
    for extra in ${DOMINIOS_ADICIONALES:-}; do
      if nombre_valido "$extra"; then nombres+=(-d "$extra"); fi
    done
    local pruebas=()
    if [ "${ACME_PRUEBAS:-0}" = "1" ]; then pruebas=(--test-cert); fi

    echo "  emitiendo el certificado de $dominio (HTTP-01)${pruebas:+ en el entorno de PRUEBAS}"
    if ! certbot certonly --webroot -w /var/www/acme "${nombres[@]}" \
        --email "$ACME_CORREO" --agree-tos --no-eff-email --non-interactive \
        --keep-until-expiring "${pruebas[@]}"; then
      solo_http "Let's Encrypt no emitio el certificado (el detalle esta arriba)"
      return 0
    fi
  fi

  if [ ! -f "$vivo" ]; then
    solo_http "no aparece $vivo"
    return 0
  fi
  renderizar "$dominio" "$FRAGMENTO"
  if recargar_borde; then
    echo "  HTTPS activo en https://$dominio (el 80 redirige salvo /salud-borde y ACME)"
  fi
}

case "${1:-asegurar}" in
  asegurar) asegurar ;;
  renderizar) renderizar "${2:?dominio}" "${3:?destino}" ;;
  *) echo "uso: certificado.sh [asegurar | renderizar DOMINIO DESTINO]"; exit 2 ;;
esac
