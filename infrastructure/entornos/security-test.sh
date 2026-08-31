#!/bin/bash
if [ -z "$1" ]; then
  echo "Uso: ./security-test.sh <IP_DEL_SERVIDOR>"
  exit 1
fi

IP_PRODUCCION=$1
echo "=== Iniciando escaneo perimetral en $IP_PRODUCCION ==="
nmap -Pn -p 22,80 $IP_PRODUCCION

echo "=== Validando handshake TCP en puerto 22 ==="
ssh -v -T -o ConnectTimeout=5 deployer@$IP_PRODUCCION
