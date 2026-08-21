## Issue relacionado

Closes #

<!-- Si es un avance parcial que NO cierra el Issue, usa: Refs #numero -->

## Historia relacionada

- **ID historia:** <!-- HU-AUT-001 -->
- **ID Jira:** <!-- SCRUM-22 -->

## Requisito

- **RF / RN / RNF:** <!-- RF-AUT-001 -->

## Grupo

<!-- Marca uno con una x -->

- [ ] Simon (grupo-omega) — Tiempo real, comunidad y plataforma
- [ ] Thomas (grupo-alpha) — Contenido, simulacion y progresion
- [ ] Santiago (grupo-beta) — Cuentas, cumplimiento y economia
- [ ] Compartido — contracts / shared / infrastructure

## Que cambio

<!-- En 2 o 3 frases. Que hace ahora el sistema que antes no hacia. -->

## Como probarlo

<!-- Pasos concretos para que el revisor lo verifique. -->

1.
2.
3.

## Pruebas agregadas

- [ ] Pruebas unitarias
- [ ] Pruebas de integracion
- [ ] Pruebas de aceptacion
- [ ] Pruebas de contrato
- [ ] No aplica — justificar por que:

**Resultado de la ejecucion local:**

```text
<!-- Pegar aqui la salida resumida de la suite de pruebas -->
```

## Cobertura

- **Cobertura del area modificada:** ____ %
- **Objetivo oficial:** >= 80 %
- [ ] Se cumple el objetivo
- [ ] No se cumple — justificacion:

## Integraciones afectadas

- [ ] Ninguna — el cambio queda dentro de mi area
- [ ] Modifica `contracts/` — **requiere aprobacion de los tres Scrum Masters**
- [ ] Modifica `shared/` — **requiere aprobacion de los tres Scrum Masters**
- [ ] Modifica `infrastructure/` — requiere aprobacion de Simon y un administrador
- [ ] Afecta a otro grupo:

## Documentacion

- [ ] Actualizada
- [ ] No hacia falta

## Capturas

<!-- Solo si el cambio es visual. Si no, borra esta seccion. -->

---

## Lista de verificacion

- [ ] El PR cierra o referencia un Issue
- [ ] La rama sigue el formato `<tipo>/<issue>-<descripcion>`
- [ ] El titulo sigue el formato `<tipo>(<ambito>): <descripcion>`
- [ ] Este PR contiene **una sola** historia o tarea
- [ ] Todas las pruebas pasan en local
- [ ] **No incluye ningun secreto**: contrasenas, tokens, API keys, certificados ni `.env`
- [ ] Si anadi variables de entorno, estan en `.env.example` **sin valores**
- [ ] No incluye archivos generados (`node_modules/`, `build/`, `dist/`, `target/`)
- [ ] La rama esta actualizada con `main`
- [ ] He movido la tarjeta del Project a `In review`
