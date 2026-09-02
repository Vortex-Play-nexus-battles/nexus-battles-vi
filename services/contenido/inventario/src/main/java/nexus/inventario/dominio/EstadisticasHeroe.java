package nexus.inventario.dominio;

/**
 * Espejo local de Estadisticas (heroes.yaml), resuelto via
 * {@code ResolutorDeEstadisticasHeroe}. poder/vida/defensa son enteros;
 * ataque/dano/sanar son formulas, ausentes segun el prototipo (los sanadores
 * no tienen ataqueDetalle/danoDetalle; los demas no tienen sanarDetalle).
 *
 * <p>nivel NO viene de heroes.yaml — ese contrato no expone el campo (su
 * propia descripcion del schema Estadisticas dice "Valores de nivel 1 segun
 * la Tabla 6"). Se fija en 1 al resolver (ver
 * {@link ResolutorDeEstadisticasHeroeHttp}) porque este sprint no tiene
 * construido ningun sistema de progresion de niveles; es un supuesto
 * temporal, no un valor real leido de ningun lado.
 */
public record EstadisticasHeroe(
        int poder,
        int vida,
        int defensa,
        int nivel,
        FormulaDetalle ataqueDetalle,
        FormulaDetalle danoDetalle,
        FormulaDetalle sanarDetalle) {

    public EstadisticasHeroe aplicar(ModificadorEstadisticas modificador) {
        return new EstadisticasHeroe(
                poder,
                vida + modificador.deltaVida(),
                defensa + modificador.deltaDefensa(),
                nivel,
                sumarSiPresente(ataqueDetalle, modificador.deltaAtaqueBase()),
                sumarSiPresente(danoDetalle, modificador.deltaDanoBase()),
                sumarDadosSiPresente(sanarDetalle, modificador.dadosSanarExtra()));
    }

    private static FormulaDetalle sumarSiPresente(FormulaDetalle formula, int delta) {
        if (formula == null || delta == 0) {
            return formula;
        }
        return formula.sumarABase(delta);
    }

    private static FormulaDetalle sumarDadosSiPresente(FormulaDetalle formula, int dadosExtra) {
        if (formula == null || dadosExtra == 0) {
            return formula;
        }
        return formula.sumarDados(dadosExtra, formula.caras());
    }
}
