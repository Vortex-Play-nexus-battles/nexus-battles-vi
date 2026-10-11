# Evidencia del movimiento — HU-UX-003

Vídeos cortos (WebM, ≤ 1 MB) con el antes/después de cada PR del Nexus Motion
System. Se graban con Playwright (`recordVideo`) sobre el laboratorio visual y
sus escenarios poblados, nunca a mano, para que se puedan volver a grabar.

| Archivo | PR | Qué enseña |
|---|---|---|
| `poc-waapi-ataque-y-confeti.webm` | PR-1 (MOTION-1) | el POC de MOTION-0 con la solución elegida: anticipa → lunge → impacto → hit-stop → regreso, el HUD al valor del servidor con relleno `scaleX`, y 60 partículas DOM+WAAPI como celebración contenida |
| `batalla-antes.webm` | PR-2 (MOTION-2) | `develop` antes del game feel: el escenario `combate-narrado` del laboratorio (crítico propio, evasión del rival, curación, cambio de turno); los héroes no se mueven y la cifra aparece al llegar el aviso |
| `batalla-despues.webm` | PR-2 (MOTION-2) | el mismo escenario con la coreografía: idle, anticipación → lunge → impacto (flash, recoil, cifra con icono) → regreso; halo de turno con un pulso; la IA «piensa»; estela en la barra |
| `misiones-tablon.webm` | PR-3 (MOTION-3) | el tablón de misiones (`misiones-tablon`): las tarjetas entran escalonadas y la que está EN PROGRESO lleva su halo quieto |
| `torneo-ruta.webm` | PR-3 (MOTION-3) | la ruta del torneo (`torneo-en-curso`): el hito actual late dos veces y se queda con su halo |
