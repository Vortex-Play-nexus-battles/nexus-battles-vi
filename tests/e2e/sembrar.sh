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

# Los dos jugadores del E2E. El apodo es lo que viaja a inventario en
# X-User-Name, y tiene que coincidir CARACTER A CARACTER con propietarioId.
ANFITRION="${E2E_ANFITRION:-anfitriona_e2e}"
INVITADO="${E2E_INVITADO:-invitado_e2e}"

# Prototipo que el catalogo de heroes siembra solo (CatalogoEnMongo).
PROTOTIPO="Guerrero Tanque"

echo "== 1) Productos: un heroe y un arma, directos en Mongo =="
# El alta por API exige JWT de administrador; el resolutor de inventario solo
# lee nombre, tipo y prototipo, asi que con eso basta.
$COMPOSE exec -T e2e-contenido-mongo mongosh --quiet productos --eval '
  db.productos.deleteMany({ _id: { $in: ["p-heroe-e2e", "p-arma-e2e"] } });
  db.productos.insertMany([
    { _id: "p-heroe-e2e", nombre: "Guerrero de prueba", tipo: "HEROE",
      prototipo: "Guerrero Tanque", estado: "ACTIVO", premium: false },
    { _id: "p-arma-e2e", nombre: "Espada de prueba", tipo: "ARMA",
      estado: "ACTIVO", premium: false, poderDeAtaque: 12, tasaDeCaida: 50 }
  ]);
  print("  productos sembrados: " + db.productos.countDocuments({ _id: /e2e/ }));
'

# Con jq y no con sed: la primera version sacaba el `id` con una expresion
# regular y cogia el del ARMA cuando buscaba el del HEROE, porque el orden de
# los campos del JSON no es el que uno supone. Un JSON se lee con un lector de
# JSON.
crear_elemento() {
  local apodo="$1" producto="$2" tipo="$3" nombre="$4"
  curl -sS -X POST "$BORDE/api/v1/inventario/elementos" \
    -H "Content-Type: application/json" \
    -H "X-User-Name: $apodo" \
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
      -H "Content-Type: application/json" -H "X-User-Name: $apodo" \
      -d '{"productoId":"p-heroe-e2e","tipo":"HEROE","nombrePropio":"diagnostico"}'
    exit 1
  fi

  # Equipar es lo que hace que `estaEquipado` sea cierto. Sin esto, la puerta
  # de heroe responde SIN_HEROE_EQUIPADO y no se puede crear una sala.
  curl -sS -o /dev/null -w '  equipar -> %{http_code}\n' \
    -X PUT "$BORDE/api/v1/inventario/heroes/$heroe/equipamiento/$arma" \
    -H "X-User-Name: $apodo"

  echo "  heroe=$heroe arma=$arma"
}

sembrar_jugador "$ANFITRION"
sembrar_jugador "$INVITADO"

echo "== 3) Comprobando el camino completo de la verificacion =="
# Las mismas tres llamadas que hace ClienteInventarioHeroes, en el mismo
# orden. Si alguna de las tres falla, la puerta de heroe responde 503 y no se
# puede crear ninguna sala: mejor enterarse aqui que a mitad de la prueba.
for apodo in "$ANFITRION" "$INVITADO"; do
  elementos=$(curl -sS "$BORDE/api/v1/inventario/elementos?pagina=0" -H "X-User-Name: $apodo")
  heroe=$(echo "$elementos" | jq -r '[.elementos[]? | select(.tipo == "HEROE")][0].id // empty')
  [ -n "$heroe" ] || { echo "::error::$apodo no tiene heroe en la vitrina"; echo "$elementos" | jq .; exit 1; }

  equipamiento=$(curl -sS "$BORDE/api/v1/inventario/heroes/$heroe/equipamiento" \
    -H "X-User-Name: $apodo")
  armas=$(echo "$equipamiento" | jq -r '(.armas // []) | length')
  # `estaEquipado` es cierto si hay algo equipado. Con cero armas, la puerta
  # responderia SIN_HEROE_EQUIPADO.
  [ "${armas:-0}" -gt 0 ] || { echo "::error::$apodo tiene el heroe sin equipar: $equipamiento"; exit 1; }

  stats=$(curl -sS "$BORDE/api/v1/inventario/heroes/$heroe/estadisticas" -H "X-User-Name: $apodo")
  vida=$(echo "$stats" | jq -r '.vida // empty')
  # La vida es la que acaba en la barra de HU-SAL-005. Un error aqui tumba la
  # verificacion entera: el cliente envuelve cualquier fallo HTTP en
  # InventarioNoDisponible.
  [ -n "$vida" ] || { echo "::error::$apodo sin estadisticas: $stats"; exit 1; }

  echo "  $apodo: heroe=$heroe armas=$armas vida=$vida"
done

echo "Semilla lista."
