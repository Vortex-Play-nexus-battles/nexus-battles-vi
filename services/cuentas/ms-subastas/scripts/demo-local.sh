#!/usr/bin/env bash
#
# Prepara y comprueba HU-SUB-004 de punta a punta con creditos REALES.
#
# Por que existe: para demostrar la historia hace falta una subasta activa y un
# jugador con saldo, y hoy conseguir las dos cosas a mano no es trivial.
# Publicar una subasta es HU-SUB-001 y exige catalogo, inventario y finanzas
# en modo http a la vez; y hasta el 17/09/2026 no existia forma de acreditar
# creditos a nadie. Este script hace ese atajo, deja la evidencia por pantalla
# y no toca nada mas.
#
# NO es un sustituto de las pruebas: `./gradlew :services:cuentas:ms-subastas:check`
# sigue siendo la compuerta. Esto es para ver la historia funcionando.
#
# Antes de ejecutarlo, con los dos servicios levantados:
#
#   docker compose -f services/cuentas/ms-finanzas/docker-compose.yml up -d
#   docker compose -f services/cuentas/ms-subastas/docker-compose.yml up -d
#   DB_PASSWORD=finanzas_password ./gradlew :services:cuentas:ms-finanzas:bootRun
#   DB_PASSWORD=subastas_password FINANZAS_MODO=http \
#     ./gradlew :services:cuentas:ms-subastas:bootRun
#
# Uso:  services/cuentas/ms-subastas/scripts/demo-local.sh
set -euo pipefail

