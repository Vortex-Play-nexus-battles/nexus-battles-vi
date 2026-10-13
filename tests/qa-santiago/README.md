# Suite de aceptación del Grupo de Santiago

Pruebas extremo a extremo, **una por criterio de aceptación**, de las 31 historias de usuario del grupo
(M01 identidad/RBAC, M03 auditoría y privacidad, M06 tienda, M07 pagos y créditos, M11 métricas,
M12 subastas, M14 chatbot). Corren con Playwright contra el banco de servicios reales
(`tests/e2e/compose.yml`) más `compose.qa.yml`, que añade ms-cumplimiento y ms-chatbot.

Cada caso (`CP-<HU>-NN`) guarda su evidencia en `salida/evidencias/<caso>/NN.png` con la descripción
de cada paso en `manifest.json`.

## Ejecución local

```bash
export QA_JWT_CLAVE_PRIVADA=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0)
docker compose -f tests/e2e/compose.yml -f tests/qa-santiago/compose.qa.yml up -d --build --wait
./tests/e2e/sembrar.sh
node tests/qa-santiago/semilla-qa.mjs            # crea datos-qa.json (productos, subastas, cuentas)
cd frontend/app-web
npx playwright test --config=../../tests/qa-santiago/playwright.qa.config.js
```

| Variable | Para qué | Por defecto |
|---|---|---|
| `E2E_BORDE` | URL del borde del banco | `http://localhost:8099` |
| `QA_RAIZ` | raíz del monorepo | `../..` desde `frontend/app-web` |
| `QA_SALIDA` | evidencias, estado entre casos e informes | `tests/qa-santiago/salida` (ignorada por Git) |
| `QA_COMPOSE_EXTRA` | override adicional para la fase 2FA (p. ej. imágenes locales) | — |

`zz-fase-2fa.qa.spec.js` corre al final: reinicia ms-identidad y ms-cumplimiento con `compose.2fa.yml`
(2FA obligatorio para roles administrativos), ejecuta sus casos y deja el banco como estaba.

En CI la ejecuta `.github/workflows/qa-santiago.yml` y publica las evidencias y el informe HTML como artefactos.
