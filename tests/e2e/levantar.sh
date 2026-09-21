#!/usr/bin/env bash
#
# Levanta el arnes E2E completo en local y deja todo listo para Playwright.
# Es lo mismo que hace `.github/workflows/e2e.yml`, en un solo comando, para
# no tener que reproducir el workflow a mano cuando algo falla en CI.
#
#   ./tests/e2e/levantar.sh          # construir, levantar y sembrar
#   ./tests/e2e/levantar.sh --solo-levantar   # sin reconstruir los jars
#
# Al terminar:
#   cd frontend/app-web && npx playwright test --config=playwright.e2e.config.js
#   docker compose -f tests/e2e/compose.yml down -v
#
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$RAIZ"

if [ "${1:-}" != "--solo-levantar" ]; then
  echo "== Construyendo los jars =="
  # Sin `test`: las pruebas unitarias ya corren en CI y aqui solo hacen falta
  # los jars. Construirlas otra vez son varios minutos por nada.
  ./gradlew --no-daemon -x test \
    :services:contenido:heroes:bootJar \
    :services:contenido:productos:bootJar \
    :services:contenido:inventario:bootJar \
    :services:contenido:motor-combate:bootJar \
    :services:cuentas:ms-finanzas:bootJar \
    :services:plataforma:salas-partidas:bootJar

  (cd services/cuentas/ms-identidad && ./mvnw -B -DskipTests package)
fi

echo "== Levantando el entorno =="
docker compose -f tests/e2e/compose.yml up -d --build --wait

echo "== Sembrando =="
chmod +x tests/e2e/sembrar.sh
./tests/e2e/sembrar.sh

# Las pruebas estan en `tests/e2e/` -su sitio segun la estructura del
# monorepo- pero `node_modules` esta en `frontend/app-web/`. Node resuelve los
# imports subiendo directorios desde el archivo que importa, asi que sin este
# enlace `import '@playwright/test'` falla con "Cannot find module" aunque el
# paquete este instalado. El enlace esta en .gitignore, no se versiona.
if [ ! -e node_modules ]; then
  echo "== Enlazando node_modules en la raiz =="
  ln -sfn frontend/app-web/node_modules node_modules
fi

cat <<'FIN'

Entorno listo. El borde esta en http://localhost:8099

  cd frontend/app-web
  npx playwright test --config=playwright.e2e.config.js

Para desmontarlo:

  docker compose -f tests/e2e/compose.yml down -v --remove-orphans
FIN
