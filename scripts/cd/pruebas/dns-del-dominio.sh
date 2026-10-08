#!/usr/bin/env bash
# Prueba de scripts/cd/dns-del-dominio.sh (el paso "DNS publico del dominio"
# de certificado-dev.yml), 7-oct.
#
# El 7-oct, al anadir www (CNAME del dominio) a DOMINIOS_ADICIONALES, el paso
# murio sin decir nada a mitad del segundo nombre y la renovacion ni empezo:
# el destino del CNAME se tomaba por servidor de nombres y una consulta sin
# respuesta, con bash -e y pipefail, cortaba el script. Lo que se fija aqui,
# con un dig falso (la zona nexus.test, www como CNAME, dos NS):
#   - los dos nombres se recorren enteros y el paso termina en 0, pase lo que
#     pase con el DNS (solo avisa);
#   - los servidores autoritativos son los NS de la zona, nunca el destino
#     del CNAME (nadie pregunta a @nexus.test);
#   - un servidor mudo, otra IP, un AAAA o un CAA ajeno se avisan; un CAA de
#     Let's Encrypt no; el CNAME no produce avisos falsos de AAAA ni de CAA;
#   - tambien con bash -e.
#
# En local, sin red: "bash scripts/cd/pruebas/dns-del-dominio.sh".
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$AQUI/../dns-del-dominio.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FALLOS=0
bien() { echo "  ok    $1"; }
mal() { echo "  FALLO $1"; FALLOS=$((FALLOS + 1)); }
contiene() { # contiene <descripcion> <texto> <patron>
  if printf '%s\n' "$2" | grep -qE -- "$3"; then bien "$1"; else mal "$1 (no aparece '$3')"; printf '%s\n' "$2" | sed 's/^/        /'; fi
}
no_contiene() {
  if printf '%s\n' "$2" | grep -qE -- "$3"; then mal "$1 (aparece '$3')"; printf '%s\n' "$2" | sed 's/^/        /'; else bien "$1"; fi
}
cuenta() { # cuenta <descripcion> <texto> <patron> <n>
  local n
  n="$(printf '%s\n' "$2" | grep -cE -- "$3")"
  if [ "$n" = "$4" ]; then bien "$1"; else mal "$1 ($n en vez de $4)"; printf '%s\n' "$2" | sed 's/^/        /'; fi
}

# dig falso: la zona nexus.test (A = FALSO_A, NS ns1/ns2.proveedor.test) y
# www.nexus.test como CNAME del dominio. Contesta en +short o en +answer como
# el de verdad: para un CNAME, primero el CNAME y luego lo del destino. Los
# servidores de FALSO_MUDOS, la IP del host y el propio dominio no contestan
# (dig sale con 9). Apunta cada consulta en $TMP/consultas.
mkdir -p "$TMP/bin"
cat > "$TMP/bin/dig" <<'FALSO'
#!/usr/bin/env bash
tipo=A; nombre=""; servidor=""; corto=0
for a in "$@"; do
  case "$a" in
    @*) servidor="${a#@}" ;;
    +short) corto=1 ;;
    +*) ;;
    A|NS|AAAA|CAA|CNAME) tipo="$a" ;;
    *) nombre="${a%.}" ;;
  esac
done
echo "$tipo $nombre @${servidor:-defecto}" >> "$CONSULTAS"
if [ -n "$servidor" ] && { [ "$servidor" = "${FALSO_HOST:-}" ] || [ "$servidor" = nexus.test ] \
     || [[ " ${FALSO_MUDOS:-} " == *" $servidor "* ]]; }; then
  echo ";; communications error to $servidor#53: timed out"
  echo ";; no servers could be reached"
  exit 9
fi
linea() { # linea <dueno> <tipo> <dato>
  if [ "$corto" = 1 ]; then echo "$3"; else printf '%s.\t300\tIN\t%s\t%s\n' "$1" "$2" "$3"; fi
}
objetivo=""
case "$nombre" in
  www.nexus.test) linea www.nexus.test CNAME nexus.test.; objetivo=nexus.test ;;
  nexus.test) objetivo=nexus.test ;;
esac
[ "$tipo" = CNAME ] && exit 0
case "$objetivo:$tipo" in
  nexus.test:A) linea nexus.test A "${FALSO_A:-203.0.113.10}" ;;
  nexus.test:NS) linea nexus.test NS ns1.proveedor.test.; linea nexus.test NS ns2.proveedor.test. ;;
  nexus.test:AAAA) [ -n "${FALSO_AAAA:-}" ] && linea nexus.test AAAA "$FALSO_AAAA" ;;
  nexus.test:CAA) [ -n "${FALSO_CAA:-}" ] && linea nexus.test CAA "$FALSO_CAA" ;;
