package com.nexusbattles.ms_chatbot.chat.privacidad;

/**
 * 7.4.8 «no almacenamiento de informacion sensible». Todo texto del usuario
 * que el chatbot vaya a guardar o a mandar a otro servicio pasa primero por
 * aqui: redactar, despues moderar (lista negra) y guardar solo lo redactado.
 *
 * <p>Lo que garantiza cada llamada:
 * <ul>
 *   <li>Tapa contrasenas y claves, numeros de tarjeta con su CVV, tokens y
 *       claves privadas. El resto del texto no cambia, asi que la moderacion
 *       sigue viendo un insulto.</li>
 *   <li>El resultado nunca es mas largo que la entrada: los limites de las
 *       columnas (asunto 150, mensaje 2000) siguen valiendo al redactar.</li>
 *   <li>Redactar un texto ya redactado no lo cambia.</li>
 *   <li>{@code null} devuelve {@code null}.</li>
 * </ul>
 *
 * <p>Tiene una sola implementacion, {@link RedactorDeDatosSensibles}: un
 * {@code @Component} sin configuracion. No se crean otras ni adaptadores
 * (lo comprueba {@code RedactorDeDatosSensiblesTest}).
 */
public interface RedaccionDeDatosSensibles {

    String redactar(String texto);
}
