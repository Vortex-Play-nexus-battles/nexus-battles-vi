# Mapa de responsabilidad de los requisitos funcionales

De donde sale: `REQUERIMIENTOS_EMPRESA_A_DIVISION.pdf` (Empresa A, SRS The Nexus
Battles VI, revision 1.1 — adaptacion organizacional), campo **Responsabilidad
organizacional -> Grupo responsable** de cada ficha. Extraido de las 185 fichas
del documento, no transcrito a mano.

## Por que existe este archivo

El monorepo es uno y el frontend `app-web` es uno, pero los requisitos estan
repartidos entre tres grupos. Sin el reparto a la vista, «esto no esta
conectado» y «esto no nos toca construirlo» se confunden, y la respuesta a un
hueco de backend acaba siendo implementarlo — que en un requisito de otro grupo
es pisarle el Sprint.

Regla que sigue FRONTEND-INTEGRATION:

- **Requisito de Grupo de Simon** -> se implementa entero, backend incluido.
- **Requisito de otro grupo con el backend ausente** -> el frontend deja de
  aparentar que funciona (CTA deshabilitado diciendo por que, dato ausente
  dicho como ausente), se registra la deuda citando el RF y su grupo, y **no**
  se implementa el servicio de otro.
- **Defecto del frontend compartido** (un campo mal leido, un precio que sale
  en cero, una vista que afirma lo que no sabe) -> se arregla siempre, sea de
  quien sea el requisito: el archivo es comun y el defecto es de quien lo ve.

Este archivo describe el reparto; no lo decide. Si el reparto cambia, cambia el
documento fuente y este se regenera.

## Recuento

| Grupo | Requisitos |
|---|---|
| Grupo de Santiago | 79 |
| Grupo de Thomas | 57 |
| Grupo de Simon | 49 |

## Por modulo

| Modulo | Santiago | Simon | Thomas |
|---|---|---|---|
| AUT — Seguridad y acceso | 10 | — | — |
| USR — Usuarios | 4 | 7 | 1 |
| INV — Jugador e inventario | — | — | 11 |
| HER — Heroes y personajes | — | — | 9 |
| PRD — Administracion de productos | — | — | 10 |
| CAR — Carro de compras y comercio electronico | 10 | — | — |
| PAG — Pagos | 6 | — | — |
| SUB — Subastas | 18 | — | — |
| MIS — Misiones | — | — | 17 |
| TOR — Torneos | — | 8 | — |
| JUE — Juego en linea | 2 | 9 | 8 |
| CHA — Chatbot | 14 | — | — |
| COR — Correo electronico | — | 5 | — |
| NOT — Notificaciones | — | 6 | — |
| COM — Comentarios | — | 9 | — |
| AUD — Auditoria | 6 | — | — |
| PRV — Proteccion de datos y privacidad | 6 | — | — |
| MET — Metricas y analitica | 2 | 2 | — |
| ADM — Administracion general | 1 | 3 | 1 |

## Lo que esto significa para las vistas del frontend

| Vista / flujo | Requisitos que la gobiernan | Grupo |
|---|---|---|
| Tienda: vitrina, carrito | RF-CAR-001 a RF-CAR-009 | Santiago |
| Tienda: resumen de compra y pago | RF-CAR-010, RF-PAG-001 | Santiago |
| Subastas: listado, puja, compra inmediata, cierre | RF-SUB-001 a RF-SUB-018 | Santiago |
| Notificaciones de subasta y tiempo real | RF-NOT-003, RF-NOT-006 | **Simon** |
| Inventario y equipamiento del heroe | RF-INV-001 a RF-INV-011 | Thomas |
| Heroes: catalogo, estadisticas, progresion, XP | RF-HER-001 a RF-HER-009 | Thomas |
| Administracion de productos (suspender/reactivar) | RF-PRD-001 a RF-PRD-010 | Thomas |
| Misiones | RF-MIS-001 a RF-MIS-017 | Thomas |
| Salas de batalla: crear, ingresar, exigir personaje equipado | RF-JUE-001 a RF-JUE-004 | **Simon** |
| Combate: turnos, ataque, fin de partida, caida de objetos | RF-JUE-005 a RF-JUE-008, 010, 011, 018, 019 | Thomas |
| Combate: barra de vida, apuesta, chat, IA, interfaz | RF-JUE-009, 014, 015, 016, 017 | **Simon** |
| Combate: creditos por partida y cofre | RF-JUE-012, RF-JUE-013 | Santiago |
| Administracion de usuarios, sanciones y apelaciones | RF-USR-004 a RF-USR-010 | **Simon** |
| Comentarios y su moderacion | RF-COM-001 a RF-COM-009 | **Simon** |
| Torneos | RF-TOR-001 a RF-TOR-008 | **Simon** |
| Correo | RF-COR-001 a RF-COR-005 | **Simon** |
| Parametros del sistema, lista negra, admin de torneos | RF-ADM-001, RF-ADM-002, RF-ADM-005 | **Simon** |
| Metricas de usuarios/moderacion y tecnicas | RF-MET-001, RF-MET-004 | **Simon** |

