package com.nexusbattles.plataforma.adminparametros.politicas;

/**
 * Las politicas de uso que nombra el documento oficial en las causales de
 * sancion (§7.3.2): los terminos y condiciones del servicio y las politicas
 * de privacidad. Las demas causales (lenguaje, acoso, fraude, trampas,
 * suplantacion) son reglas de esos terminos, no politicas aparte; ampliar la
 * lista es cambiar el requisito, no la API.
 */
public enum Politica {
    TERMINOS_Y_CONDICIONES("Términos y condiciones del servicio"),
    POLITICA_DE_PRIVACIDAD("Política de privacidad");

    private final String nombre;

    Politica(String nombre) {
        this.nombre = nombre;
    }

    /** Como se lee en la consola y en el aviso al jugador. */
    public String nombre() {
        return nombre;
    }
}
