package com.nexusbattles.ms_chatbot.chat.conocimiento;

// 413 (motivo IMPORTACION_DEMASIADO_GRANDE, ms-chatbot.yaml 1.6.0): el
// archivo de importacion trae mas de MAXIMO_DE_TEMAS temas o pesa mas de
// MAXIMO_DE_BYTES. No se escribe nada.
//
//   * 500 temas: la base publicada tiene 59 y el panel los lista todos de
//     una vez; mas de 500 ya no es una base que alguien revise a mano.
//   * 8 MiB: 500 temas con todos sus campos al maximo (titulo de 150, dos
//     respuestas de 4000 y 28 variantes de 200 caracteres) ocupan unos 7 MB;
//     la base actual pesa 112 KB. Spring no limita el tamano de un cuerpo
//     JSON (el `max-http-form-post-size` de Tomcat es solo para formularios)
//     y el borde corta en 10 MB (`client_max_body_size`), asi que sin este
//     techo el servicio leeria en memoria cualquier archivo que le llegue
//     directo, sin pasar por el borde.
public class ImportacionDemasiadoGrandeException extends RuntimeException {

    public static final int MAXIMO_DE_TEMAS = 500;
    public static final long MAXIMO_DE_BYTES = 8L * 1024 * 1024;

    public ImportacionDemasiadoGrandeException(String mensaje) {
        super(mensaje);
    }

    static ImportacionDemasiadoGrandeException porTemas(int cuantos) {
        return new ImportacionDemasiadoGrandeException("El archivo trae " + cuantos + " temas y se importan como maximo "
            + MAXIMO_DE_TEMAS + ". Divide la base o quita temas antes de importarla.");
    }

    static ImportacionDemasiadoGrandeException porTamano() {
        return new ImportacionDemasiadoGrandeException("El archivo pesa mas de 8 MB, el maximo que se importa ("
            + MAXIMO_DE_TEMAS + " temas). Revisa que sea una exportacion de la base de conocimiento.");
    }
}
