package com.nexusbattles.ms_chatbot.chat.conocimiento;

// 409 (motivo TEMA_DUPLICADO, ms-chatbot.yaml 1.6.0; RF-CHA-013 «entrada
// duplicada o en conflicto con otra existente»): dos temas de la misma
// version con el mismo titulo, sin contar tildes, mayusculas ni signos
// (NormalizadorTexto). El motor reconoce el titulo tal cual como pregunta de
// su tema (1.3.3): con dos iguales, pulsar esa sugerencia responderia uno u
// otro segun la prioridad, y el administrador no sabria cual edita.
public class TemaDuplicadoException extends RuntimeException {

    public TemaDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
