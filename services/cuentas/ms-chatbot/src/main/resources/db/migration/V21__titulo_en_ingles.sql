-- ms-chatbot.yaml 1.17.0 (7.4.2 «comprensión de consultas en lenguaje natural
-- en español/english» y «sugerencias de preguntas relacionadas basadas en el
-- contexto»): el título en inglés de cada tema. Hasta aquí las preguntas
-- relacionadas, los «Related topics» y las sugerencias al escalar salían en
-- español en una respuesta en inglés, y pulsar una cambiaba la conversación
-- al español.
--
-- Opcional: un tema sin título en inglés sigue usando el español. Se llena
-- solo en los temas de la base que despliegan las migraciones (los 62 de
-- contenido/base-de-conocimiento.json), en todas sus versiones, y solo
-- donde el tema conserva su título de siempre y aún no tiene uno en inglés:
-- lo que un administrador cambió no se toca. Los temas creados desde la
-- consola lo reciben desde el panel.

ALTER TABLE temas_conocimiento ADD COLUMN titulo_en VARCHAR(150);

UPDATE temas_conocimiento SET titulo_en = 'Available heroes'
  WHERE clave = 'heroes-disponibles' AND titulo = 'Héroes disponibles' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'A hero''s stats and level'
  WHERE clave = 'estadisticas-y-nivel' AND titulo = 'Estadísticas y nivel de un héroe' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Weapons, armor and items'
  WHERE clave = 'armas-armaduras-items' AND titulo = 'Armas, armaduras e ítems' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Epic abilities'
  WHERE clave = 'habilidades-epicas' AND titulo = 'Habilidades épicas' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How turn-based combat works'
  WHERE clave = 'combate-por-turnos' AND titulo = 'Cómo funciona el combate por turnos' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Random effects in combat'
  WHERE clave = 'efectos-aleatorios' AND titulo = 'Sistema de efectos aleatorios en combate' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Battle mode (PvP and co-op)'
  WHERE clave = 'modo-batalla' AND titulo = 'Modo Batalla (JcJ y cooperativo)' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to complete a mission (PvE mode)'
  WHERE clave = 'completar-mision' AND titulo = 'Cómo completar una misión (modo JcE)' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How the Tournament works'
  WHERE clave = 'torneo' AND titulo = 'Cómo funciona el Torneo' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to create an account'
  WHERE clave = 'crear-cuenta' AND titulo = 'Cómo crear una cuenta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Recover my password'
  WHERE clave = 'recuperar-contrasena' AND titulo = 'Recuperar mi contraseña' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Manage my account and profile'
  WHERE clave = 'mi-cuenta' AND titulo = 'Gestionar mi cuenta y mi perfil' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to put a product up for auction'
  WHERE clave = 'publicar-subasta' AND titulo = 'Cómo publicar un producto en subasta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to bid or buy in an auction'
  WHERE clave = 'pujar-o-comprar' AND titulo = 'Cómo pujar o comprar en una subasta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Credits and in-game currency'
  WHERE clave = 'creditos' AND titulo = 'Créditos y moneda del juego' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'General technical problem'
  WHERE clave = 'problema-tecnico' AND titulo = 'Problema técnico general' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Sanctions system'
  WHERE clave = 'sanciones' AND titulo = 'Sistema de sanciones' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to appeal a sanction'
  WHERE clave = 'apelar-sancion' AND titulo = 'Cómo apelar una sanción' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Greeting'
  WHERE clave = 'saludo' AND titulo = 'Saludo' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Farewell'
  WHERE clave = 'despedida' AND titulo = 'Despedida' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'What THE NEXUS BATTLES VI is'
  WHERE clave = 'que-es-el-juego' AND titulo = 'Qué es THE NEXUS BATTLES VI' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Guide for new players'
  WHERE clave = 'guia-nuevos-jugadores' AND titulo = 'Guía para nuevos jugadores' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Equip your hero (complete «deck»)'
  WHERE clave = 'completar-mazo' AND titulo = 'Equipar a tu héroe («mazo completo»)' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Basic combat strategies'
  WHERE clave = 'estrategias-basicas' AND titulo = 'Estrategias básicas de combate' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Team synergies and combinations'
  WHERE clave = 'sinergias-equipo' AND titulo = 'Sinergias y combinaciones de equipo' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Tips for organizing your items'
  WHERE clave = 'consejos-objetos' AND titulo = 'Consejos para organizar tus objetos' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'What you lose when you are defeated'
  WHERE clave = 'derrota-del-heroe' AND titulo = 'Qué pierdes al ser derrotado' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Mission types: Story, Challenge and Exploration'
  WHERE clave = 'tipos-de-misiones' AND titulo = 'Tipos de misiones: Historia, Desafío y Exploración' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Mission rewards'
  WHERE clave = 'recompensas-de-misiones' AND titulo = 'Recompensas de las misiones' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Mission difficulty levels'
  WHERE clave = 'dificultades-de-misiones' AND titulo = 'Niveles de dificultad de las misiones' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Achievements, titles and badges'
  WHERE clave = 'logros-titulos-insignias' AND titulo = 'Logros, títulos e insignias' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Product prices and availability'
  WHERE clave = 'precios-y-disponibilidad' AND titulo = 'Precios y disponibilidad de productos' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'System requirements'
  WHERE clave = 'requisitos-del-sistema' AND titulo = 'Requisitos del sistema' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Maintenance and updates'
  WHERE clave = 'mantenimiento-y-actualizaciones' AND titulo = 'Mantenimiento y actualizaciones' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Talk to human support'
  WHERE clave = 'hablar-con-soporte' AND titulo = 'Hablar con soporte humano' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to create a battle room'
  WHERE clave = 'crear-sala-de-batalla' AND titulo = 'Cómo crear una sala de batalla' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to join a battle room'
  WHERE clave = 'unirse-a-una-sala' AND titulo = 'Cómo entrar a una sala de batalla' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to sign up for a tournament'
  WHERE clave = 'inscribirse-en-torneo' AND titulo = 'Cómo inscribirte en un torneo' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to buy in the store (credits or simulated payment)'
  WHERE clave = 'comprar-en-la-tienda' AND titulo = 'Cómo comprar en la tienda (créditos o pago simulado)' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'I didn''t get the code to confirm my email'
  WHERE clave = 'verificar-correo' AND titulo = 'No me llegó el código para confirmar mi correo' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Account locked after failed attempts'
  WHERE clave = 'cuenta-bloqueada' AND titulo = 'Cuenta bloqueada por intentos fallidos' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'My session ended on its own'
  WHERE clave = 'sesion-terminada' AND titulo = 'Mi sesión se cerró sola' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Two-step verification for your account'
  WHERE clave = 'verificacion-en-dos-pasos' AND titulo = 'Verificación en dos pasos de tu cuenta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Your account''s security questions'
  WHERE clave = 'preguntas-de-seguridad' AND titulo = 'Preguntas de seguridad de tu cuenta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to change your password in My Account'
  WHERE clave = 'cambiar-contrasena' AND titulo = 'Cómo cambiar tu clave en Mi Cuenta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Change your nickname in your profile'
  WHERE clave = 'cambiar-apodo' AND titulo = 'Cambiar tu apodo en tu perfil' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Code of conduct and word filter'
  WHERE clave = 'normas-de-conducta' AND titulo = 'Normas de conducta y filtro de palabras' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Your data and closing your account'
  WHERE clave = 'privacidad-y-datos' AND titulo = 'Tus datos y cerrar tu cuenta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How to cancel an auction'
  WHERE clave = 'cancelar-subasta' AND titulo = 'Cómo cancelar una subasta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Automatic bidding in an auction'
  WHERE clave = 'puja-automatica' AND titulo = 'Puja automática en una subasta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Collect what you won in an auction'
  WHERE clave = 'recoger-subasta-ganada' AND titulo = 'Recoger lo que ganaste en una subasta' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Auction and bid limits'
  WHERE clave = 'limites-de-subastas' AND titulo = 'Límites de subastas y pujas' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Special actions and power in combat'
  WHERE clave = 'acciones-y-poder' AND titulo = 'Acciones especiales y poder en combate' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Online battle rewards'
  WHERE clave = 'recompensas-de-batalla' AND titulo = 'Recompensas de las batallas en línea' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Reward chests'
  WHERE clave = 'cofres' AND titulo = 'Cofres de recompensa' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Private messages and chat'
  WHERE clave = 'mensajes-privados' AND titulo = 'Mensajes privados y chat' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How notifications work'
  WHERE clave = 'notificaciones' AND titulo = 'Cómo funcionan las notificaciones' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Comment on and rate products'
  WHERE clave = 'comentarios-y-calificaciones' AND titulo = 'Comentar y calificar productos' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Assistant preferences'
  WHERE clave = 'preferencias-del-asistente' AND titulo = 'Preferencias del asistente' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Basic game rules'
  WHERE clave = 'reglas-del-juego' AND titulo = 'Reglas básicas del juego' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'Terms of service and rules of use'
  WHERE clave = 'terminos-del-servicio' AND titulo = 'Términos del servicio y reglas de uso' AND titulo_en IS NULL;
UPDATE temas_conocimiento SET titulo_en = 'How the Auction works'
  WHERE clave = 'como-funciona-la-subasta' AND titulo = 'Cómo funciona la Subasta' AND titulo_en IS NULL;
