

## Operaciones del catálogo (SA-18, contrato 1.4.0)

Además de los eventos que le envían los demás servicios, la bitácora **importa**
de productos las operaciones de los administradores sobre el catálogo
(`GET /api/v1/productos/auditoria/operaciones`, productos.yaml 1.12.0) con su
propia credencial de servicio. Entran con `afectado` = `producto:<id>`,
`banner:<id>` o `recurso:<id>`, el nombre que tenía lo afectado
(`nombreAfectado`, columna `nombre_afectado` de V5), el momento en que
ocurrieron (`ocurridoEn`), su IP y su referencia `productos:<id>`, que es única:
repetir una ronda no duplica nada. Diseño y motivos en
`docs/gobierno/ADR-009-la-bitacora-importa-las-operaciones-del-catalogo.md`.

| Variable | Para qué | Por omisión |
|---|---|---|
| `CATALOGO_URL` | Base de productos (sin `/api/v1`) | vacía: importación apagada |
| `DIRECTORIO_ACTIVO_URL` | Endpoint de token del emisor (ADR-005) | vacía: importación apagada |
| `DIRECTORIO_ACTIVO_CLIENT_ID` / `_SECRET` | Credencial de la bitácora (`ms-cumplimiento`; desplegar.sh la genera en el host de plataforma) | vacías: importación apagada |
| `AUDITORIA_CATALOGO_INTERVALO` | Cada cuánto se importa | `PT30S` |
| `AUDITORIA_CATALOGO_SOLAPE` | Cuánto se vuelve a pedir hacia atrás en cada ronda | `PT2M` |

Sin URL o sin credencial la importación queda apagada y el servicio arranca
igual (lo dice en su bitácora). Si productos o el emisor no responden, la
ronda no escribe nada y la siguiente lo vuelve a pedir.

