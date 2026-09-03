---
paths:
  - "frontend/**/*"
  - "shared/ui-kit/**/*"
---

# Frontend — HTML/CSS/JS sin framework (`PILA_T_1.PDF` + `PaletaDeColoresYPilaTecnologica.pdf`)

**Ya NO se usa Angular** (ni React). Dos versiones anteriores de la propuesta lo consideraron; la
definitiva es HTML5 + CSS3 + JavaScript ES2022 con módulos, sin framework y sin paso de compilación,
servido como recursos estáticos por el propio Spring Boot (mismo origen, sin CORS). Razón principal:
las maquetas del proyecto ya están en HTML/CSS plano — reescribirlas no aportaba nada en un semestre.

## Stack
- HTML5, CSS3, JavaScript ES2022 con módulos nativos — sin framework, sin build step
- Una página HTML por vista; un `.js` del mismo nombre cargado como módulo
- Estilos: **Tailwind CSS** con la paleta y tipografía de abajo declaradas como variables de tema
- Componentes compartidos en `shared/ui-kit/` (tarjeta, tabla, paginación, barra de vida, insignias
  de estado) — se escriben una vez y se importan, nunca se reimplementan por módulo
- Los 4 estados obligatorios (carga / error / éxito / vacío) en toda vista que consulte datos
  (RNF-USA-003) — función compartida en `shared/ui-kit/`
- Consumo de API: `fetch` con el envoltorio común de `src/comun/` (token Keycloak, trace id, formato
  de error estándar) — no llamar a `fetch` "pelado" en cada módulo
- Verificación de tipos: JSDoc + comprobación del editor (no hay compilador)
- Calidad: ESLint + Prettier · Pruebas end-to-end: Playwright
- Tipografías Rajdhani e Inter, autoalojadas — no depender de un CDN externo

## Corrección técnica importante: STOMP, no Socket.IO
Una versión anterior consideró Socket.IO por su reconexión automática — descartado. **Socket.IO no
es WebSocket**: usa su propio protocolo y su propia negociación sobre HTTP, así que un cliente de
Socket.IO no logra conectarse a un endpoint de Spring WebSocket con STOMP (no degrada, simplemente
no conecta). Usar siempre **cliente STOMP sobre WebSocket nativo**. Afecta sala de batalla, chat de
sala, subastas, **notificaciones** y chatbot. La reconexión automática se resuelve con configuración
del propio cliente STOMP, no con otra biblioteca.

## Paleta de colores — verificada WCAG 2.2 AA

Tema **claro**: la mayoría de nuestras vistas son de lectura densa (tablas, hilos, colas de
moderación, paneles, formularios) y un fondo claro reduce fatiga visual frente a uno oscuro. El
oscuro (Cromo) queda acotado solo a nav y cabecera de combate. Verde/ámbar/rojo de la barra de vida
están fijados por RF-JUE-009 y se reutilizan en toda la interfaz — no reasignarlos a otra cosa.

**Marca**
| Token | Hex | Uso | Contraste |
|---|---|---|---|
| Fondo | `#D7DEED` | fondo general de la app | — |
| Superficie | `#FFFFFF` | tarjetas y paneles | — |
| Cromo | `#1C2340` | SOLO nav y cabecera de combate | — |
| Borde decorativo | `#9FABC9` | separadores, líneas de tabla (no interactivo) | 2,30:1 |
| Borde interactivo | `#6F7994` | campos de texto, botones outline | 3,22–4,34:1 |
| Acción primaria | `#2244BF` | enlaces, íconos | 5,88:1 |
| Acción primaria (relleno) | `#1E3F88` | fondo sólido de botón, texto blanco | 8,54:1 |
| Acento (relleno) | `#582784` | fondo sólido, texto blanco | 8,77:1 |

**Estado** (texto/ícono en color saturado; insignias con fondo tenue del mismo color, no relleno sólido)
| Estado | Color | Fondo insignia |
|---|---|---|
| Éxito (vida >60%) | `#086B31` | `#E4F5E9` |
| Advertencia (vida 60–40%) | `#8A4A00` | `#FBEEDD` |
| Error (vida <40%) | `#B81A1A` | `#FBE4E4` |
| Información | `#095E8C` | `#DDEEF7` |

**Texto:** principal `#111726` (13,25:1, AAA) · secundario `#48536A` (5,72:1) · terciario `#57627A`
(4,53:1 — justo sobre el mínimo AA, no oscurecer más el fondo detrás de este tono).

## Tipografía y tamaños
- **Rajdhani** 600–700 para títulos · **Inter** 400–600 para cuerpo/interfaz · Inter variante
  tabular para cifras (créditos, calificaciones, métricas)
- Título grande: 28px Rajdhani 700 · Título mediano: 20px Rajdhani 600 · Cuerpo: 16px Inter 400–500
  · Texto pequeño: 13px Inter 400 · Texto de botón: 14px Inter 600
- Componentes: botón grande 48px · botón mediano 40px · botón pequeño/ícono 32px · campo de texto
  40px · ícono estándar 20–24px

## Reglas de interfaz obligatorias (no son propuesta — ya están en el SRS)
- Resolución mínima 1360×768, adaptable hacia abajo (RNF-USA-001, RNF-POR-002)
- Paginación de 16 elementos por página en vitrinas y listados (RNF-USA-001)
- Los 4 estados (carga/error/éxito/vacío) en toda vista que consulte datos (RNF-USA-003)
- Efectos y controles representados con iconos, interacción consistente entre módulos (RNF-USA-002)
- Pantalla de combate: área de juego >80%, menús abajo/costado, barra de info arriba (RN-COM-010)
- Barra de vida verde/ámbar/rojo con el valor numérico siempre visible (RF-JUE-009, RN-COM-005)
- Toda función principal operable por teclado, foco siempre visible (RNF-ACC-002)
- Chatbot flotante, redimensionable, conserva historial, presente en todas las vistas (RNF-DIS-002)

## Diseño
**Sistema de diseño** (fuente de verdad visual — 101 variables, 16 conjuntos, 30 iconos, 8 estilos
de texto, 2 modos de color): https://www.figma.com/design/iMcw1JhmSAD6XjzDZuYlIz — fuente para
`shared/ui-kit/` y las variables de tema en `src/comun/`. Las variables CSS del Figma usan los
nombres `--fondo`, `--superficie`, `--cromo`, `--texto`, `--texto-2`, `--texto-3`, `--borde`,
`--primaria` — adoptarlos tal cual, no inventar nombres propios.

Figma con la auditoría UX y las pantallas de Sprint 1 (usa estos hex exactos vía variables de
color, no valores sueltos): https://www.figma.com/design/PXanKCqsAYLemJhyTyTTHk — no rediseñar
desde cero, traducir lo que ya está ahí a HTML/CSS/JS plano.

## Organización del código
Un `.html` + un `.js` del mismo nombre por vista. Lo que se repite en dos vistas sube obligatoriamente
a `src/comun/` (dueño: los 3 Scrum Masters, aprobación transversal) o a `shared/ui-kit/` — nunca se
duplica dentro de una vista.
