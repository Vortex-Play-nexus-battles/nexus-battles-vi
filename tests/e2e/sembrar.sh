#!/usr/bin/env bash
#
# Siembra los datos minimos para que la puerta de heroe (HU-SAL-003) diga que
# si, y comprueba que el camino entero responde ANTES de que Playwright abra
# el navegador. Si algo falla aqui, el diagnostico es mucho mas claro que un
# "elemento no encontrado" a los treinta segundos de prueba.
#
# Se siembra a traves de la API de inventario, no insertando en Mongo: asi la
# prueba usa el mismo camino que usaria un jugador y, de paso, comprueba que
# ese camino funciona. La unica excepcion es la coleccion `productos`, cuya
# alta exige rol de administrador; ahi se inserta directo.
#
#   ./tests/e2e/sembrar.sh              # usa el compose de tests/e2e
#
set -euo pipefail

BORDE="${BORDE:-http://localhost:8099}"
COMPOSE="docker compose -f $(dirname "$0")/compose.yml"

# Los dos jugadores del E2E. Inventario guarda propietarioId por apodo y lo
# lee del JWT del jugador (el sujeto), asi que el apodo con el que se
# registran aqui es el que tiene que coincidir CARACTER A CARACTER.
ANFITRION="${E2E_ANFITRION:-anfitriona_e2e}"
INVITADO="${E2E_INVITADO:-invitado_e2e}"
# Tercero: el que prueba a entrar con un codigo equivocado. Necesita heroe
# aunque no vaya a entrar nunca — sin el, `IngresarASala` lo rechaza por la
# puerta de heroe (422) ANTES de mirar el codigo, y la prueba del codigo no
# estaria probando el codigo.
CURIOSO="${E2E_CURIOSO:-curioso_e2e}"
# Cuarto: tiene heroe pero NI UN credito. Es el que prueba el 422 de
# HU-JUE-014 CA-02 (creditos-insuficientes) en apuesta-de-creditos.e2e.spec.js.
POBRE="${E2E_POBRE:-pobre_e2e}"
# La misma clave que usa sala-de-batalla.e2e.spec.js: el spec vuelve a
# registrar (tolera 400/409) e inicia sesion con ella.
CLAVE="${E2E_CLAVE:-Contrasena-E2E-2026}"

# Prototipo que el catalogo de heroes siembra solo (CatalogoEnMongo).
PROTOTIPO="Guerrero Tanque"

# Inventario ya no cree en X-User-Name a secas: un jugador es quien dice su
# JWT. Asi que cada jugador se registra e inicia sesion en ms-identidad (el
# mismo camino que usara el navegador) y siembra su inventario con su token.
declare -A TOKEN_DE
token_de() {
  local apodo="$1"
  if [ -z "${TOKEN_DE[$apodo]:-}" ]; then
    local email="$apodo@nexus.test"
    # 409/400 si ya existe de una corrida anterior: no es un fallo. El aviso
    # va a stderr para no mezclarse con el token que devuelve esta funcion.
    curl -sS -o /dev/null -w "  registro de $apodo -> %{http_code}\n" \
      -X POST "$BORDE/api/v1/auth/registro" \
      -F "nombres=Jugadora" -F "apellidos=De Prueba" -F "email=$email" \
      -F "password=$CLAVE" -F "apodo=$apodo" >&2
    local token
    token=$(curl -sS -X POST "$BORDE/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$email\",\"password\":\"$CLAVE\"}" | jq -r '.token // empty')
    if [ -z "$token" ]; then
      echo "::error::No se pudo iniciar sesion como $apodo en ms-identidad"; exit 1
    fi
    TOKEN_DE[$apodo]="$token"
  fi
  printf '%s' "${TOKEN_DE[$apodo]}"
}

