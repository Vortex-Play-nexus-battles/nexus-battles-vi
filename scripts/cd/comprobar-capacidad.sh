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
#
# ## Solo cuenta lo que SUMA memoria (R17.0)
#
# Los umbrales responden a una pregunta: "¿cabe una JVM mas?". Un despliegue
# que solo REEMPLAZA contenedores que ya corren (una version nueva de
# ms-identidad, por ejemplo) no suma ninguna: Compose para el contenedor viejo
# antes de arrancar el nuevo. Y una corrida que solo trae frontend y borde no
# toca ninguna JVM. Aplicarles los umbrales tenia dos efectos, los dos malos:
#
#   - ANTES bloqueaba cualquier despliegue en cuanto el swap libre bajaba de
#     150 MiB, que es justo el regimen normal del host con sus 12 JVM
#     (medido: 129-225 MiB). La corrida 35961416881 (#712, solo frontend) se
#     quedo sin desplegar por eso.
#   - DESPUES, si saltaba, el paso "apagar" apagaba lo recien desplegado: un
#     servicio del MVP que ya estaba en marcha antes, con el mismo margen.
#
# Por eso ANTES apunta en ESTADO_NUEVOS que servicios de la corrida no tenian
# contenedor en marcha. Los umbrales solo se aplican si hay alguno, y el paso
# "apagar" de cd.yml solo apaga esos. Un reemplazo nunca se apaga desde aqui.
set -euo pipefail

MODO="${1:-antes}"

MIN_DISP_ANTES="${MIN_DISP_ANTES:-150}"
MIN_SWAP_ANTES="${MIN_SWAP_ANTES:-150}"
MIN_DISP_DESPUES="${MIN_DISP_DESPUES:-80}"
MIN_SWAP_DESPUES="${MIN_SWAP_DESPUES:-50}"

# "servicio:puerto servicio:puerto ..." -- la misma lista que recibe
# desplegar.sh. Vacia = la corrida solo trae frontend y borde.
SERVICIOS_PUERTOS="${SERVICIOS_PUERTOS:-}"
ESTADO_NUEVOS="${ESTADO_NUEVOS:-/opt/nexus/capacidad-nuevos.txt}"

contenedores_en_marcha() {
  docker ps --format '{{.Names}}' 2>/dev/null \
    || sudo -n docker ps --format '{{.Names}}' 2>/dev/null \
    || true
}

# Servicios de la corrida que no tienen hoy su contenedor srv-<servicio> en
# marcha: son los unicos que van a ocupar memoria que hoy no esta ocupada.
nuevos_de_la_corrida() {
  local en_marcha par servicio
  en_marcha=$(contenedores_en_marcha)
  for par in $SERVICIOS_PUERTOS; do
    servicio="${par%%:*}"
    [ -n "$servicio" ] || continue
    # Here-string y no tuberia: con pipefail, un `printf | grep -q` puede
    # salir con SIGPIPE cuando grep encuentra pronto, y el servicio se
    # contaria como nuevo sin serlo.
    if ! grep -qx "srv-${servicio}" <<< "$en_marcha"; then
      printf '%s ' "$servicio"
    fi
  done
}

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
    nuevos=$(nuevos_de_la_corrida)
    nuevos="${nuevos% }"
    # Se apunta SIEMPRE, tambien vacio: DESPUES y el paso "apagar" leen esto
    # y no deben heredar la lista de una corrida anterior.
    if ! printf '%s\n' "$nuevos" > "$ESTADO_NUEVOS" 2>/dev/null; then
      echo "::warning::No se pudo escribir $ESTADO_NUEVOS; DESPUES aplicara los umbrales por prudencia."
    fi
    if [ -z "$nuevos" ]; then
      echo "  Esta corrida no suma memoria: solo reemplaza contenedores que ya corren"
      echo "  (${SERVICIOS_PUERTOS:-ninguno; solo frontend y borde}). Los umbrales no aplican."
      exit 0
    fi
    echo "  Servicios que hoy no corren y esta corrida arranca: $nuevos"
    if [ "$disponible" -lt "$MIN_DISP_ANTES" ] || [ "$swap_libre" -lt "$MIN_SWAP_ANTES" ]; then
      echo "::error::El host no tiene sitio para un servicio mas ($nuevos): hacen falta ${MIN_DISP_ANTES} MiB disponibles y ${MIN_SWAP_ANTES} MiB de swap libre. Apaga algo primero (entrada 'apagar' de cd.yml) o reparte carga al otro host."
      echo "No se despliega. Esto NO es un fallo del servicio: es que no cabe."
      exit 1
    fi
    echo "  Hay sitio. Continua el despliegue."
    ;;

  despues)
    fallo=0
    if [ -f "$ESTADO_NUEVOS" ]; then
      nuevos=$(tr -d '\n' < "$ESTADO_NUEVOS")
      conocido=1
    else
      nuevos=""
      conocido=0
    fi
    if [ "$conocido" -eq 1 ] && [ -z "${nuevos// /}" ]; then
      # Solo reemplazos: el margen es el mismo que antes del despliegue. Se
      # informa, pero no es motivo para apagar un servicio que ya corria.
      echo "  Corrida sin servicios nuevos: el margen se informa, no se exige."
    elif [ "$disponible" -lt "$MIN_DISP_DESPUES" ] || [ "$swap_libre" -lt "$MIN_SWAP_DESPUES" ]; then
      echo "::error::Despues del despliegue el host quedo por debajo del minimo (${MIN_DISP_DESPUES} MiB disponibles / ${MIN_SWAP_DESPUES} MiB de swap). Servicios nuevos de esta corrida: ${nuevos:-desconocidos}."
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
      echo "::error::Apagar solo lo que esta corrida arranco de nuevo (${nuevos:-nada}), nunca un servicio que ya corria."
      exit 1
    fi
    echo "  El MVP sigue en pie."
    ;;

  *)
    echo "::error::Modo no reconocido: '$MODO'. Usa 'antes' o 'despues'."
    exit 1
    ;;
esac
