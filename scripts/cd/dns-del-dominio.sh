#!/usr/bin/env bash
# DNS publico del dominio, visto desde fuera de AWS (certificado-dev.yml;
# 6-oct, sacado a script el 7-oct).
#
# Para cada nombre (DOMINIO y ADICIONALES) dice a donde lo llevan tres
# resolutores publicos y los servidores autoritativos de su zona, y avisa de
# lo que haria fallar la emision: un AAAA (el host no tiene IPv6) o un CAA
# que no autorice a Let's Encrypt. SOLO AVISA: termina siempre en 0. Quien
# decide si se emite es certificado.sh, mirando desde el propio host.
#
# 7-oct — con www como CNAME del dominio, el paso abortaba sin decir nada y,
# con el, los de despues: la renovacion diaria no llegaba a correr.
#   - "dig NS www..." contesta con el CNAME y los NS del destino, y el
#     destino (el propio dominio) acababa en la lista de servidores de
#     nombres: una consulta a la IP del host, que no contesta DNS.
#   - Con bash -e y pipefail (los run de Actions), una consulta sin
#     respuesta, o sin ningun A, cortaba el script entero.
#   - Para un CNAME, "dig +short AAAA/CAA" devuelve el nombre de destino:
#     avisos falsos de AAAA y de CAA.
# Ahora solo cuentan los NS cuyo dueno es la zona, una consulta sin
# respuesta es "(no resuelve)" y AAAA y CAA solo leen registros de ese tipo.
#
#   DOMINIO=nexusbattlesvi.com ADICIONALES=www.nexusbattlesvi.com \
#   DESTINO=<IP publica del host> bash scripts/cd/dns-del-dominio.sh
set -uo pipefail

normalizar() { printf '%s' "$1" | tr 'A-Z' 'a-z' | sed 's|^https\?://||; s|/.*$||'; }

D="$(normalizar "${DOMINIO:-}")"
DESTINO="${DESTINO:-}"
if [ -z "$D" ]; then
  echo "Sin DOMINIO: nada que mirar."
  exit 0
fi

# autoritativos <nombre>: los NS del primer antepasado (o el mismo) que los
# tenga como DUENO. El CNAME y los NS de su destino no cuentan.
autoritativos() {
  local zona="$1" ns
  while [ -n "$zona" ] && [ "$zona" != "${zona#*.}" ]; do
    ns="$(dig +noall +answer +time=3 +tries=2 NS "$zona" 2>/dev/null \
      | awk -v z="$zona." 'tolower($1) == z && $4 == "NS" { sub(/\.$/, "", $5); print $5 }' \
      | head -3)" || true
    if [ -n "$ns" ]; then
      printf '%s\n' "$ns"
      return 0
    fi
    zona="${zona#*.}"
  done
}

# ipv4 <nombre> <servidor>: las IPv4 que da ese servidor, en una linea.
ipv4() {
  local ips
  ips="$(dig +short +time=3 +tries=2 A "$1" @"$2" 2>/dev/null \
    | grep -E '^[0-9]+(\.[0-9]+){3}$' | sort -u | tr '\n' ' ')" || true
  printf '%s' "${ips% }"
}

# Los mismos nombres que pedira certificado.sh: el dominio y, separados por
# espacios, los adicionales.
nombres="$D"
for extra in ${ADICIONALES:-}; do
  extra="$(normalizar "$extra")"
  if [ -n "$extra" ] && [[ " $nombres " != *" $extra "* ]]; then
    nombres="$nombres $extra"
  fi
done

bien=1
for nombre in $nombres; do
  echo "== $nombre"
  cname="$(dig +short +time=3 +tries=2 CNAME "$nombre" @1.1.1.1 2>/dev/null | head -1)" || true
  [ -n "$cname" ] && echo "  CNAME -> ${cname%.}"
  for r in 1.1.1.1 8.8.8.8 9.9.9.9 $(autoritativos "$nombre"); do
    ips="$(ipv4 "$nombre" "$r")"
    if [ -n "$DESTINO" ] && [ "$ips" = "$DESTINO" ]; then
      printf '  %-28s -> el host de plataforma\n' "$r"
    else
      printf '  %-28s -> %s\n' "$r" "${ips:-(no resuelve)}"
      bien=0
    fi
  done
  aaaa="$(dig +short +time=3 +tries=2 AAAA "$nombre" @1.1.1.1 2>/dev/null \
    | grep -E '^[0-9a-fA-F:]+$' | grep ':' | head -3 | tr '\n' ' ')" || true
  if [ -n "$aaaa" ]; then
    echo "::warning::$nombre tiene AAAA (${aaaa% }) y el host no tiene IPv6: los clientes IPv6 no llegarian."
  fi
  # CAA: manda el del antepasado mas cercano que tenga (para un CNAME, el
  # resolutor ya devuelve el del destino).
  zona="$nombre"
  while [ -n "$zona" ] && [ "$zona" != "${zona#*.}" ]; do
    caa="$(dig +short +time=3 +tries=2 CAA "$zona" @1.1.1.1 2>/dev/null \
      | grep -E '^[0-9]+ [A-Za-z]+ ')" || true
    if [ -n "$caa" ]; then
      echo "  CAA de $zona: $(printf '%s' "$caa" | tr '\n' ' ')"
      if ! printf '%s\n' "$caa" | grep -qi 'issue "letsencrypt.org'; then
        echo "::warning::El CAA de $zona no autoriza a letsencrypt.org: Let's Encrypt se negara a emitir."
      fi
      break
    fi
    zona="${zona#*.}"
  done
done

if [ "$bien" = 1 ]; then
  echo "El DNS publico lleva todos los nombres al host de plataforma."
else
  echo "::warning::Algun resolutor no lleva el dominio al host de plataforma (propagacion, registro A equivocado o un servidor que no contesta). certificado.sh no emitira hasta que el host lo vea apuntando a si mismo."
fi
exit 0
