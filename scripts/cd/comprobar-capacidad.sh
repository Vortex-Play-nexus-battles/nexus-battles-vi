#!/usr/bin/env bash
#
# Compuerta de capacidad del host de dev — R16.6
#
# ## Por que existe
#
# El 24-sep se desplego ms-ecommerce sobre un host que ya sostenia
# ms-finanzas. Ningun contenedor murio y ningun healthcheck fallo DURANTE el
# despliegue, asi que el CD lo dio por bueno. Lo que paso despues:
#
#   swap        1995 -> 2047 de 2047 MiB (lleno)
#   disponible   318 -> 67 MiB
#   ms-finanzas  al 168 % de CPU, 502 en sus tres rutas
#
# y unos minutos mas tarde ni el borde respondia. Hubo que reiniciar la EC2.
#
# El healthcheck de desplegar.sh mira si el servicio que acaba de subir
# contesta. No mira si al subirlo ha empujado a otro fuera de la RAM. Esta
# compuerta mira justo eso, antes y despues.
#
# ## De donde salen los umbrales (medidos, no elegidos)
#
# Mediciones del 24-sep en nexus-plataforma-dev, 1910 MiB de RAM y 2047 de swap:
#
#   estado                                     disponible   swap libre   resultado
#   ---------------------------------------------------------------------------
#   9 MVP + identidad                             312 MiB      493 MiB   sano
#   + ms-finanzas                                 318 MiB       52 MiB   sano
#   + finanzas + cumplimiento + ecommerce (12)    187 MiB      106 MiB   sano, al limite
#   + ecommerce sobre finanzas, en caliente        67 MiB        0 MiB   THRASHING
#
# El salto entre "al limite" y "thrashing" esta entre 106 y 0 MiB de swap
# libre, y entre 187 y 67 MiB de disponible. Los umbrales se ponen del lado
# seguro de ese salto, no en el borde:
#
#   ANTES   no empezar si disponible < 150 MiB o swap libre < 150 MiB
#   DESPUES revertir si disponible <  80 MiB o swap libre <  50 MiB
#
# El umbral de 80 MiB de DESPUES es el que ya documentaba CAPACIDAD.md; lo
# nuevo es mirarlo tambien ANTES, y mirar el swap, que es el que se agoto
# primero las dos veces.
set -euo pipefail

MODO="${1:-antes}"

MIN_DISP_ANTES="${MIN_DISP_ANTES:-150}"
MIN_SWAP_ANTES="${MIN_SWAP_ANTES:-150}"
MIN_DISP_DESPUES="${MIN_DISP_DESPUES:-80}"
MIN_SWAP_DESPUES="${MIN_SWAP_DESPUES:-50}"

disponible=$(free -m | awk '/^Mem:/{print $7}')
swap_total=$(free -m | awk '/^Swap:/{print $2}')
swap_usado=$(free -m | awk '/^Swap:/{print $3}')
swap_libre=$(( swap_total - swap_usado ))

printf '  RAM disponible: %s MiB\n  swap libre:     %s MiB (de %s)\n' \
  "$disponible" "$swap_libre" "$swap_total"

# Un host sin swap no es un host sin margen: es otro tipo de host. Los de dev
# siempre lo tienen (asegurar-swap.sh), pero si algun dia no lo tuviera, el
# criterio pasa a ser solo la RAM en vez de fallar siempre por un swap de 0.
if [ "$swap_total" -eq 0 ]; then
  echo "  (sin swap configurado: se decide solo por la RAM disponible)"
  MIN_SWAP_ANTES=0
  MIN_SWAP_DESPUES=0
fi

case "$MODO" in
  antes)
    if [ "$disponible" -lt "$MIN_DISP_ANTES" ] || [ "$swap_libre" -lt "$MIN_SWAP_ANTES" ]; then
      echo "::error::El host no tiene sitio para un servicio mas: hacen falta ${MIN_DISP_ANTES} MiB disponibles y ${MIN_SWAP_ANTES} MiB de swap libre. Apaga algo primero (entrada 'apagar' de cd.yml) o reparte carga al otro host."
      echo "No se despliega. Esto NO es un fallo del servicio: es que no cabe."
      exit 1
    fi
    echo "  Hay sitio. Continua el despliegue."
    ;;

  despues)
    fallo=0
    if [ "$disponible" -lt "$MIN_DISP_DESPUES" ] || [ "$swap_libre" -lt "$MIN_SWAP_DESPUES" ]; then
      echo "::error::Despues del despliegue el host quedo por debajo del minimo (${MIN_DISP_DESPUES} MiB disponibles / ${MIN_SWAP_DESPUES} MiB de swap)."
      fallo=1
    fi

    # Lo que de verdad importa: que lo que sostiene el MVP siga contestando.
    # Si uno de estos cae, da igual que el servicio nuevo este sano.
    echo "  Nucleo del MVP:"
    for par in "borde:80:/salud-borde" "ms-identidad:8089:/actuator/health" \
               "salas-partidas:8084:/actuator/health" "comentarios:8081:/actuator/health"; do
      nombre=${par%%:*}; resto=${par#*:}; puerto=${resto%%:*}; ruta=${resto#*:}
      codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://localhost:${puerto}${ruta}" || echo "---")
      printf '    %-18s %s\n' "$nombre" "$codigo"
      case "$codigo" in 200) ;; *) echo "::error::$nombre no contesta tras el despliegue."; fallo=1 ;; esac
    done

    if [ "$fallo" -ne 0 ]; then
      echo "::error::Revertir el servicio recien desplegado, no el MVP."
      exit 1
    fi
    echo "  El MVP sigue en pie y queda margen."
    ;;

  *)
    echo "::error::Modo no reconocido: '$MODO'. Usa 'antes' o 'despues'."
    exit 1
    ;;
esac
