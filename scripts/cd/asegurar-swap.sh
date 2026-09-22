#!/usr/bin/env bash
# R8.3 — asegura que el host tiene area de intercambio antes de desplegar.
#
# ## Por que existe
#
# `nexus-contenido-dev` es un t3.small con 1,9 GiB de RAM y **cero swap**. La
# suma de los `mem_limit` de `docker-compose.contenido.yml` es 1.760 MB; con
# los ~300 MB del sistema y dockerd, el margen declarado es de **-114 MB**.
# Ahi corren los cuatro servicios de los que depende el combate (heroes,
# inventario, productos, motor-combate).
#
# Sin swap, un pico de arranque no degrada: el OOM killer **mata un
# contenedor**. Con 2 GiB de swap el kernel pagina y el servicio sobrevive,
# mas lento. `nexus-plataforma-dev` lo tiene desde el primer dia
# (`infrastructure/entornos/plataforma/main.tf`, bloque `user_data`); el de
# contenido se creo sin el.
#
# ## Por que no basta con Terraform
#
# `user_data` **solo corre en el primer arranque**. Cambiarlo en un host que
# ya esta vivo no hace nada — y forzar su re-ejecucion significaria recrear la
# instancia, que es exactamente lo que no se puede hacer: se perderia
# `/opt/nexus` y habria que rehacer la IP elastica.
#
# Asi que el Terraform se corrige para cualquier reconstruccion futura (eso va
# aparte) y **este script** lo arregla en el host que ya existe. Corre desde
# `desplegar.sh`, o sea por la via de acceso que el CD ya usa: ni una llave
# nueva, ni un paso manual, ni nadie entrando por SSH a mano.
#
# ## Idempotente de verdad
#
# Si ya hay swap activa, no toca nada y sale. Si el fichero existe pero no
# esta activa, la activa sin recrearla. Solo crea cuando no hay nada. Puede
# correr en cada despliegue, mil veces, sin efecto acumulado.
#
# ## No es load-bearing
#
# Si algo falla —no hay sudo, el disco esta lleno, el kernel no deja— avisa y
# **devuelve 0**. Un despliegue no se cae porque no se pudo crear swap: se
# quedaria sin desplegar nada, que es peor que desplegar sin swap. El aviso
# queda en la bitacora de la corrida.
set -uo pipefail

ARCHIVO_SWAP="${ARCHIVO_SWAP:-/swapfile}"
TAMANO_SWAP="${TAMANO_SWAP:-2G}"

# En el host el script corre como `ubuntu`, que tiene sudo sin contrasena en
# las AMI de Ubuntu. Si ya somos root, `sudo` sobra (y puede no existir).
if [ "$(id -u)" -eq 0 ]; then
  COMO_ROOT=""
elif command -v sudo >/dev/null 2>&1; then
  COMO_ROOT="sudo"
else
  echo "[swap] no soy root y no hay sudo; se deja el host como esta." >&2
  exit 0
fi

# ¿Ya hay swap activa? `swapon --show` no imprime nada cuando no hay ninguna.
if [ -n "$($COMO_ROOT swapon --show=NAME --noheadings 2>/dev/null || true)" ]; then
  echo "[swap] ya activa, no se toca nada:"
  $COMO_ROOT swapon --show || true
  exit 0
fi

crear_swap() {
  # fallocate es instantaneo; dd es el respaldo para sistemas de archivos que
  # no lo soportan (no es el caso de ext4, pero cuesta dos lineas).
  $COMO_ROOT fallocate -l "$TAMANO_SWAP" "$ARCHIVO_SWAP" 2>/dev/null \
    || $COMO_ROOT dd if=/dev/zero of="$ARCHIVO_SWAP" bs=1M count=2048 status=none
  # 600: el fichero de swap contiene memoria del sistema. Con otros permisos
  # el propio `swapon` avisa de que es inseguro.
  $COMO_ROOT chmod 600 "$ARCHIVO_SWAP"
  $COMO_ROOT mkswap "$ARCHIVO_SWAP" >/dev/null
}

if [ -f "$ARCHIVO_SWAP" ]; then
  echo "[swap] $ARCHIVO_SWAP existe pero no esta activa; se activa sin recrearla."
else
  echo "[swap] no hay area de intercambio; se crea $ARCHIVO_SWAP de $TAMANO_SWAP."
  if ! crear_swap; then
    echo "[swap] no se pudo crear el fichero; el despliegue continua sin swap." >&2
    $COMO_ROOT rm -f "$ARCHIVO_SWAP" 2>/dev/null || true
    exit 0
  fi
fi

if ! $COMO_ROOT swapon "$ARCHIVO_SWAP" 2>/dev/null; then
  echo "[swap] swapon fallo; el despliegue continua sin swap." >&2
  exit 0
fi

# Persistencia: sin la linea en fstab, el swap desaparece al reiniciar — y
# este host se apaga y se enciende (o se apagara, cuando R9.3 le ponga el
# mismo horario que a plataforma).
if ! $COMO_ROOT grep -qs "^${ARCHIVO_SWAP} " /etc/fstab; then
  echo "${ARCHIVO_SWAP} none swap sw 0 0" | $COMO_ROOT tee -a /etc/fstab >/dev/null
  echo "[swap] persistida en /etc/fstab."
fi

echo "[swap] activa:"
$COMO_ROOT swapon --show || true
free -m || true