echo "== 1) Productos: un heroe y un arma, directos en Mongo =="
# El alta por API exige JWT de administrador, asi que se inserta directo.
#
# El documento lleva TODOS los campos del record `Producto`, con su tipo
# exacto. No es verbosidad gratuita: con la version corta -solo nombre, tipo
# y prototipo- productos devolvia 500 al leerlos.
#
# `Producto` es un record, asi que Spring Data lo construye por su constructor
# canonico y no tolera huecos en los primitivos (`int tiraje`,
# `boolean premium`, `int version`). Y dos campos estan mapeados a DECIMAL128:
# un numero suelto escrito desde mongosh llega como Int32 y la conversion
# falla. De ahi `NumberDecimal`.
$COMPOSE exec -T e2e-contenido-mongo mongosh --quiet productos --eval '
  db.productos.deleteMany({ _id: { $in: ["p-heroe-e2e", "p-arma-e2e"] } });
  const base = {
    _class: "nexus.dominio.Producto",
    imagen: null, descripcion: "Producto de prueba del E2E",
    tiraje: 1, precioCreditos: 100,
    precioMonedaReal: NumberDecimal("0"),
    premium: false,
    heroe: null, costoPoder: null,
    multiplicadorNivel: NumberDecimal("1"),
    turnosCarga: null, turnosRecarga: null,
    efectoGeneral: null, efectoPotenciado: null,
    defensa: null, parte: null, efecto: null,
    estado: "ACTIVO", version: 0,
    creadoEn: new Date(), modificadoEn: new Date()
  };
  db.productos.insertMany([
    Object.assign({}, base, {
      _id: "p-heroe-e2e", nombre: "Guerrero de prueba", tipo: "HEROE",
      prototipo: "Guerrero Tanque",
      poderDeAtaque: null, tasaDeCaida: NumberDecimal("0")
    }),
    Object.assign({}, base, {
      _id: "p-arma-e2e", nombre: "Espada de prueba", tipo: "ARMA",
      prototipo: null,
      poderDeAtaque: 12, tasaDeCaida: NumberDecimal("50")
    })
  ]);
  print("  productos sembrados: " + db.productos.countDocuments({ _id: /e2e/ }));
'

# Comprobar YA que productos los sirve. Si esto falla, el 500 de
# /estadisticas viene de aqui y no de inventario, y conviene saberlo antes de
# perseguirlo en el servicio equivocado.
for p in p-heroe-e2e p-arma-e2e; do
  codigo=$(curl -sS -o /tmp/prod-$p.json -w '%{http_code}' "$BORDE/api/v1/productos/$p")
  echo "  GET /api/v1/productos/$p -> $codigo"
  if [ "$codigo" != "200" ]; then
    echo "::error::productos no sirve $p. Respuesta:"; cat "/tmp/prod-$p.json"; echo
    echo "Documento tal como quedo en Mongo:"
    $COMPOSE exec -T e2e-contenido-mongo mongosh --quiet productos \
      --eval "printjson(db.productos.findOne({_id: \"$p\"}))"
    exit 1
  fi
done

echo "  prototipos que publica heroes:"
curl -sS "$BORDE/api/v1/heroes" | jq -r '.[]?.nombre // .[]?.prototipo // empty' 2>/dev/null \
  | head -8 | sed 's/^/    /' || echo "    (no se pudo leer el catalogo)"

