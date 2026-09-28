#!/usr/bin/env bash
#
# Reglas de entrada del grupo de seguridad del host de contenido (28-sep).
#
# ## Por que existe
#
# El host de contenido vive en la cuenta del Grupo 2 (551262695144) y su grupo
# de seguridad no lo gobierna OpenTofu desde aqui (ver
# infrastructure/entornos/contenido/README.md, «ANTES DE NADA»). Hasta hoy,
# cada puerto nuevo era una peticion a una persona con consola en esa cuenta,
# y el cierre de 8101-8104 al mundo lleva pendiente desde B12. Con el rol OIDC
# de esa cuenta (vars.AWS_ROLE_ARN_CONTENIDO, permisos solo sobre ESTE grupo),
# infra-dev.yml aplica lo declarado en reglas-entrada.json sin nadie en medio.
#
# ## Que hace, y que no
#
# Solo sobre los puertos DECLARADOS:
#   1) si al puerto le falta la regla desde el origen declarado, la autoriza;
#   2) si una regla abierta al mundo (0.0.0.0/0 o ::/0) cubre SOLO puertos
#      declarados, la revoca -- despues de autorizar, para no cortar nada.
# Nunca toca un puerto que no este declarado (el 22 del CD, por ejemplo), ni
# una regla que mezcle puertos declarados con otros, ni las de salida. Se
# niega a declarar un origen abierto al mundo.
#
# Con SIMULAR=1 solo dice lo que haria.
#
#   scripts/infra/reglas-sg-contenido.sh infrastructure/entornos/contenido/reglas-entrada.json
set -euo pipefail

ARCHIVO="${1:?uso: reglas-sg-contenido.sh <reglas-entrada.json>}"
SIMULAR="${SIMULAR:-0}"

command -v jq >/dev/null 2>&1 || { echo "::error::Falta jq."; exit 1; }
command -v aws >/dev/null 2>&1 || { echo "::error::Falta la CLI de AWS."; exit 1; }

GRUPO=$(jq -r '.grupo // empty' "$ARCHIVO")
REGION=$(jq -r '.region // empty' "$ARCHIVO")
ORIGEN=$(jq -r '.origen // empty' "$ARCHIVO")
if [ -z "$GRUPO" ] || [ -z "$REGION" ] || [ -z "$ORIGEN" ]; then
  echo "::error::$ARCHIVO debe declarar grupo, region y origen."
  exit 1
fi
case "$ORIGEN" in
  0.0.0.0/0|::/0)
    echo "::error::El origen declarado es el mundo entero ($ORIGEN). Estos puertos son internos: solo el host de plataforma."
    exit 1 ;;
  */32) ;;
  *)
    echo "::error::El origen declarado ($ORIGEN) no es una sola direccion (/32). Solo el host de plataforma llama a estos puertos."
    exit 1 ;;
esac

mapfile -t PUERTOS < <(jq -r '.puertos[].puerto' "$ARCHIVO")
if [ "${#PUERTOS[@]}" -eq 0 ]; then
  echo "::error::$ARCHIVO no declara ningun puerto."
  exit 1
fi

# AWS solo admite en la descripcion de una regla letras sin tilde, numeros,
# espacios y ._-:/()#,@[]+=&;{}!$* (hasta 255). Un apostrofo o un "<" hacen
# fallar la llamada a medias; se comprueba antes de tocar nada.
while IFS= read -r descripcion; do
  if [ "${#descripcion}" -gt 255 ] || ! printf '%s' "$descripcion" | LC_ALL=C grep -qE '^[0-9A-Za-z_ .:/()#,@+=&;{}!$*[-]*$'; then
    echo "::error::Descripcion no admitida por AWS: '$descripcion'. Solo letras sin tilde, numeros, espacios y ._-:/()#,@[]+=&;{}!\$*."
    exit 1
  fi
done < <(jq -r '.puertos[].descripcion' "$ARCHIVO")

