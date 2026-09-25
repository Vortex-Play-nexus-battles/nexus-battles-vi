#!/usr/bin/env bash
#
# Siembra los datos minimos para que la puerta de heroe (HU-SAL-003) diga que
# si, y comprueba que el camino entero responde ANTES de que Playwright abra
# el navegador. Si algo falla aqui, el diagnostico es mucho mas claro que un
# "elemento no encontrado" a los treinta segundos de prueba.
#
# Desde R17 el heroe, su equipo y los creditos iniciales de cada jugador no
# los siembra este script: los pone el alta del jugador al registrarse, por las
# APIs de ms-finanzas e inventario, igual que en DEV. Este script registra a
# los jugadores por el camino normal y espera a que su alta termine, de modo
# que cada corrida del banco prueba tambien el alta. La unica insercion directa
# es la coleccion `productos` (su alta exige rol de administrador): el kit del
# banco apunta a esos productos.
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
  db.productos.deleteMany({ _id: { $in: ["p-heroe-e2e", "p-arma-e2e", "dddddddd-0000-0000-0000-00000000000a"] } });
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
      // Con imagen, a diferencia de los otros dos. El retrato del heroe de
      // combate sale de aqui (R8: producto.imagen -> HeroeDeCombate.retratoUrl),
      // asi que con `imagen: null` el campo llegaria nulo y la prueba no podria
      // distinguir "se propaga" de "no habia nada que propagar".
      //
      // La ruta la sirve el propio borde: monta ../../frontend en
      // /srv/nexus/frontend, y el fichero existe en el repositorio. O sea que
      // ademas de no ser nula, se puede cargar.
      imagen: "/frontend/app-web/src/cuentas/avatares/guerrero-tanque.jpg",
      poderDeAtaque: null, tasaDeCaida: NumberDecimal("0")
    }),
    Object.assign({}, base, {
      _id: "p-arma-e2e", nombre: "Espada de prueba", tipo: "ARMA",
      prototipo: null,
      poderDeAtaque: 12, tasaDeCaida: NumberDecimal("50")
    }),
    // El tercero tiene un _id con forma de UUID a proposito. Los dos de arriba
    // no sirven para subastar: PublicarSubastaRequest declara
    // `@NotNull UUID productoId`, asi que "p-arma-e2e" se rechaza con 400 antes
    // de llegar al negocio. Sin este producto no se puede probar la subasta de
    // un objeto real, que es justo lo que subastas.e2e.spec.js decia que le
    // faltaba (HU-SUB-001 CA de inventario).
    Object.assign({}, base, {
      _id: "dddddddd-0000-0000-0000-00000000000a",
      nombre: "Hacha subastable de prueba", tipo: "ARMA",
      prototipo: null,
      poderDeAtaque: 9, tasaDeCaida: NumberDecimal("50")
    })
  ]);
  print("  productos sembrados: " + db.productos.countDocuments({ _id: /e2e/ }));
'

# Comprobar YA que productos los sirve. Si esto falla, el 500 de
# /estadisticas viene de aqui y no de inventario, y conviene saberlo antes de
# perseguirlo en el servicio equivocado.
for p in p-heroe-e2e p-arma-e2e dddddddd-0000-0000-0000-00000000000a; do
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

# R17 — el heroe, su arma equipada y los creditos iniciales ya NO los pone esta
# semilla: los pone el alta del jugador (ms-identidad la orquesta contra
# ms-finanzas, inventario y productos en cuanto se registra). Lo que hace aqui
# la semilla es registrar a cada jugador por el camino normal y ESPERAR a que
# su alta termine. Si no termina, el banco se pone rojo aqui, con el estado
# del alta y la bitacora delante, y no a los treinta segundos de una prueba.
#
# Por eso los productos (paso 1) se siembran ANTES de registrar a nadie: el
# kit del banco (JUGADOR_KIT_INICIAL en compose.yml) apunta a p-heroe-e2e y
# p-arma-e2e, y el alta los busca en el catalogo.
esperar_alta() {
  local apodo="$1" estado="" cuerpo=""
  echo "== 2) Alta de $apodo: creditos, heroe y equipo los pone ms-identidad =="
  for _ in $(seq 1 60); do
    cuerpo=$(curl -sS "$BORDE/api/v1/auth/onboarding" -H "Authorization: Bearer $(token_de "$apodo")")
    estado=$(echo "$cuerpo" | jq -r '.estado // empty')
    [ "$estado" = "COMPLETO" ] && break
    sleep 1
  done
  if [ "$estado" != "COMPLETO" ]; then
    echo "::error::El alta de $apodo no termino en 60 s (estado=${estado:-sin respuesta}):"
    echo "$cuerpo" | jq . 2>/dev/null || echo "$cuerpo"
    echo "Bitacora del alta en ms-identidad:"
    $COMPOSE logs --tail 120 srv-ms-identidad 2>/dev/null | grep -E 'ONBOARDING|AUDITORIA_CUENTA|ERROR' | tail -40 || true
    exit 1
  fi
  echo "  $apodo: alta COMPLETA creditos=$(echo "$cuerpo" | jq -r '.creditosIniciales') heroe=$(echo "$cuerpo" | jq -r '.heroeInicial')"
}