# Con jq y no con sed: la primera version sacaba el `id` con una expresion
# regular y cogia el del ARMA cuando buscaba el del HEROE, porque el orden de
# los campos del JSON no es el que uno supone. Un JSON se lee con un lector de
# JSON.
crear_elemento() {
  local apodo="$1" producto="$2" tipo="$3" nombre="$4"
  curl -sS -X POST "$BORDE/api/v1/inventario/elementos" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $(token_de "$apodo")" \
    -d "{\"productoId\":\"$producto\",\"tipo\":\"$tipo\",\"nombrePropio\":\"$nombre\"}" \
    | jq -r '.id // empty'
}

sembrar_jugador() {
  local apodo="$1"
  echo "== 2) Inventario de $apodo: un heroe con un arma equipada =="

  local heroe arma
  heroe=$(crear_elemento "$apodo" p-heroe-e2e HEROE "Aquiles de $apodo")
  arma=$(crear_elemento "$apodo" p-arma-e2e ARMA "Espada de $apodo")

  if [ -z "$heroe" ] || [ -z "$arma" ]; then
    echo "::error::No se pudieron crear los elementos de $apodo. Respuesta de inventario:"
    curl -sS -X POST "$BORDE/api/v1/inventario/elementos" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $(token_de "$apodo")" \
      -d '{"productoId":"p-heroe-e2e","tipo":"HEROE","nombrePropio":"diagnostico"}'
    exit 1
  fi

  # Equipar es lo que hace que `estaEquipado` sea cierto. Sin esto, la puerta
  # de heroe responde SIN_HEROE_EQUIPADO y no se puede crear una sala.
  curl -sS -o /dev/null -w '  equipar -> %{http_code}\n' \
    -X PUT "$BORDE/api/v1/inventario/heroes/$heroe/equipamiento/$arma" \
    -H "Authorization: Bearer $(token_de "$apodo")"

  echo "  heroe=$heroe arma=$arma"
}

sembrar_jugador "$ANFITRION"
sembrar_jugador "$INVITADO"
sembrar_jugador "$CURIOSO"
sembrar_jugador "$POBRE"

echo "== 3) Comprobando el camino completo de la verificacion =="
# Las mismas tres llamadas que hace ClienteInventarioHeroes, en el mismo
# orden. Si alguna de las tres falla, la puerta de heroe responde 503 y no se
# puede crear ninguna sala: mejor enterarse aqui que a mitad de la prueba.
for apodo in "$ANFITRION" "$INVITADO" "$CURIOSO" "$POBRE"; do
  elementos=$(curl -sS "$BORDE/api/v1/inventario/elementos?pagina=0" -H "Authorization: Bearer $(token_de "$apodo")")
  heroe=$(echo "$elementos" | jq -r '[.elementos[]? | select(.tipo == "HEROE")][0].id // empty')
  [ -n "$heroe" ] || { echo "::error::$apodo no tiene heroe en la vitrina"; echo "$elementos" | jq .; exit 1; }

  equipamiento=$(curl -sS "$BORDE/api/v1/inventario/heroes/$heroe/equipamiento" \
    -H "Authorization: Bearer $(token_de "$apodo")")
  armas=$(echo "$equipamiento" | jq -r '(.armas // []) | length')
  # `estaEquipado` es cierto si hay algo equipado. Con cero armas, la puerta
  # responderia SIN_HEROE_EQUIPADO.
  [ "${armas:-0}" -gt 0 ] || { echo "::error::$apodo tiene el heroe sin equipar: $equipamiento"; exit 1; }

  stats=$(curl -sS "$BORDE/api/v1/inventario/heroes/$heroe/estadisticas" -H "Authorization: Bearer $(token_de "$apodo")")
  vida=$(echo "$stats" | jq -r '.vida // empty')
  # La vida es la que acaba en la barra de HU-SAL-005. Un error aqui tumba la
  # verificacion entera: el cliente envuelve cualquier fallo HTTP en
  # InventarioNoDisponible.
  [ -n "$vida" ] || { echo "::error::$apodo sin estadisticas: $stats"; exit 1; }

  echo "  $apodo: heroe=$heroe armas=$armas vida=$vida"
done

echo "== 4) Creditos (HU-JUE-014): saldo inicial en ms-finanzas ==="
# El libro de creditos no esta detras del borde (ms-finanzas no corre en el
# host de dev), asi que se le habla por el puerto que el compose expone.
# Acreditar es idempotente por refId: repetir la semilla no duplica saldo.
FINANZAS="${FINANZAS:-http://localhost:8093/api/v1}"
SALDO_INICIAL="${E2E_SALDO_INICIAL:-500}"

# Desde #455 el libro de creditos solo atiende a servicios (ROLE_SERVICIO,
# ADR-005): la semilla se identifica como el cliente `e2e-banco` registrado en
# AUTH_CLIENTES_SERVICIO del compose y pide su credencial por client_credentials
# al mismo emisor que usan los servicios. Un jugador con su propio token NO
# puede acreditarse (403): eso es justamente lo que R0 cerro.
BANCO_CLIENTE="${E2E_BANCO_CLIENTE:-e2e-banco}"
BANCO_SECRETO="${E2E_BANCO_SECRETO:-e2e-secreto-del-banco-de-pruebas}"
TOKEN_BANCO=$(curl -sS -u "$BANCO_CLIENTE:$BANCO_SECRETO" -X POST "$BORDE/api/v1/auth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" | jq -r '.access_token // empty')
[ -n "$TOKEN_BANCO" ] || { echo "::error::ms-identidad no entrego la credencial de servicio de $BANCO_CLIENTE"; exit 1; }

# Prueba negativa de la semilla: un jugador no se acredita a si mismo.
uid_anfitrion_prueba=$(printf '%s' "$(token_de "$ANFITRION")" | cut -d. -f2 | tr '_-' '/+')
while [ $(( ${#uid_anfitrion_prueba} % 4 )) -ne 0 ]; do uid_anfitrion_prueba="$uid_anfitrion_prueba="; done
uid_anfitrion_prueba=$(printf '%s' "$uid_anfitrion_prueba" | base64 -d 2>/dev/null | jq -r '.uid // empty')
codigo_suplantacion=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$FINANZAS/creditos/acreditar" \
  -H "Authorization: Bearer $(token_de "$ANFITRION")" -H "Content-Type: application/json" \
  -d "{\"uid\":\"$uid_anfitrion_prueba\",\"monto\":999999,\"refId\":\"autoregalo-$ANFITRION\",\"concepto\":\"suplantacion\"}")
if [ "$codigo_suplantacion" != "403" ]; then
  echo "::error::un jugador pudo llamar a /creditos/acreditar con su propio token ($codigo_suplantacion): el libro esta abierto"; exit 1
fi
echo "  suplantacion rechazada por ms-finanzas: 403"

# El `uid` es el identificador estable del jugador (ADR-002): sale del
# token, no del apodo. Se lee del cuerpo del JWT (base64url, sin firma).
uid_de() {
  local token cuerpo
  token=$(token_de "$1")
  cuerpo=$(printf '%s' "$token" | cut -d. -f2 | tr '_-' '/+')
  # Relleno de base64 hasta multiplo de 4.
  while [ $(( ${#cuerpo} % 4 )) -ne 0 ]; do cuerpo="$cuerpo="; done
  printf '%s' "$cuerpo" | base64 -d 2>/dev/null | jq -r '.uid // empty'
}

# El curioso tambien: su intento con codigo equivocado reserva antes de que
# la sala lo rechace, y sin saldo recibiria 422 en vez del 409 que se prueba.
# El pobre, a proposito, se queda sin nada.
for apodo in "$ANFITRION" "$INVITADO" "$CURIOSO"; do
  uid=$(uid_de "$apodo")
  [ -n "$uid" ] || { echo "::error::el token de $apodo no trae uid"; exit 1; }
  codigo=$(curl -sS -o /tmp/acreditar-$apodo.json -w '%{http_code}' \
    -X POST "$FINANZAS/creditos/acreditar" \
    -H "Authorization: Bearer $TOKEN_BANCO" \
    -H "Content-Type: application/json" \
    -d "{\"uid\":\"$uid\",\"monto\":$SALDO_INICIAL,\"refId\":\"semilla-e2e-$apodo\",\"concepto\":\"semilla-e2e\"}")
  if [ "$codigo" != "200" ]; then
    echo "::error::ms-finanzas no acredito a $apodo ($codigo):"; cat "/tmp/acreditar-$apodo.json"; echo; exit 1
  fi
  # El saldo lo consulta el propio jugador: es el unico caso en que un
  # usuario (no un servicio) puede leer /creditos/{uid}/saldo, y solo el suyo.
  disponible=$(curl -sS -H "Authorization: Bearer $(token_de "$apodo")" "$FINANZAS/creditos/$uid/saldo" | jq -r '.saldoDisponible // empty')
  echo "  $apodo: uid=$uid disponible=$disponible"
done

echo "Semilla lista."
