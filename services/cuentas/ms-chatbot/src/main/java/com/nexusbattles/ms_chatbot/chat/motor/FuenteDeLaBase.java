package com.nexusbattles.ms_chatbot.chat.motor;

/**
 * 7.4.9: de dónde saca {@link MotorRespuestas} la base en producción. En la
 * aplicación es {@link TemasEnProduccion}, que la guarda en memoria por
 * versión; las pruebas del motor le dan una lista fija.
 */
@FunctionalInterface
public interface FuenteDeLaBase {

    /** La versión en producción con sus temas activos, o {@link BaseEnProduccion#vacia()}. */
    BaseEnProduccion enProduccion();
}
