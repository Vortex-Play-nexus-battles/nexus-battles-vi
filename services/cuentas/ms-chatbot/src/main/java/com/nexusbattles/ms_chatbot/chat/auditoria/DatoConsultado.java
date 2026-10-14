package com.nexusbattles.ms_chatbot.chat.auditoria;

/**
 * ms-chatbot.yaml 1.18.0 (§7.4.4, observación de RF-CHA-008: «el acceso del
 * chatbot a datos personales debe respetar el modelo de autorización y quedar
 * auditado»): qué datos de su cuenta consultó el asistente por una persona.
 *
 * <p>Solo lo que se lee con el token de esa persona. Los torneos no están: se
 * leen del listado público ({@code GET /torneos}, sin credencial). Ir a una
 * sección tampoco: no lee nada.
 */
public enum DatoConsultado {
    INVENTARIO("inventario"),
    BUSQUEDA_EN_INVENTARIO("busqueda-en-inventario"),
    SUBASTAS("subastas"),
    NOTIFICACIONES("notificaciones"),
    MOVIMIENTOS("movimientos-de-creditos"),
    MISIONES("misiones"),
    INFORME_DE_ACTIVIDAD("informe-de-actividad");

    private final String clave;

    DatoConsultado(String clave) {
        this.clave = clave;
    }

    /** Como sale en el asiento: estable, en minúsculas y sin tildes. */
    public String clave() {
        return clave;
    }
}