esac
exit 0
FALSO
chmod +x "$TMP/bin/dig"

# correr [VAR=valor...] -- <opciones de bash>: el script con el dig falso.
correr() {
  local entorno=() opciones=()
  while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do entorno+=("$1"); shift; done
  [ "$#" -gt 0 ] && shift
  opciones=("$@")
  : > "$TMP/consultas"
  env PATH="$TMP/bin:$PATH" CONSULTAS="$TMP/consultas" FALSO_HOST=203.0.113.10 \
    DOMINIO=nexus.test ADICIONALES=www.nexus.test DESTINO=203.0.113.10 "${entorno[@]}" \
    bash "${opciones[@]}" "$SCRIPT" 2>&1
  echo "salida=$?"
}

echo "== dominio y www (CNAME), todo bien =="
r="$(correr)"
contiene "termina en 0" "$r" '^salida=0$'
contiene "recorre el dominio" "$r" '^== nexus\.test$'
contiene "recorre tambien www" "$r" '^== www\.nexus\.test$'
contiene "muestra el CNAME de www" "$r" 'CNAME -> nexus\.test$'
cuenta "los 5 servidores de cada nombre llevan al host (3 publicos + 2 NS)" "$r" '-> el host de plataforma$' 10
contiene "los NS de la zona cuentan como autoritativos" "$r" '^  ns2\.proveedor\.test +-> el host de plataforma$'
no_contiene "ningun aviso" "$r" '::warning::'
contiene "lo dice al final" "$r" 'El DNS publico lleva todos los nombres al host de plataforma'
c="$(cat "$TMP/consultas")"
no_contiene "nunca toma el destino del CNAME por servidor de nombres" "$c" '@nexus\.test$'
no_contiene "nunca pregunta al host" "$c" '@203\.0\.113\.10$'

echo "== lo mismo con bash -e (como un run de Actions) =="
r="$(correr -- -e)"
contiene "termina en 0" "$r" '^salida=0$'
cuenta "recorre los dos nombres enteros" "$r" '-> el host de plataforma$' 10

echo "== un servidor autoritativo que no contesta, con bash -e =="
r="$(correr FALSO_MUDOS=ns2.proveedor.test -- -e)"
contiene "termina en 0" "$r" '^salida=0$'
cuenta "el mudo sale como (no resuelve) en los dos nombres" "$r" '^  ns2\.proveedor\.test +-> \(no resuelve\)$' 2
contiene "y sigue con www" "$r" '^== www\.nexus\.test$'
contiene "avisa al final" "$r" '::warning::Algun resolutor no lleva el dominio'

echo "== el DNS lleva a otra IP =="
r="$(correr FALSO_A=198.51.100.7)"
contiene "termina en 0" "$r" '^salida=0$'
contiene "dice a donde lleva" "$r" '^  1\.1\.1\.1 +-> 198\.51\.100\.7$'
contiene "avisa" "$r" '::warning::Algun resolutor no lleva el dominio'

echo "== AAAA y CAA =="
r="$(correr FALSO_AAAA=2001:db8::1)"
contiene "avisa del AAAA del dominio" "$r" '::warning::nexus\.test tiene AAAA \(2001:db8::1\)'
r="$(correr FALSO_CAA='0 issue "digicert.com"')"
contiene "avisa de un CAA que no autoriza a Let's Encrypt" "$r" "::warning::El CAA de nexus\.test no autoriza a letsencrypt\.org"
r="$(correr FALSO_CAA='0 issue "letsencrypt.org"')"
contiene "muestra el CAA" "$r" 'CAA de nexus\.test: 0 issue "letsencrypt\.org"'
no_contiene "un CAA de Let's Encrypt no es un aviso" "$r" '::warning::'

echo "== sin dominio =="
r="$(correr DOMINIO= ADICIONALES=)"
contiene "no hace nada y termina en 0" "$r" '^salida=0$'
contiene "lo dice" "$r" 'Sin DOMINIO'

echo "== un adicional repetido o con esquema =="
r="$(correr ADICIONALES='https://WWW.nexus.test/ www.nexus.test nexus.test')"
cuenta "cada nombre una sola vez" "$r" '^== ' 2

echo
if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS comprobacion(es) fallaron."
  exit 1
fi
echo "Todo en orden."
