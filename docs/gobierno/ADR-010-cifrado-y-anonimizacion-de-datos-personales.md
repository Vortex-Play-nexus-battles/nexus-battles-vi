# ADR-010 — Cifrado y anonimización de los datos personales

- **Estado:** aceptada (la parte técnica); el cifrado del disco del host de contenido queda **bloqueado** (PERMISO AWS + RIESGO DE DATOS)
- **Fecha:** 14 de octubre de 2026
- **Ámbito:** `services/cuentas/ms-identidad`, las bases de todos los servicios, `infrastructure/entornos`, `infrastructure/red-balanceo/tls`
- **Relacionada con:** §7.3.6 del documento del curso, RF-PRV-001 (#541), RF-PRV-002 (#542), ADR-007, ADR-009
- **Clasificación:** REQUISITO PDF (§7.3.6) · DECISIÓN TÉCNICA (cómo se cumple) · PERMISO AWS + RIESGO DE DATOS (disco de contenido)
- **Dueño de los requisitos:** RF-PRV-001 y RF-PRV-002 son del Grupo de Santiago (`MAPA-RESPONSABILIDAD-RF.md`, Sprint 2; HU etiquetadas `grupo-4`). Este ADR deja escrito lo que hay y lo que falta para el cierre del módulo del Super Administrador; no les quita las HU.

## Contexto

§7.3.6 pide «cifrado de información sensible» y «anonimización de datos para
análisis estadísticos». La ficha de RF-PRV-001 lo concreta: cifrar la
información sensible de la base de datos, las transacciones y las
conversaciones del asistente, en tránsito y en reposo, sin prescribir algoritmo
ni longitud de clave. El criterio CA-02 de HU-PRV-001 admite el cifrado a nivel
de almacenamiento cuando el motor no cifra por campo.

Lo que hay en `develop` (comprobado en el código y en la infraestructura):

| Dónde | Qué protege | Cómo |
|---|---|---|
| Navegador ↔ borde | Todo lo que viaja entre la persona y la plataforma | HTTPS en `nexusbattlesvi.com`: TLS 1.2/1.3 con suites AEAD, el 80 redirige al 443 (`infrastructure/red-balanceo/tls`) |
| Disco del host de plataforma | Las bases de ms-identidad (cuentas, correo, nombres), ms-finanzas (transacciones), ms-chatbot (conversaciones), ms-cumplimiento (bitácora con IP), comentarios, moderación y sanciones, notificaciones, correo, ms-ecommerce, torneos y salas, y sus respaldos diarios | Volumen EBS cifrado (`encrypted = true` en `infrastructure/entornos/plataforma/main.tf`, clave gestionada por AWS) |
| Contraseñas, respuestas de seguridad y códigos de correo | El secreto, aunque alguien lea la base | BCrypt: se guarda el resumen, nunca el valor |
| Secreto del segundo factor (TOTP) | Que la base sola no baste para generar códigos | AES-256-GCM (`CifradoDeSecretos`) con una clave que `desplegar.sh` genera una vez por host, guarda en `600` y nunca imprime |
| Estadísticas para analizar la plataforma | Que una cifra no delate a nadie | Solo salen agregadas (cuántas, por día, por estado, por categoría). Ninguna respuesta estadística lleva `uid`, correo, apodo, nombre ni IP |
| Cierre de cuenta | Los datos personales de quien se va | `AnonimizadorDeCuentas` a los 30 días: conserva solo `uid`, rol y alta, lo que exigen finanzas y auditoría |

Lo que **no** está cifrado:

- **El disco del host de contenido.** `infrastructure/entornos/contenido/main.tf`
  no declara `encrypted`. Allí viven ms-subastas (pujas), inventario, héroes,
  productos, misiones y motor-combate. No guardan correo, nombres, contraseñas
  ni pagos; sí `uid` de jugadores y, desde ADR-009, el `uid` y la IP de quien
  hace una operación administrativa sobre el catálogo.
- **El tráfico entre hosts.** Los dos hosts se llaman entre sí por la red
  privada de la VPC en HTTP. Cada grupo de seguridad abre esos puertos solo al
  otro host: 8081-8089 de plataforma para el host de contenido, 8101-8104 de
  contenido para el de plataforma. Dentro de un host, los contenedores hablan
  por la red de Docker.

## Decisión

1. **Cifrado en reposo por almacenamiento, y por campo solo para los
   secretos.** El correo y los nombres no se cifran campo a campo: §7.3.4 pide
   buscar a una persona por nombre, apodo, correo o ID, y el directorio busca
   por coincidencia parcial. Un cifrado por campo aleatorio impide esa búsqueda
   y uno determinista (o un índice ciego) la deja en coincidencia exacta:
   degradaría una función obligatoria a cambio de proteger contra lo mismo que
   ya protege el disco cifrado (la lectura del disco o de una instantánea). Los
   secretos, que nunca se buscan, sí se protegen campo a campo (BCrypt o
   AES-GCM).
2. **Las estadísticas se publican solo agregadas, y una guarda lo fija.**
   `tests/contratos/estadisticas-sin-datos-personales.py` recorre la respuesta
   de cada operación estadística (indicadores de cuentas, tablero de
   moderación, métricas de sanciones, frecuencia de reportes, calificaciones,
   analíticas del asistente, estado del catálogo, tablero técnico) y falla si
   una declara un dato que identifica a una persona. Esa es la anonimización
   para análisis estadístico: no hay un conjunto de datos por persona que
   anonimizar después, porque ninguna estadística sale con identificadores. Si
   algún día hiciera falta uno (una exportación para análisis externo), saldría
   con el `uid` sustituido por un resumen con sal y sin correo, nombres, apodo
   ni IP, y entraría en la lista de la guarda.
3. **El tráfico entre hosts sigue en HTTP dentro de la VPC** mientras exista
   solo el entorno DEV (D-08). Lo limitan la red privada y los grupos de
   seguridad, que solo abren cada puerto al otro host. Cifrarlo exige una
   autoridad de certificados interna y su rotación en los dos hosts: entra en
   el plan de producción, no en DEV.
4. **El disco del host de contenido debe quedar cifrado**, y no se hace desde
   el repositorio: Terraform no puede cifrar el volumen raíz de una instancia
   que ya existe sin reemplazarla, y la compuerta de `infra-dev.yml` impide, a
   propósito, cualquier `replace` sobre la instancia, la IP o el volumen.

## Lo que queda bloqueado y la acción humana exacta

| Bloqueo | Tipo | Acción humana exacta |
|---|---|---|
| Disco del host de contenido sin cifrar | PERMISO AWS + RIESGO DE DATOS | En una ventana aprobada: respaldo de las bases del host (`infrastructure/respaldo-dr/respaldar.sh`, con `contenido-mongo`) copiado fuera de él, host apagado, instantánea del volumen raíz de `nexus-contenido-dev`, copia **cifrada** de esa instantánea y reemplazo del volumen raíz por uno creado desde ella; después, `encrypted = true` en `infrastructure/entornos/contenido/main.tf` con el estado de Terraform alineado al volumen nuevo, y encender. Activar además el cifrado de EBS por omisión en la región, para que ningún volumen nuevo nazca sin cifrar. |

## Consecuencias

- El informe de cierre del Super Administrador declara «Cifrado de
  información sensible» **PARCIAL**: las cuentas, las transacciones, las
  conversaciones y la bitácora están cifradas en reposo y viajan cifradas hasta
  el borde; el disco de contenido y el tráfico entre hosts, no.
- «Anonimización para análisis estadísticos» queda **DONE** con prueba
  automática (la guarda y su autoprueba en CI).
- Una estadística nueva entra en la lista de la guarda en el mismo cambio que la
  crea; si se renombra una de la lista sin actualizarla, la guarda falla.