esperar_alta "$ANFITRION"
esperar_alta "$INVITADO"
esperar_alta "$CURIOSO"
esperar_alta "$POBRE"
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

# R17 — el saldo inicial ya no lo acredita esta semilla: lo acredito el alta de
# cada jugador (concepto `bono-registro`, JUGADOR_CREDITOS_INICIALES=500 en el
# banco, los mismos 500 que se sembraban antes a mano). Aqui se COMPRUEBA.
#
# El curioso lo necesita: su intento con codigo equivocado reserva antes de que
# la sala lo rechace, y sin saldo recibiria 422 en vez del 409 que se prueba.
for apodo in "$ANFITRION" "$INVITADO" "$CURIOSO"; do
  uid=$(uid_de "$apodo")
  [ -n "$uid" ] || { echo "::error::el token de $apodo no trae uid"; exit 1; }
  # El saldo lo consulta el propio jugador: es el unico caso en que un
  # usuario (no un servicio) puede leer /creditos/{uid}/saldo, y solo el suyo.
  disponible=$(curl -sS -H "Authorization: Bearer $(token_de "$apodo")" "$FINANZAS/creditos/$uid/saldo" | jq -r '.saldoDisponible // empty')
  if [ "$(printf '%.0f' "${disponible:-0}")" -lt "$SALDO_INICIAL" ]; then
    echo "::error::el alta de $apodo no dejo sus $SALDO_INICIAL creditos (disponible=${disponible:-?})"; exit 1
  fi
  echo "  $apodo: uid=$uid disponible=$disponible (bono de registro)"
done

# El pobre, a proposito, se queda sin nada: es el que prueba el 422 de
# creditos insuficientes. Desde R17 tambien recibe su bono al registrarse, asi
# que se le debita entero, como si lo hubiera gastado. Por el libro y con la
# credencial del banco, no tocando la base de ms-finanzas; refId fijo, asi que
# repetir la semilla no debita dos veces.
uid_pobre=$(uid_de "$POBRE")
disponible_pobre=$(curl -sS -H "Authorization: Bearer $(token_de "$POBRE")" "$FINANZAS/creditos/$uid_pobre/saldo" | jq -r '.saldoDisponible // 0')
if [ "$(printf '%.0f' "$disponible_pobre")" -gt 0 ]; then
  codigo=$(curl -sS -o /tmp/debitar-pobre.json -w '%{http_code}' -X POST "$FINANZAS/creditos/debitar" \
    -H "Authorization: Bearer $TOKEN_BANCO" -H "Content-Type: application/json" \
    -d "{\"uid\":\"$uid_pobre\",\"monto\":$disponible_pobre,\"refId\":\"semilla-e2e-vaciar-$POBRE\",\"concepto\":\"semilla-e2e\"}")
  [ "$codigo" = "200" ] || { echo "::error::ms-finanzas no debito al pobre ($codigo):"; cat /tmp/debitar-pobre.json; echo; exit 1; }
fi
disponible_pobre=$(curl -sS -H "Authorization: Bearer $(token_de "$POBRE")" "$FINANZAS/creditos/$uid_pobre/saldo" | jq -r '.saldoDisponible // empty')
[ "$(printf '%.0f' "${disponible_pobre:-1}")" -eq 0 ] || { echo "::error::el pobre sigue con saldo ($disponible_pobre)"; exit 1; }
echo "  $POBRE: uid=$uid_pobre disponible=$disponible_pobre (bono gastado a proposito)"
echo "== 5) Moderacion (HU-USR-004..007): una moderadora y un administrador ==="
# ms-identidad registra a todo el mundo como JUGADOR y el unico camino para
# crear MODERADOR/ADMINISTRADOR es el endpoint de admin, que exige... un
# administrador. En el banco E2E se rompe el huevo-gallina en la base de
# identidad, que es de este compose y de nadie mas: se registran por el
# camino normal y se les cambia el rol por SQL. El token con el rol nuevo lo
# obtienen al iniciar sesion DESPUES de este paso (el rol viaja en el JWT).
MODERADORA="${E2E_MODERADORA:-moderadora_e2e}"
ADMIN="${E2E_ADMIN:-admin_e2e}"
token_de "$MODERADORA" >/dev/null
token_de "$ADMIN" >/dev/null
$COMPOSE exec -T e2e-identidad-db psql -v ON_ERROR_STOP=1 -U identidad -d identidaddb -q <<SQL
UPDATE usuarios SET rol_id = (SELECT id FROM roles WHERE nombre = 'MODERADOR')     WHERE apodo = '$MODERADORA';
UPDATE usuarios SET rol_id = (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR') WHERE apodo = '$ADMIN';
SQL
roles=$($COMPOSE exec -T e2e-identidad-db psql -At -U identidad -d identidaddb \
  -c "SELECT u.apodo || '=' || r.nombre FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE u.apodo IN ('$MODERADORA','$ADMIN') ORDER BY 1")
echo "  $roles" | tr '\n' ' '; echo
echo "$roles" | grep -q "^$ADMIN=ADMINISTRADOR$" || { echo "::error::$ADMIN no quedo como ADMINISTRADOR"; exit 1; }
echo "$roles" | grep -q "^$MODERADORA=MODERADOR$" || { echo "::error::$MODERADORA no quedo como MODERADOR"; exit 1; }

echo "Semilla lista."