declarado() {
  local p="$1" d
  for d in "${PUERTOS[@]}"; do
    [ "$d" = "$p" ] && return 0
  done
  return 1
}

leer_reglas() {
  aws ec2 describe-security-group-rules --region "$REGION" \
    --filters "Name=group-id,Values=$GRUPO" --output json
}

tabla() {
  echo "$1" | jq -r '.SecurityGroupRules[] | select(.IsEgress == false)
    | "    \(.SecurityGroupRuleId)  \(.IpProtocol)  \(.FromPort)-\(.ToPort)  \(.CidrIpv4 // .CidrIpv6 // .ReferencedGroupInfo.GroupId // "-")"' | sort -k3
}

REGLAS=$(leer_reglas)
echo "Grupo $GRUPO ($REGION), origen declarado $ORIGEN. Reglas de entrada antes:"
tabla "$REGLAS"

cambios=0

# 1) Autorizar lo que falte. Se considera cubierto si ya hay una regla tcp
#    desde el origen declarado cuyo rango incluye el puerto.
while IFS=$'\t' read -r puerto descripcion; do
  cubierto=$(echo "$REGLAS" | jq --argjson p "$puerto" --arg o "$ORIGEN" \
    '[.SecurityGroupRules[] | select(.IsEgress == false and (.IpProtocol == "tcp" or .IpProtocol == "-1")
      and .FromPort <= $p and .ToPort >= $p and .CidrIpv4 == $o)] | length')
  if [ "$cubierto" -gt 0 ]; then
    echo "  $puerto: ya admite $ORIGEN"
    continue
  fi
  echo "  $puerto: falta -> se autoriza desde $ORIGEN ($descripcion)"
  cambios=$((cambios + 1))
  if [ "$SIMULAR" != "1" ]; then
    aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$GRUPO" \
      --ip-permissions "$(jq -cn --argjson p "$puerto" --arg o "$ORIGEN" --arg d "$descripcion" \
        '[{IpProtocol:"tcp", FromPort:$p, ToPort:$p, IpRanges:[{CidrIp:$o, Description:$d}]}]')" >/dev/null
  fi
done < <(jq -r '.puertos[] | [.puerto, .descripcion] | @tsv' "$ARCHIVO")

# 2) Revocar lo abierto al mundo, solo si TODO su rango esta declarado.
while IFS=$'\t' read -r id desde hasta cidr; do
  todos=1
  for ((p = desde; p <= hasta; p++)); do
    if ! declarado "$p"; then todos=0; break; fi
    # Un rango enorme no se recorre entero: con un puerto no declarado basta.
  done
  if [ "$todos" -eq 1 ]; then
    echo "  $id ($desde-$hasta desde $cidr): abierta al mundo y solo con puertos declarados -> se revoca"
    cambios=$((cambios + 1))
    if [ "$SIMULAR" != "1" ]; then
      aws ec2 revoke-security-group-ingress --region "$REGION" --group-id "$GRUPO" \
        --security-group-rule-ids "$id" >/dev/null
    fi
  else
    echo "  $id ($desde-$hasta desde $cidr): abierta al mundo pero incluye puertos no declarados -> no se toca"
  fi
done < <(echo "$REGLAS" | jq -r '.SecurityGroupRules[]
  | select(.IsEgress == false and .IpProtocol == "tcp" and (.CidrIpv4 == "0.0.0.0/0" or .CidrIpv6 == "::/0"))
  | [.SecurityGroupRuleId, .FromPort, .ToPort, (.CidrIpv4 // .CidrIpv6)] | @tsv')

if [ "$SIMULAR" = "1" ]; then
  echo "SIMULAR=1: $cambios cambio(s) previstos, ninguno aplicado."
  exit 0
fi

if [ "$cambios" -eq 0 ]; then
  echo "Sin cambios: el grupo ya coincide con $ARCHIVO."
  exit 0
fi

echo "Reglas de entrada despues ($cambios cambio(s)):"
tabla "$(leer_reglas)"