## Ficha por ficha

| RF | Grupo | Sprint | Nombre |
|---|---|---|---|
| RF-AUT-001 | Santiago | S1 | Registro de nuevo usuario |
| RF-AUT-002 | Santiago | S1 | Validación de la política de contraseñas |
| RF-AUT-003 | Santiago | S1 | Validación del apodo contra lista negra |
| RF-AUT-004 | Santiago | S1 | Inicio de sesión |
| RF-AUT-005 | Santiago | S2 | Recuperación de cuenta |
| RF-AUT-006 | Santiago | S2 | Cambio de contraseña |
| RF-AUT-007 | Santiago | S2 | Autenticación de dos factores para administradores |
| RF-AUT-008 | Santiago | S2 | Cierre de sesión por inactividad |
| RF-AUT-009 | Santiago | S2 | Control de intentos de acceso fallidos |
| RF-AUT-010 | Santiago | S2 | Notificación de accesos no reconocidos |
| RF-USR-001 | Santiago | S1 | Gestión del perfil propio |
| RF-USR-002 | Santiago | S1 | Creación de usuarios administrativos |
| RF-USR-003 | Santiago | S1 | Modificación de usuarios por administradores |
| RF-USR-004 | Simon | S3 | Emisión de advertencias |
| RF-USR-005 | Simon | S3 | Suspensión temporal de cuentas |
| RF-USR-006 | Simon | S3 | Baneo definitivo de cuentas |
| RF-USR-007 | Simon | S3 | Proceso de apelación de sanciones |
| RF-USR-008 | Simon | S3 | Panel de administración de usuarios |
| RF-USR-009 | Simon | S3 | Búsqueda, filtrado y exportación de usuarios |
| RF-USR-010 | Simon | S3 | Vista administrativa del perfil de usuario |
| RF-USR-011 | Santiago | S1 | Restablecimiento de contraseña por administrador |
| RF-USR-012 | Thomas | S2 | Estadísticas y logros del jugador |
| RF-INV-001 | Thomas | S1 | Vista responsiva del inventario |
| RF-INV-002 | Thomas | S1 | Control de paginación |
| RF-INV-003 | Thomas | S1 | Banner de misiones disponibles |
| RF-INV-004 | Thomas | S2 | Búsqueda indexada en el inventario |
| RF-INV-005 | Thomas | S2 | Vista de detalle del producto en inventario |
| RF-INV-006 | Thomas | S1 | Realce visual del producto |
| RF-INV-007 | Thomas | S2 | Gestión del inventario propio |
| RF-INV-008 | Thomas | S1 | Barra de navegación principal |
| RF-INV-009 | Thomas | S2 | Equipamiento del héroe |
| RF-INV-010 | Thomas | S2 | Indicadores de estado del elemento |
| RF-INV-011 | Thomas | S2 | Bloqueo de productos publicados en subasta |
| RF-HER-001 | Thomas | S1 | Catálogo de prototipos de héroe |
| RF-HER-002 | Thomas | S1 | Estadísticas base del héroe |
| RF-HER-003 | Thomas | S1 | Progresión de nivel del héroe |
| RF-HER-004 | Thomas | S1 | Otorgamiento de experiencia |
| RF-HER-005 | Thomas | S1 | Gestión del poder del héroe |
| RF-HER-006 | Thomas | S2 | Acciones especiales del héroe |
| RF-HER-007 | Thomas | S2 | Habilidades épicas |
| RF-HER-008 | Thomas | S2 | Adquisición de habilidades épicas |
| RF-HER-009 | Thomas | S2 | Aplicación del multiplicador de nivel |
| RF-PRD-001 | Thomas | S1 | Panel de control del inventario global |
| RF-PRD-002 | Thomas | S1 | Creación de productos |
| RF-PRD-003 | Thomas | S1 | Tiraje limitado de productos |
| RF-PRD-004 | Thomas | S1 | Modificación de productos existentes |
| RF-PRD-005 | Thomas | S1 | Suspensión lógica y reactivación de productos |
| RF-PRD-006 | Thomas | S2 | Gestión de productos de pago en moneda real |
| RF-PRD-007 | Thomas | S2 | Diseñador visual de productos |
| RF-PRD-008 | Thomas | S2 | Historial de versiones y plantillas de diseño |
| RF-PRD-009 | Thomas | S2 | Respaldo automático y reversión de cambios |
| RF-PRD-010 | Thomas | S2 | Confirmación de acciones críticas sobre productos |
| RF-CAR-001 | Santiago | S1 | Vitrina de productos del comercio electrónico |
| RF-CAR-002 | Santiago | S1 | Presentación del precio según ubicación geográfica |
| RF-CAR-003 | Santiago | S1 | Marcador de promoción |
| RF-CAR-004 | Santiago | ? | Añadir a la cesta y a la lista de deseos |
| RF-CAR-005 | Santiago | S2 | Distinción de productos deseados y propios |
| RF-CAR-006 | Santiago | S1 | Búsqueda y filtrado de la vitrina |
| RF-CAR-007 | Santiago | S2 | Gestión del carro de compras |
| RF-CAR-008 | Santiago | ? | Disponibilidad y vistas del carro de compras |
| RF-CAR-009 | Santiago | S2 | Persistencia y guardado del carro |
| RF-CAR-010 | Santiago | S2 | Resumen de compra y formulario de pago |
| RF-PAG-001 | Santiago | S2 | Integración con pasarela de pagos simulada |
| RF-PAG-002 | Santiago | S2 | Registro de transacciones en moneda real |
| RF-PAG-003 | Santiago | S2 | Correo de confirmación de compra |
| RF-PAG-004 | Santiago | S3 | Facturación electrónica automática |
| RF-PAG-005 | Santiago | S3 | Cumplimiento de normativas fiscales |
| RF-PAG-006 | Santiago | S3 | Doble confirmación en compras de alto valor |
| RF-SUB-001 | Santiago | S2 | Publicación de un producto en subasta |
| RF-SUB-002 | Santiago | S2 | Modalidades de duración de la subasta |
| RF-SUB-003 | Santiago | S2 | Cobro de la comisión de publicación |
| RF-SUB-004 | Santiago | S2 | Subasta por pujas |
| RF-SUB-005 | Santiago | S3 | Compra inmediata |
| RF-SUB-006 | Santiago | S2 | Reserva y liberación de créditos |
| RF-SUB-007 | Santiago | S3 | Pujas automáticas |
| RF-SUB-008 | Santiago | S3 | Finalización de subasta con ganador |
| RF-SUB-009 | Santiago | S3 | Finalización de subasta sin ofertas |
| RF-SUB-010 | Santiago | S3 | Usuario «Maestro de Juego» |
| RF-SUB-011 | Santiago | S2 | Listado de subastas activas |
| RF-SUB-012 | Santiago | S3 | Vista de detalle de la subasta |
| RF-SUB-013 | Santiago | S3 | Panel de gestión personal de subastas |
| RF-SUB-014 | Santiago | S3 | Reclamación de productos ganados |
| RF-SUB-015 | Santiago | S3 | Cancelación de subastas |
| RF-SUB-016 | Santiago | S3 | Límites de participación en subastas |
| RF-SUB-017 | Santiago | S3 | Detección de fraude en subastas |
| RF-SUB-018 | Santiago | S3 | Resolución de disputas de subastas |
| RF-MIS-001 | Thomas | S2 | Tablón de misiones |
| RF-MIS-002 | Thomas | S2 | Categorías de misión |
| RF-MIS-003 | Thomas | S2 | Estructura de la misión |
| RF-MIS-004 | Thomas | S2 | Matriculación de una misión |
| RF-MIS-005 | Thomas | S2 | Configuración de rotaciones de habilidades |
| RF-MIS-006 | Thomas | S3 | Lógica de decisión de la inteligencia artificial |
| RF-MIS-007 | Thomas | S3 | Simulación automática de la misión |
| RF-MIS-008 | Thomas | S3 | Encuentro aleatorio con enemigos Máster |
| RF-MIS-009 | Thomas | S3 | Estados de la misión |
| RF-MIS-010 | Thomas | S3 | Reporte de misión |
| RF-MIS-011 | Thomas | S3 | Historial de misiones |
| RF-MIS-012 | Thomas | S3 | Bloqueo del héroe durante la misión |
| RF-MIS-013 | Thomas | S3 | Entrega de recompensas de misión |
| RF-MIS-014 | Thomas | S3 | Niveles de dificultad escalonada |
| RF-MIS-015 | Thomas | S3 | Logros y reconocimientos |
| RF-MIS-016 | Thomas | S3 | Procesamiento asíncrono de misiones |
| RF-MIS-017 | Thomas | S3 | Diseño de misiones y Máster propios del equipo |
| RF-TOR-001 | Simon | S3 | Creación de torneos |
| RF-TOR-002 | Simon | S3 | Inscripción y pago del torneo |
| RF-TOR-003 | Simon | S3 | Gestión de equipos de torneo |
| RF-TOR-004 | Simon | S3 | Administración del árbol del torneo |
| RF-TOR-005 | Simon | S3 | Sustitución por equipos de inteligencia artificial |
| RF-TOR-006 | Simon | S3 | Transmisión de encuentros del torneo |
| RF-TOR-007 | Simon | S3 | Premiación del torneo |
| RF-TOR-008 | Simon | S3 | Consulta de equipos e inscripción a torneos |
| RF-JUE-001 | Simon | S1 | Creación de sala de batalla |
| RF-JUE-002 | Simon | S1 | Ingreso a una sala existente |
| RF-JUE-003 | Simon | S1 | Exigencia de personaje equipado |
| RF-JUE-004 | Simon | S1 | Modalidades de partida |
| RF-JUE-005 | Thomas | S1 | Determinación del orden de turnos |
| RF-JUE-006 | Thomas | S1 | Ejecución de una acción por turno |
| RF-JUE-007 | Thomas | S1 | Resolución aleatoria del efecto del ataque |
| RF-JUE-008 | Thomas | S2 | Recálculo de la distribución de efectos con equipamiento |
| RF-JUE-009 | Simon | S1 | Barra de progreso de vida |
| RF-JUE-010 | Thomas | S2 | Prohibición de daño entre aliados |
| RF-JUE-011 | Thomas | S2 | Finalización de la partida |
| RF-JUE-012 | Santiago | S2 | Asignación de créditos por partida |
| RF-JUE-013 | Santiago | S2 | Cofre de recompensa por acumulación de créditos |
| RF-JUE-014 | Simon | S2 | Apuesta de créditos en batallas |
| RF-JUE-015 | Simon | S2 | Chat de salas y de vista general |
| RF-JUE-016 | Simon | S2 | Inteligencia artificial de los personajes |
| RF-JUE-017 | Simon | S2 | Diseño de la interfaz de combate |
| RF-JUE-018 | Thomas | S2 | Bloqueo de cambios de equipamiento en combate |
| RF-JUE-019 | Thomas | S2 | Caída de objetos al derrotar enemigos |
| RF-CHA-001 | Santiago | S3 | Disponibilidad permanente del chatbot |
| RF-CHA-002 | Santiago | S3 | Ventana de conversación del chatbot |
| RF-CHA-003 | Santiago | S3 | Atención a visitantes y usuarios registrados |
| RF-CHA-004 | Santiago | S3 | Procesamiento de lenguaje natural |
| RF-CHA-005 | Santiago | S3 | Base de conocimiento del chatbot |
| RF-CHA-006 | Santiago | S3 | Tipos de respuesta del chatbot |
| RF-CHA-007 | Santiago | S3 | Gestión de consultas no resueltas |
| RF-CHA-008 | Santiago | S3 | Consulta de información en tiempo real |
| RF-CHA-009 | Santiago | S3 | Acciones asistidas del chatbot |
| RF-CHA-010 | Santiago | S3 | Personalización del chatbot por perfil |
| RF-CHA-011 | Santiago | S3 | Retroalimentación y aprendizaje continuo |
| RF-CHA-012 | Santiago | S3 | Panel de analíticas del chatbot |
| RF-CHA-013 | Santiago | S3 | Gestión de la base de conocimiento |
| RF-CHA-014 | Santiago | S3 | Entrenamiento y validación del modelo |
| RF-COR-001 | Simon | S1 | Envío de correo con plantilla corporativa |
| RF-COR-002 | Simon | S1 | Confirmación de creación de cuenta |
| RF-COR-003 | Simon | S1 | Correo de recuperación de contraseña |
| RF-COR-004 | Simon | S2 | Envío de mensajes publicitarios |
| RF-COR-005 | Simon | S2 | Correos de misiones y subastas |
| RF-NOT-001 | Simon | S2 | Alertas al iniciar sesión |
| RF-NOT-002 | Simon | S2 | Banner informativo rotativo |
| RF-NOT-003 | Simon | S2 | Notificaciones del módulo de subastas |
| RF-NOT-004 | Simon | S2 | Notificaciones del módulo de misiones |
| RF-NOT-005 | Simon | S2 | Notificaciones de sanciones y políticas |
| RF-NOT-006 | Simon | S2 | Notificaciones en tiempo real |
| RF-COM-001 | Simon | S2 | Publicación de comentarios |
| RF-COM-002 | Simon | S2 | Calificación única por producto |
| RF-COM-003 | Simon | S2 | Cálculo de la calificación promedio |
| RF-COM-004 | Simon | S2 | Eliminación de comentarios propios |
| RF-COM-005 | Simon | S3 | Cola de moderación de comentarios |
| RF-COM-006 | Simon | S3 | Reporte de comentarios por los usuarios |
| RF-COM-007 | Simon | S3 | Filtros automáticos de contenido |
| RF-COM-008 | Simon | S3 | Acciones de moderación sobre comentarios |
| RF-COM-009 | Simon | S3 | Ordenamiento y votación de utilidad de comentarios |
| RF-AUD-001 | Santiago | S1 | Registro de auditoría de acciones administrativas |
| RF-AUD-002 | Santiago | S1 | Inmutabilidad del registro de auditoría |
| RF-AUD-003 | Santiago | S1 | Acceso restringido al registro de auditoría |
| RF-AUD-004 | Santiago | S2 | Exportación del registro de auditoría |
| RF-AUD-005 | Santiago | S3 | Conservación del registro de auditoría |
| RF-AUD-006 | Santiago | S2 | Auditoría de transacciones económicas |
| RF-PRV-001 | Santiago | S2 | Cifrado de información sensible |
| RF-PRV-002 | Santiago | S2 | Anonimización de datos |
| RF-PRV-003 | Santiago | S3 | Consentimiento explícito para el tratamiento de datos |
| RF-PRV-004 | Santiago | S3 | Portal de privacidad |
| RF-PRV-005 | Santiago | S3 | Derecho al olvido |
| RF-PRV-006 | Santiago | S3 | Acceso y portabilidad de los datos |
| RF-MET-001 | Simon | S3 | Métricas de usuarios y moderación |
| RF-MET-002 | Santiago | S3 | Métricas del mercado de subastas |
| RF-MET-003 | Santiago | S3 | Métricas del chatbot y de la atención |
| RF-MET-004 | Simon | S3 | Métricas técnicas de la plataforma |
| RF-ADM-001 | Simon | S2 | Configuración de parámetros del sistema |
| RF-ADM-002 | Simon | S1 | Gestión de la lista negra de términos |
| RF-ADM-003 | Thomas | S2 | Gestión de anuncios y banners |
| RF-ADM-004 | Santiago | S3 | Gestión de tickets de soporte y disputas |
| RF-ADM-005 | Simon | S3 | Administración de torneos |
