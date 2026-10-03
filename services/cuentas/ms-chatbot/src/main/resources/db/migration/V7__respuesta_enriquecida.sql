-- ms-chatbot.yaml 1.3.4 (7.4.3, 7.4.6): la respuesta enriquecida del bot
-- (pasos, enlaces a secciones del sitio, tarjetas de texto, botones de
-- respuesta rapida y la oferta de soporte humano) se guarda con el mensaje,
-- para que el historial la vuelva a pintar igual. JSON; null en los mensajes
-- del usuario, en los del bot sin nada que pintar y en los anteriores a V7.
ALTER TABLE mensajes ADD COLUMN enriquecido TEXT;
