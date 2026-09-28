package nexus.misiones.dominio;

/**
 * Los niveles de dificultad escalonada de la seccion 7.8.11.
 *
 * <p>Heroico: «enemigos con 50% mas estadisticas»; Legendario: «con 100% mas».
 * Mitico: «maxima dificultad», sin cifra en el documento (la propia ficha de
 * HU-MIS-014 lo anota): su multiplicador es una decision del PO y, mientras no
 * exista, el escalon no se ofrece. «Cada nivel superior requiere haber
 * completado el anterior al menos una vez.»
 */
public enum Escalon {
    NORMAL(1.0),
    HEROICO(1.5),
    LEGENDARIO(2.0),
    MITICO(null);

    private final Double multiplicadorDelDocumento;

    Escalon(Double multiplicadorDelDocumento) {
        this.multiplicadorDelDocumento = multiplicadorDelDocumento;
    }

    /** Lo que fija el documento; nulo en Mitico. */
    public Double multiplicadorDelDocumento() {
        return multiplicadorDelDocumento;
    }

    /** El escalon que hay que completar antes; nulo en Normal. */
    public Escalon anterior() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }
}