FINANZAS=${FINANZAS_URL:-http://localhost:8093/api/v1}
SUBASTAS=${SUBASTAS_URL:-http://localhost:8092/api/v1}
# La misma de application.properties. Es de desarrollo y no sirve fuera de aqui.
CLAVE=${JWT_CLAVE_SECRETA:-una-clave-de-desarrollo-muy-larga-que-nunca-se-usa-en-produccion-cambiala-siempre}

# Jugadores nuevos en cada ejecucion. Es deliberado: con identificadores fijos
# el script solo funciona sobre una base de datos virgen —la segunda vez el
# comprador ya tiene saldo de la vez anterior y el paso 1, que comprueba el
# rechazo por saldo, deja de tener sentido—. Un guion de demostracion que solo
# pasa la primera vez es justo el que falla delante de la clase.
# Aleatorio y no derivado del reloj: con un sufijo por segundo, dos ejecuciones
# seguidas se pisan las identidades entre si —el comprador de una acaba siendo
# el rival de la anterior, que ya tiene saldo, y el paso 1 falla sin motivo
# aparente—. Costo una ejecucion intermitente descubrirlo.
sufijo() { printf '%08x' $((RANDOM * RANDOM % 4294967296)); }
SUFIJO=$(sufijo)
COMPRADOR=c0ffee00-0000-4000-8000-0000$(sufijo)
VENDEDOR=beef0000-0000-4000-8000-0000$(sufijo)
RIVAL=d00d0000-0000-4000-8000-0000$(sufijo)
SUBASTA=aaaa1111-0000-4000-8000-0000$(sufijo)

fallar() { echo "✗ $1" >&2; exit 1; }

curl -sf -o /dev/null "$FINANZAS/actuator/health" || fallar "ms-finanzas no responde en $FINANZAS"
curl -sf -o /dev/null "$SUBASTAS/actuator/health" || fallar "ms-subastas no responde en $SUBASTAS"

# Token de desarrollo firmado con la clave compartida de ms-identidad. El claim
# que importa es 'uid': es el identificador estable, y de ahi sale el jugador en
# todas las operaciones que mueven creditos.
token() {
  CLAVE="$CLAVE" UID_JUGADOR="$1" APODO="$2" python3 - <<'PY'
import hmac, hashlib, base64, json, os, time
clave = os.environ["CLAVE"].encode()
b64 = lambda x: base64.urlsafe_b64encode(x).rstrip(b"=").decode()
cab = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
ahora = int(time.time())
cuerpo = b64(json.dumps({"sub": os.environ["APODO"], "uid": os.environ["UID_JUGADOR"],
                         "rol": "JUGADOR", "ver": 1,
                         "iat": ahora, "exp": ahora + 7200}, separators=(",", ":")).encode())
firma = b64(hmac.new(clave, f"{cab}.{cuerpo}".encode(), hashlib.sha256).digest())
print(f"{cab}.{cuerpo}.{firma}")
PY
}

acreditar() {
  curl -sf -o /dev/null -X POST "$FINANZAS/creditos/acreditar" \
    -H 'Content-Type: application/json' \
    -d "{\"uid\":\"$1\",\"monto\":$2,\"refId\":\"demo-$1\",\"concepto\":\"demo local\"}" \
    || fallar "no se pudo acreditar a $1 (¿/creditos/acreditar ya exige token?)"
}

saldo() { curl -sf "$FINANZAS/creditos/$1/saldo"; }

pujar() { # token, monto, clave
  curl -s -w '\n%{http_code}' -X POST "$SUBASTAS/subastas/$SUBASTA/pujas" \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $3" -d "{\"monto\":\"$2\"}"
}

echo "▸ Sembrando una subasta activa del vendedor"
# A mano y no por la API: publicar es HU-SUB-001 y arrastra catalogo e
# inventario. Para ver pujar no hacen falta.
docker exec postgres-subastas psql -q -U subastas_user -d subastas_db -c "
  INSERT INTO subastas (id, producto_id, vendedor_id, oferta_vigente, incremento_minimo,
      precio_compra_inmediata, estado, fecha_fin, version, nombre_producto,
      cantidad_pujas, elemento_inventario_id)
  VALUES ('$SUBASTA', gen_random_uuid(), '$VENDEDOR', 100, 50, 800, 'ACTIVA',
      NOW() + INTERVAL '2 hours', 0, 'Hacha de obsidiana (demo)', 0, 'elem-demo-$SUFIJO')
  ON CONFLICT (id) DO UPDATE SET estado = 'ACTIVA', oferta_vigente = 100,
      mejor_postor_id = NULL, cantidad_pujas = 0,
      fecha_fin = NOW() + INTERVAL '2 hours';" >/dev/null \
  || fallar "no se pudo sembrar la subasta. Causa probable, por orden: postgres-subastas
   no esta levantado, o el unico parcial uq_subastas_elemento_inventario_activa ya tiene
   una subasta ACTIVA sobre ese elemento (solo se admite una por elemento)."

TOK_COMPRADOR=$(token "$COMPRADOR" comprador_demo)
TOK_RIVAL=$(token "$RIVAL" rival_demo)

echo
echo "▸ 1. Pujar sin saldo debe rechazarse con su motivo, no con un 500"
RESPUESTA=$(pujar "$TOK_COMPRADOR" 150 "demo-sin-saldo-$(sufijo)")
echo "$RESPUESTA" | head -1
[ "$(echo "$RESPUESTA" | tail -1)" = "422" ] || fallar "se esperaba 422 SALDO_INSUFICIENTE"
echo "$RESPUESTA" | grep -q SALDO_INSUFICIENTE || fallar "falta el motivo estable en el cuerpo"

echo
echo "▸ 2. Acreditamos 1000 al comprador y 2000 al rival"
acreditar "$COMPRADOR" 1000
acreditar "$RIVAL" 2000

# Con la clave derivada del reloj, dos ejecuciones dentro del mismo segundo la
# reutilizaban y el servidor las rechazaba con CLAVE_REUTILIZADA —correctamente:
# esa clave identifica UNA operacion, no una por segundo—.
CLAVE_PUJA="demo-puja-$(sufijo)"
echo
echo "▸ 3. Ahora la puja de 150 entra"
RESPUESTA=$(pujar "$TOK_COMPRADOR" 150 "$CLAVE_PUJA")
echo "$RESPUESTA" | head -1
[ "$(echo "$RESPUESTA" | tail -1)" = "201" ] || fallar "la puja deberia haber entrado. Respuesta: $RESPUESTA"
PUJA_ID=$(echo "$RESPUESTA" | head -1 | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

echo "   saldo del comprador: $(saldo "$COMPRADOR")"

echo
echo "▸ 4. Reintentar con la MISMA clave devuelve la misma puja, no una nueva"
REPETIDA=$(pujar "$TOK_COMPRADOR" 150 "$CLAVE_PUJA" | head -1 \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
[ "$REPETIDA" = "$PUJA_ID" ] || fallar "el reintento creo una puja distinta: $REPETIDA"
echo "   misma puja: $PUJA_ID"
echo "   saldo sin doble retencion: $(saldo "$COMPRADOR")"

echo
echo "▸ 5. El rival puja 250 y al superado se le devuelven sus creditos"
pujar "$TOK_RIVAL" 250 "demo-rival-$(sufijo)" | head -1
echo "   superado: $(saldo "$COMPRADOR")"
echo "   rival:    $(saldo "$RIVAL")"

echo
echo "▸ 6. El resumen que ve la pantalla trae el saldo real"
curl -sf -H "Authorization: Bearer $TOK_RIVAL" "$SUBASTAS/mis-pujas/resumen"

echo
echo
echo "✓ HU-SUB-004 funciona de punta a punta con creditos reales."
echo "  Abrir la pantalla:  frontend/app-web -> npm run dev"
echo "  y entrar en        http://localhost:8080/cuentas/pujas.html?id=$SUBASTA"
