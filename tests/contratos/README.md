# Guardianes de contrato

Scripts sin Docker ni compilación que corren en el job `CI - contratos OpenAPI` de
`.github/workflows/ci.yml` en cada push y pull request. Todos devuelven un código distinto de cero
si encuentran algo, y todos se pueden correr en local desde la raíz del repositorio.

| Script | Qué impide |
|---|---|
| `validar-contratos.py` | Un contrato OpenAPI inválido, un `operationId` o una ruta repetidos entre contratos, un contrato sin `info.version` semántica. Los AsyncAPI se comprueban como YAML. |
| `rutas-contrato-codigo.py` (B0) | Que contrato, código y clientes cuenten historias distintas: operación de contrato sin controlador, controlador sin contrato, cliente (Java, configuración, frontend) que llama a una ruta que ningún contrato declara, destino STOMP `/app/**` sin `@MessageMapping` o al revés. Lo aún no implementado se declara con `x-implementacion: pendiente` + `x-fase`, y sale listado. El dueño de cada contrato está en `mapa-contratos.yaml`. |
| `cambios-de-contrato.py <base>` | Un cambio incompatible sin versión mayor: operación, `operationId` o 2xx que desaparecen, parámetro que pasa a obligatorio, cuerpo de petición con una propiedad obligatoria nueva o un tipo cambiado, respuesta 2xx que pierde una propiedad o cambia de forma, canal AsyncAPI que desaparece. Y un contrato que cambia sin mover `info.version`. |
| `pactos-verificados.py` | Un pacto de `contracts/pactos/` sin prueba de proveedor que monte cada uno de sus estados. |
| `transferencia-de-subasta.py` | Que se desconecte cualquiera de las cinco piezas de la transferencia de propiedad por subasta (#660/#669). |
| `sin-secretos-en-bitacora.py` | Una línea de bitácora que imprima contraseñas, tokens, códigos o secretos. |
| `compose-sin-claves-duplicadas.py`, `compose-credencial-de-servicio-completa.py` | Compose con claves repetidas o con credencial de servicio a medias. |

```bash
python3 tests/contratos/validar-contratos.py
python3 tests/contratos/rutas-contrato-codigo.py --detalle   # --detalle lista las rutas leídas de cada servicio
python3 tests/contratos/cambios-de-contrato.py origin/develop
```

Requisitos: Python 3.10+, `pyyaml` y `openapi-spec-validator==0.7.1`.
