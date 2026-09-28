#!/usr/bin/env bash
# Prueba de scripts/infra/reglas-sg-contenido.sh (28-sep).
#
# El script toca el grupo de seguridad de una cuenta que no es la nuestra
# (Grupo 2): equivocarse abre un puerto interno al mundo o cierra el 22 por el
# que despliega el CD. Aqui `aws` es falso: devuelve las reglas que fija cada
# caso y apunta cada autorizacion y revocacion. No necesita red ni AWS.
#
#   bash scripts/cd/pruebas/reglas-sg-contenido.sh
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$RAIZ/scripts/infra/reglas-sg-contenido.sh"
DECLARADO="$RAIZ/infrastructure/entornos/contenido/reglas-entrada.json"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FALLOS=0
fallo() { FALLOS=$((FALLOS + 1)); echo "  FALLO: $*"; }
ok() { echo "  ok    $*"; }

mkdir -p "$TMP/bin"
cat > "$TMP/bin/aws" <<'EOF'
#!/usr/bin/env bash
# Solo entiende las tres llamadas del script.
case "$*" in
  *describe-security-group-rules*) cat "$FALSO_REGLAS" ;;
  *authorize-security-group-ingress*)
    permisos=""
    while [ $# -gt 0 ]; do
      if [ "$1" = "--ip-permissions" ]; then permisos="$2"; fi
      shift
    done
    echo "autoriza $(echo "$permisos" | jq -r '.[0] | "\(.FromPort) \(.IpRanges[0].CidrIp)"')" >> "$FALSO_LLAMADAS" ;;
  *revoke-security-group-ingress*)
    id=""
    while [ $# -gt 0 ]; do
      if [ "$1" = "--security-group-rule-ids" ]; then id="$2"; fi
      shift
    done
    echo "revoca $id" >> "$FALSO_LLAMADAS" ;;
  *) echo "aws falso: llamada no prevista: $*" >&2; exit 9 ;;
esac
EOF
chmod +x "$TMP/bin/aws"

# $1 = archivo de reglas actuales; $2 = archivo declarado; resto = VAR=valor.
correr() {
  local reglas="$1" declarado="$2"; shift 2
  : > "$TMP/llamadas"
  env -i PATH="$TMP/bin:/usr/bin:/bin" FALSO_REGLAS="$reglas" FALSO_LLAMADAS="$TMP/llamadas" "$@" \
    bash "$SCRIPT" "$declarado" > "$TMP/salida" 2>&1
}

regla() { # id desde hasta cidr
  printf '{"SecurityGroupRuleId":"%s","IsEgress":false,"IpProtocol":"tcp","FromPort":%s,"ToPort":%s,"CidrIpv4":"%s"}' "$1" "$2" "$3" "$4"
}

echo "== Hoy (28-sep): 22 y 8101-8104 abiertos al mundo =="
printf '{"SecurityGroupRules":[%s,%s,{"SecurityGroupRuleId":"sgr-salida","IsEgress":true,"IpProtocol":"-1","FromPort":-1,"ToPort":-1,"CidrIpv4":"0.0.0.0/0"}]}' \
  "$(regla sgr-ssh 22 22 0.0.0.0/0)" "$(regla sgr-mundo 8101 8104 0.0.0.0/0)" > "$TMP/hoy.json"
correr "$TMP/hoy.json" "$DECLARADO"
codigo=$?
[ "$codigo" -eq 0 ] && ok "termina bien" || fallo "codigo $codigo: $(cat "$TMP/salida")"
for p in 8101 8102 8103 8104 8105 8092 8090 8094; do
  grep -qx "autoriza $p 35.168.124.119/32" "$TMP/llamadas" && ok "autoriza $p solo desde plataforma" || fallo "no autorizo $p"
done
grep -qx "revoca sgr-mundo" "$TMP/llamadas" && ok "revoca 8101-8104 abiertos al mundo" || fallo "no revoco sgr-mundo"
grep -q "sgr-ssh" "$TMP/llamadas" && fallo "toco el 22 del CD" || ok "no toca el 22"
grep -q "sgr-salida" "$TMP/llamadas" && fallo "toco una regla de salida" || ok "no toca la salida"
primera_revocacion=$(grep -n '^revoca' "$TMP/llamadas" | head -1 | cut -d: -f1)
ultima_autorizacion=$(grep -n '^autoriza' "$TMP/llamadas" | tail -1 | cut -d: -f1)
[ "${primera_revocacion:-0}" -gt "${ultima_autorizacion:-0}" ] && ok "autoriza antes de revocar" || fallo "revoco antes de autorizar"

echo "== Ya reconciliado: no cambia nada =="
reglas=$(regla sgr-ssh 22 22 0.0.0.0/0)
for p in 8101 8102 8103 8104 8105 8092 8090 8094; do
  reglas="$reglas,$(regla "sgr-$p" "$p" "$p" 35.168.124.119/32)"
done
printf '{"SecurityGroupRules":[%s]}' "$reglas" > "$TMP/listo.json"
correr "$TMP/listo.json" "$DECLARADO"
[ ! -s "$TMP/llamadas" ] && ok "ninguna llamada de escritura" || fallo "escribio: $(cat "$TMP/llamadas")"
grep -q "Sin cambios" "$TMP/salida" && ok "lo dice" || fallo "no dijo 'Sin cambios'"

echo "== Una regla al mundo que mezcla puertos declarados y otros: no se toca =="
printf '{"SecurityGroupRules":[%s]}' "$(regla sgr-todo 0 65535 0.0.0.0/0)" > "$TMP/todo.json"
correr "$TMP/todo.json" "$DECLARADO"
grep -q "revoca" "$TMP/llamadas" && fallo "revoco una regla con puertos no declarados" || ok "no la revoca"
grep -q "no se toca" "$TMP/salida" && ok "avisa" || fallo "no aviso"

echo "== SIMULAR=1: no escribe =="
correr "$TMP/hoy.json" "$DECLARADO" SIMULAR=1
[ ! -s "$TMP/llamadas" ] && ok "ninguna llamada de escritura" || fallo "escribio en simulacion"
grep -q "cambio(s) previstos" "$TMP/salida" && ok "cuenta lo previsto" || fallo "no conto lo previsto"

echo "== Un origen abierto al mundo se rechaza =="
jq '.origen = "0.0.0.0/0"' "$DECLARADO" > "$TMP/mundo.json"
correr "$TMP/hoy.json" "$TMP/mundo.json"
codigo=$?
[ "$codigo" -ne 0 ] && ok "se niega (codigo $codigo)" || fallo "acepto 0.0.0.0/0 como origen"
[ ! -s "$TMP/llamadas" ] && ok "sin llamadas" || fallo "escribio con origen al mundo"

echo "== Un origen que no es /32 se rechaza =="
jq '.origen = "35.168.0.0/16"' "$DECLARADO" > "$TMP/red.json"
correr "$TMP/hoy.json" "$TMP/red.json"
codigo=$?
[ "$codigo" -ne 0 ] && ok "se niega (codigo $codigo)" || fallo "acepto un /16"

if [ "$FALLOS" -gt 0 ]; then
  echo "$FALLOS fallo(s)."
  exit 1
fi
echo "Todo en orden."
