#!/usr/bin/env bash
#
# HTTPS del borde con Let's Encrypt — 28-sep, revisado el 6-oct. USD 0.
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
#               /salud-borde, el reto de ACME y /mailpit/.
#
# Solo pasa a HTTP + TLS con un certificado DE CONFIANZA del dominio. Para
# pedirlo a Let's Encrypt hace falta todo esto, y si falta algo lo dice y se
# queda (o vuelve) en SOLO HTTP sin romper el despliegue:
#
#   1. DOMINIO_PUBLICO tiene un nombre (no una IP).
#   2. ACME_ACEPTA_TERMINOS=true: el consentimiento EXPLICITO del Subscriber
#      Agreement de Let's Encrypt. Lo da una persona; el script no lo supone.
#   3. El DNS de cada nombre apunta a ESTE host (IP publica del servicio de
#      metadatos de EC2).
#   4. Este host se sirve a si mismo el reto por http://NOMBRE/.well-known/...
#      (sonda propia antes de molestar a Let's Encrypt: los fallos de
#      validacion cuentan contra un limite por hora).
#
# Con el certificado ya emitido, cada ejecucion hace `certbot renew` (no hace
# nada si no toca) y recarga nginx. Lo llaman desplegar.sh en cada despliegue
# de plataforma y certificado-dev.yml una vez al dia.
#
# ## 6-oct — lo que cambio al revisarlo para encenderlo de verdad
#
#   - El estado del certificado se lee DENTRO del contenedor de certbot, como
#     root. certbot crea live/ y archive/ con permisos 0700 de root
#     (storage.py, new_lineage) y el usuario del despliegue (ubuntu) no puede
#     ni comprobar que el fichero existe: con el `[ -f ]` de antes, un
#     certificado recien emitido "no aparecia" y el borde volvia a solo HTTP
#     sin decir por que.
#   - Un certificado del entorno de PRUEBAS (ACME_PRUEBAS=1) no se publica
#     nunca: demuestra que el DNS y el reto funcionan sin gastar los limites
#     de Let's Encrypt, pero ningun navegador confia en el. Al quitar
#     ACME_PRUEBAS se reemite en produccion con --force-renewal: sin el,
#     certbot se queda con el de pruebas porque "no toca renovar".
#   - Si DOMINIOS_ADICIONALES trae un nombre que el certificado no cubre, se
#     amplia (--renew-with-new-domains) en vez de renovar el de siempre. El
#     linaje se llama siempre como el dominio (--cert-name): live/DOMINIO/ no
#     cambia de nombre al ampliarlo.
#   - ACME_CORREO es opcional. Let's Encrypt dejo de mandar avisos de
#     caducidad el 4-jun-2025 y ya no guarda los correos de las cuentas; la
#     caducidad la vigila certificado-dev.yml desde fuera.
#   - HSTS_SEGUNDOS enciende Strict-Transport-Security, gradual y apagado por
#     omision (ver la plantilla).
#   - Dos usos nuevos: `estado` (emisor, nombres y fechas; nunca la llave) y
#     `probar-renovacion` (certbot renew --dry-run contra el entorno de
#     pruebas: no toca el certificado vigente).
#
# ## Uso
#
#   certificado.sh                               # asegurar (lo normal)
#   certificado.sh estado                        # que certificado hay
#   certificado.sh probar-renovacion             # renovacion simulada
#   certificado.sh renderizar DOMINIO DESTINO    # solo escribe el fragmento
#
# Variables: DOMINIO_PUBLICO, DOMINIOS_ADICIONALES (mas nombres en el mismo
# certificado, separados por espacios), ACME_ACEPTA_TERMINOS, ACME_CORREO
# (opcional), ACME_PRUEBAS=1 (entorno de pruebas de Let's Encrypt),
# HSTS_SEGUNDOS (vacio = sin HSTS), NEXUS_DIR (/opt/nexus), CERTBOT_IMAGEN,
# PLANTILLA_TLS, IP_PUBLICA (para las pruebas).
set -euo pipefail

DIR="${NEXUS_DIR:-/opt/nexus}"
PLANTILLA="${PLANTILLA_TLS:-$DIR/web/infrastructure/red-balanceo/tls/borde-tls.conf.plantilla}"
FRAGMENTO="$DIR/tls/borde-tls.conf"
IMAGEN="${CERTBOT_IMAGEN:-certbot/certbot:v5.8.0}"
BORDE="${CONTENEDOR_BORDE:-srv-borde}"

# Por que no se publico HTTPS, para decirlo una vez al final.
MOTIVO=""

aviso() { echo "::warning::$*"; }

