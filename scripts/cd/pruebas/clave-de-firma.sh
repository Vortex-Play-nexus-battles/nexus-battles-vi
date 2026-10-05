#!/usr/bin/env bash
# Prueba de la clave de firma estable de ms-identidad (funcion
# asegurar_clave_de_firma de scripts/cd/desplegar.sh).
#
# Nacio del smoke de dev del 24-sep: cada despliegue de ms-identidad generaba
# una clave nueva, cerraba todas las sesiones y dejaba a salas-partidas con
# una credencial de servicio que inventario rechazaba con 401 durante 15 min.
# Lo que aqui se fija: la clave se genera una vez y se reutiliza, un secret de
# GitHub manda, es una RSA PKCS#8 que ClavesDeFirma sabe leer, nunca se
# imprime y nunca va al .env compartido.
#
# Se corre en local, sin Docker: "bash scripts/cd/pruebas/clave-de-firma.sh".
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/../desplegar.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Cargar solo las funciones del script, sin ejecutar el despliegue.
DESPLEGAR_SOLO_FUNCIONES=1 source "$SCRIPT"

FALLOS=0
bien() { echo "  ok    $1"; }
mal() { echo "  FALLO $1"; FALLOS=$((FALLOS + 1)); }
comprobar() { # comprobar <descripcion> <comando...>
  local descripcion="$1"
  shift
  if "$@"; then bien "$descripcion"; else mal "$descripcion"; fi
}
no_contiene() { ! grep -qF -- "$2" "$1"; }

ARCHIVO="$TMP/secretos-firma.env"

echo "1) Primer despliegue, sin secret ni clave guardada"
unset JWT_CLAVE_PRIVADA
asegurar_clave_de_firma "$ARCHIVO" >"$TMP/salida-1.txt"
PRIMERA="${JWT_CLAVE_PRIVADA:-}"
comprobar "deja la clave exportada para Compose" test -n "$PRIMERA"
printf '%s' "$PRIMERA" | base64 -d >"$TMP/clave.der"
comprobar "es una RSA de 2048 bits" \
  sh -c "openssl pkey -inform DER -in '$TMP/clave.der' -noout -text 2>/dev/null | head -n1 | grep -q '2048 bit'"
# PKCS#8 lleva el identificador del algoritmo; PKCS#1 no. ClavesDeFirma usa
# PKCS8EncodedKeySpec, asi que una PKCS#1 tumbaria a ms-identidad al arrancar.
comprobar "va en PKCS#8, que es lo que lee ClavesDeFirma" \
  sh -c "openssl asn1parse -inform DER -in '$TMP/clave.der' 2>/dev/null | grep -q rsaEncryption"
# Y la prueba de verdad: la misma llamada que hace ClavesDeFirma. Una clave
# que openssl acepta pero Java no, tumba a ms-identidad al arrancar.
if command -v java >/dev/null 2>&1; then
  cat >"$TMP/LeerClave.java" <<'JAVA'
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class LeerClave {
    public static void main(String[] args) throws Exception {
        byte[] der = Base64.getDecoder().decode(new String(System.in.readAllBytes()).trim());
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
JAVA
  comprobar "Java la lee como la lee ClavesDeFirma" \
    sh -c "printf '%s' \"\$1\" | java '$TMP/LeerClave.java' >/dev/null 2>&1" _ "$PRIMERA"
else
  echo "  (sin java en esta maquina: se omite la lectura con PKCS8EncodedKeySpec)"
fi
comprobar "queda guardada en el host" grep -qxF "JWT_CLAVE_PRIVADA=$PRIMERA" "$ARCHIVO"
comprobar "el archivo solo lo lee su dueno (600)" test "$(stat -c %a "$ARCHIVO")" = "600"
comprobar "la bitacora no la imprime" no_contiene "$TMP/salida-1.txt" "${PRIMERA:0:40}"
comprobar "la bitacora dice que se genero" grep -q "generada" "$TMP/salida-1.txt"

echo "2) Siguiente despliegue: la misma clave, no una nueva"
unset JWT_CLAVE_PRIVADA
asegurar_clave_de_firma "$ARCHIVO" >"$TMP/salida-2.txt"
comprobar "reutiliza la guardada" test "${JWT_CLAVE_PRIVADA:-}" = "$PRIMERA"
comprobar "no genera otra" no_contiene "$TMP/salida-2.txt" "generada"
comprobar "el archivo sigue con una sola linea" test "$(wc -l <"$ARCHIVO")" -eq 1

echo "3) Con un secret de GitHub: manda el secret, y el archivo se alinea"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null \
  | openssl pkcs8 -topk8 -nocrypt -outform DER 2>/dev/null | base64 -w0 >"$TMP/secret.txt"
SECRET="$(cat "$TMP/secret.txt")"
export JWT_CLAVE_PRIVADA="$SECRET"
asegurar_clave_de_firma "$ARCHIVO" >"$TMP/salida-3.txt"
comprobar "usa la del secret" test "${JWT_CLAVE_PRIVADA:-}" = "$SECRET"
comprobar "revertir.sh encontrara la misma" grep -qxF "JWT_CLAVE_PRIVADA=$SECRET" "$ARCHIVO"
comprobar "la bitacora no la imprime" no_contiene "$TMP/salida-3.txt" "${SECRET:0:40}"

echo "4) Sin openssl y sin nada guardado: avisa y no rompe el despliegue"
SIN_OPENSSL="$TMP/bin"
mkdir -p "$SIN_OPENSSL"
for herramienta in touch chmod grep head cut; do
  ln -s "$(command -v "$herramienta")" "$SIN_OPENSSL/$herramienta"
done
(
  PATH="$SIN_OPENSSL"
  unset JWT_CLAVE_PRIVADA
  asegurar_clave_de_firma "$TMP/otro.env" >"$TMP/salida-4.txt"
  printf '%s' "${JWT_CLAVE_PRIVADA:-<vacia>}" >"$TMP/resultado-4.txt"
)
comprobar "termina bien" test -f "$TMP/resultado-4.txt"
comprobar "no exporta nada" test "$(cat "$TMP/resultado-4.txt")" = "<vacia>"
comprobar "lo dice en la bitacora" grep -q "AVISO" "$TMP/salida-4.txt"

echo "5) La clave nunca se escribe en el .env que comparten los servicios"
# El .env lo cargan con env_file los ocho servicios de plataforma: con la
# clave privada dentro, cualquiera de ellos podria fabricar tokens.
comprobar "no esta en la lista de variables opcionales del .env" \
  sh -c "! sed -n '/^for variable in SMTP_PORT/,/; do\$/p' '$SCRIPT' | grep -q JWT_CLAVE_PRIVADA"
comprobar "no esta en el bloque fijo del .env" \
  sh -c "! sed -n '/^cat > .env <<EOF/,/^EOF\$/p' '$SCRIPT' | grep -q JWT_CLAVE_PRIVADA"
comprobar "ms-identidad la recibe por su environment" \
  grep -q 'JWT_CLAVE_PRIVADA: \${JWT_CLAVE_PRIVADA:-}' "$AQUI/../../../docker-compose.cuentas.yml"

echo
if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS comprobacion(es) fallaron"
  exit 1
fi
echo "Todas las comprobaciones pasaron"
