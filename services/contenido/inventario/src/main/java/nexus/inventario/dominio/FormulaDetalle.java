package nexus.inventario.dominio;

/**
 * Representacion local de una formula "base + NdM", espejo de
 * FormulaDetalle del contrato heroes.yaml. Se declara aqui, y no se importa
 * la clase del servicio heroes, porque ArchUnit prohibe imports de dominio
 * entre servicios: todo cruce va por REST (ver ClienteHeroesHttp en
 * motor-combate para el precedente).
 */
public record FormulaDetalle(int base, int cantidadDados, int caras) {

    public FormulaDetalle sumarABase(int delta) {
        return new FormulaDetalle(base + delta, cantidadDados, caras);
    }

    /**
     * Agrega dados adicionales al conteo, asumiendo el mismo numero de caras
     * de la formula base.
     *
     * <p><b>Supuesto sin confirmar:</b> RN-EQP-008 da los dados extra con su
     * propio numero de caras (ej. "Kit de urgencias +(2d6)"), pero no aclara
     * que pasa si esas caras no coinciden con las de la formula base del
     * heroe. Aqui se asume que coinciden porque en todos los ejemplos
     * conocidos (Chamán/Médico) el dado del objeto coincide con el dado base
     * de curacion del héroe; si un caso real no coincide, este metodo lanza
     * para no aplicar un calculo silenciosamente incorrecto.
     */
    public FormulaDetalle sumarDados(int cantidadDadosExtra, int carasEsperadas) {
        if (cantidadDadosExtra == 0) {
            return this;
        }
        if (caras != carasEsperadas) {
            throw new IllegalStateException(
                    "El objeto agrega dados de %d caras pero la formula base usa %d caras; "
                            .formatted(carasEsperadas, caras)
                            + "el supuesto de compatibilidad de dados no se cumple, revisar con Cesar");
        }
        return new FormulaDetalle(base, cantidadDados + cantidadDadosExtra, caras);
    }
}