# Un nombre DNS en minusculas con al menos un punto; nunca una IP (para la IP
# esta la plantilla de la sonda, no la del borde).
nombre_valido() {
  local n="$1"
  [[ "$n" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || return 1
  [[ ! "$n" =~ ^[0-9.]+$ ]]
}

# "HTTPS://Juego.Example.com/x" -> "juego.example.com".
normalizar() {
  printf '%s' "$1" | tr 'A-Z' 'a-z' | sed 's|^https\?://||; s|/.*$||'
}

# HSTS_SEGUNDOS si es un numero de segundos; nada si no.
hsts_segundos() {
  local s="${HSTS_SEGUNDOS:-}"
  if [[ "$s" =~ ^[1-9][0-9]{0,8}$ ]]; then
    echo "$s"
  fi
}

renderizar() {
  local dominio="$1" destino="$2" hsts
  if ! nombre_valido "$dominio"; then
    echo "::error::'$dominio' no es un nombre de dominio valido."
    return 1
  fi
  if [ ! -f "$PLANTILLA" ]; then
    echo "::error::No esta la plantilla $PLANTILLA."
    return 1
  fi
  hsts="$(hsts_segundos)"
  mkdir -p "$(dirname "$destino")"
  if [ -n "$hsts" ]; then
    sed -e "s/__DOMINIO__/$dominio/g" \
      -e "s/^# __HSTS__.*$/add_header Strict-Transport-Security \"max-age=$hsts\" always;/" \
      "$PLANTILLA" > "$destino.tmp"
  else
    sed "s/__DOMINIO__/$dominio/g" "$PLANTILLA" > "$destino.tmp"
  fi
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

# Lo que dice el certificado vigente de un linaje, leido DENTRO del contenedor
# de certbot (como root: live/ es 0700) y con la misma biblioteca con la que
# certbot lo escribio. Una linea clave=valor por dato y nada si no hay
# certificado. Lee cert.pem (solo la hoja); la llave no se abre nunca.
LEER_CERTIFICADO_PY='
import datetime, sys
from cryptography import x509
try:
    with open("/etc/letsencrypt/live/%s/cert.pem" % sys.argv[1], "rb") as f:
        cert = x509.load_pem_x509_certificate(f.read())
except (OSError, ValueError):
    sys.exit(0)
emisor = cert.issuer.rfc4514_string()
try:
    san = cert.extensions.get_extension_for_class(x509.SubjectAlternativeName)
    nombres = sorted(san.value.get_values_for_type(x509.DNSName))
except x509.ExtensionNotFound:
    nombres = []
caduca = getattr(cert, "not_valid_after_utc", None)
if caduca is None:
    caduca = cert.not_valid_after.replace(tzinfo=datetime.timezone.utc)
ahora = datetime.datetime.now(datetime.timezone.utc)
pruebas = "STAGING" in emisor.upper() or "FAKE LE" in emisor.upper()
print("emisor=" + emisor)
print("nombres=" + " ".join(nombres))
print("caduca=" + caduca.strftime("%Y-%m-%dT%H:%M:%SZ"))
print("dias=%d" % (caduca - ahora).days)
print("pruebas=%d" % (1 if pruebas else 0))
'
leer_certificado() {
  docker run --rm --entrypoint python3 \
    -v "$DIR/letsencrypt:/etc/letsencrypt:ro" \
    "$IMAGEN" -c "$LEER_CERTIFICADO_PY" "$1" 2>/dev/null || true
}

# campo CLAVE "lineas clave=valor"
campo() {
  printf '%s\n' "$2" | sed -n "s/^$1=//p" | head -1
}

# Antes de pedir nada a Let's Encrypt: consentimiento, DNS de cada nombre
# hacia este host y el reto servido por el 80. Si algo falta deja el motivo en
# MOTIVO y devuelve 1.
listo_para_emitir() {
  if [ "${ACME_ACEPTA_TERMINOS:-}" != "true" ]; then
    aviso "Falta ACME_ACEPTA_TERMINOS=true: el consentimiento del Subscriber Agreement de Let's Encrypt lo da una persona."
    MOTIVO="sin consentimiento para emitir"
    return 1
  fi

  local propia n resuelve sonda contenido
  propia="$(ip_de_este_host)"
  for n in "$@"; do
    resuelve="$(getent ahostsv4 "$n" 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')"
    if [ -z "$resuelve" ]; then
      MOTIVO="$n todavia no resuelve (¿se creo el registro A?)"
      echo "  $MOTIVO"
      return 1
    fi
    # shellcheck disable=SC2086  # $resuelve es una lista separada por espacios
    if [ -n "$propia" ] && ! printf '%s\n' $resuelve | grep -qx "$propia"; then
      MOTIVO="$n apunta a $resuelve y este host es $propia"
      echo "  $MOTIVO"
      return 1
    fi

    # Sonda propia del reto: si este host no se sirve a si mismo el fichero
    # por el nombre, Let's Encrypt tampoco podra, y un fallo alli cuenta
    # contra su limite por hora.
    sonda="sonda-$(date +%s)-$$"
    printf 'nexus-%s\n' "$sonda" > "$DIR/acme/.well-known/acme-challenge/$sonda"
    contenido="$(curl -s --max-time 10 "http://$n/.well-known/acme-challenge/$sonda" || true)"
    rm -f "$DIR/acme/.well-known/acme-challenge/$sonda"
    if [ "$contenido" != "nexus-$sonda" ]; then
      MOTIVO="http://$n/.well-known/acme-challenge/ no sirve el reto desde este host (¿puerto 80, borde, DNS?)"
      echo "  $MOTIVO"
      return 1
    fi
  done
  return 0
}

# emitir PRUEBAS(0|1) OPCIONES... -- DOMINIO [ADICIONALES...]
# Un solo certificado para todos los nombres; el linaje se llama como el
# dominio principal.
emitir() {
  local pruebas_pedidas="$1"
  shift
  local opciones=()
  while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do
    opciones+=("$1")
    shift
  done
  shift
  local dominio="$1" n nombres=()
  for n in "$@"; do nombres+=(-d "$n"); done

  # Sin correo, cuenta sin correo: Let's Encrypt ya no lo usa para avisar.
  local contacto=(--register-unsafely-without-email)
  if [ -n "${ACME_CORREO:-}" ]; then
    contacto=(--email "$ACME_CORREO" --no-eff-email)
  fi
  local pruebas=()
  if [ "$pruebas_pedidas" = "1" ]; then pruebas=(--test-cert); fi

  echo "  pidiendo a Let's Encrypt el certificado de $* (HTTP-01)${pruebas:+ en el entorno de PRUEBAS}"
  certbot certonly --webroot -w /var/www/acme --cert-name "$dominio" "${nombres[@]}" \
    "${contacto[@]}" --agree-tos --non-interactive "${opciones[@]}" "${pruebas[@]}"
}

asegurar() {
  mkdir -p "$DIR/tls" "$DIR/letsencrypt" "$DIR/acme/.well-known/acme-challenge"
  local dominio
  dominio="$(normalizar "${DOMINIO_PUBLICO:-}")"

  if [ -z "$dominio" ]; then
    solo_http "sin DOMINIO_PUBLICO (variable del entorno dev en GitHub)"
    return 0
  fi
  if ! nombre_valido "$dominio"; then
    aviso "DOMINIO_PUBLICO='$dominio' no es un nombre de dominio valido."
    solo_http "nombre no valido"
    return 0
  fi
  if [ -n "${HSTS_SEGUNDOS:-}" ] && [ -z "$(hsts_segundos)" ]; then
    aviso "HSTS_SEGUNDOS='$HSTS_SEGUNDOS' no es un numero de segundos: se publica sin HSTS."
  fi

  local nombres=("$dominio") extra
  for extra in ${DOMINIOS_ADICIONALES:-}; do
    extra="$(normalizar "$extra")"
    if ! nombre_valido "$extra"; then
      aviso "DOMINIOS_ADICIONALES: '$extra' no es un nombre de dominio valido; se ignora."
    elif [[ " ${nombres[*]} " != *" $extra "* ]]; then
      nombres+=("$extra")
    fi
  done

  local pruebas_pedidas=0
  if [ "${ACME_PRUEBAS:-0}" = "1" ]; then pruebas_pedidas=1; fi

  # Que hay hoy y que le falta.
  local estado emisor es_de_pruebas cubiertos falta="" n
  estado="$(leer_certificado "$dominio")"
  emisor="$(campo emisor "$estado")"
  es_de_pruebas="$(campo pruebas "$estado")"
  cubiertos=" $(campo nombres "$estado") "
  for n in "${nombres[@]}"; do
    [[ "$cubiertos" == *" $n "* ]] || falta="$falta $n"
  done

  local opciones=()
  if [ -z "$emisor" ]; then
    echo "  sin certificado de $dominio: hay que emitirlo"
    opciones=(--keep-until-expiring)
  elif [ "$es_de_pruebas" = "1" ] && [ "$pruebas_pedidas" = "0" ]; then
    echo "  el certificado de $dominio es del entorno de PRUEBAS: se pide el real"
    opciones=(--force-renewal)
  elif [ -n "$falta" ]; then
    echo "  el certificado de $dominio no cubre:$falta; se amplia"
    opciones=(--renew-with-new-domains)
  fi

  if [ "${#opciones[@]}" -gt 0 ]; then
    if listo_para_emitir "${nombres[@]}"; then
      if ! emitir "$pruebas_pedidas" "${opciones[@]}" -- "${nombres[@]}"; then
        MOTIVO="Let's Encrypt no emitio el certificado (el detalle esta arriba)"
        aviso "$MOTIVO"
      fi
    fi
  else
    if [ "$es_de_pruebas" = "0" ] && [ "$pruebas_pedidas" = "1" ]; then
      echo "  ACME_PRUEBAS=1 no cambia nada: ya hay un certificado real de $dominio y no se cambia por uno de pruebas"
    fi
    echo "  certificado de $dominio presente ($(campo dias "$estado") dias de vigencia): renovar si toca"
    if ! certbot renew --webroot -w /var/www/acme --non-interactive --quiet; then
      aviso "certbot renew fallo; el certificado actual sigue valiendo hasta su fecha."
    fi
  fi

  # Lo que hay despues de emitir o renovar: solo se publica uno de confianza.
  estado="$(leer_certificado "$dominio")"
  if [ -z "$(campo emisor "$estado")" ]; then
    solo_http "${MOTIVO:-no hay certificado de $dominio}"
    return 0
  fi
  if [ "$(campo pruebas "$estado")" = "1" ]; then
    if [ "$pruebas_pedidas" = "1" ]; then
      solo_http "el certificado de $dominio es del entorno de PRUEBAS: DNS y reto funcionan; quita ACME_PRUEBAS para emitir el real"
    else
      solo_http "${MOTIVO:-el certificado de $dominio sigue siendo del entorno de PRUEBAS}"
    fi
    return 0
  fi
  if [ -n "$MOTIVO" ]; then
    aviso "Se sigue publicando el certificado vigente de $dominio: $MOTIVO."
  fi

  renderizar "$dominio" "$FRAGMENTO"
  if recargar_borde; then
    echo "  HTTPS activo en https://$dominio (certificado hasta $(campo caduca "$estado"); el 80 redirige salvo /salud-borde, ACME y /mailpit/)"
  fi
}

# Para la evidencia y el diagnostico: emisor, nombres, fechas y si esta
# publicado. Nunca la llave.
mostrar_estado() {
  local dominio estado
  dominio="$(normalizar "${DOMINIO_PUBLICO:-}")"
  if [ -z "$dominio" ] || ! nombre_valido "$dominio"; then
    echo "  sin DOMINIO_PUBLICO valido: no hay certificado que mirar"
    return 0
  fi
  estado="$(leer_certificado "$dominio")"
  if [ -z "$(campo emisor "$estado")" ]; then
    echo "  sin certificado de $dominio"
  else
    printf '%s\n' "$estado" | sed 's/^/  /'
  fi
  if [ -f "$FRAGMENTO" ]; then
    echo "  fragmento TLS: presente (el borde publica HTTPS)"
  else
    echo "  fragmento TLS: ausente (el borde esta solo en HTTP)"
  fi
}

# certbot renew --dry-run: renovacion completa contra el entorno de pruebas
# de Let's Encrypt (reto incluido), sin guardar nada ni tocar el certificado
# vigente. Es la prueba de que la renovacion automatica funcionara.
probar_renovacion() {
  local dominio
  dominio="$(normalizar "${DOMINIO_PUBLICO:-}")"
  if [ -z "$dominio" ] || ! nombre_valido "$dominio"; then
    echo "::error::Sin DOMINIO_PUBLICO valido no hay certificado que renovar."
    return 1
  fi
  if [ -z "$(campo emisor "$(leer_certificado "$dominio")")" ]; then
    echo "::error::No hay certificado de $dominio que renovar: primero tiene que emitirse."
    return 1
  fi
  echo "  renovacion simulada de $dominio (certbot renew --dry-run, entorno de pruebas; el certificado vigente no se toca)"
  if certbot renew --dry-run --cert-name "$dominio" --webroot -w /var/www/acme --non-interactive; then
    echo "  renovacion simulada: OK"
  else
    echo "::error::La renovacion simulada fallo (el detalle esta arriba)."
    return 1
  fi
}

case "${1:-asegurar}" in
  asegurar) asegurar ;;
  estado) mostrar_estado ;;
  probar-renovacion) probar_renovacion ;;
  renderizar) renderizar "${2:?dominio}" "${3:?destino}" ;;
  *) echo "uso: certificado.sh [asegurar | estado | probar-renovacion | renderizar DOMINIO DESTINO]"; exit 2 ;;
esac
