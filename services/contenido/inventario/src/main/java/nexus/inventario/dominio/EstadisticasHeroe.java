package nexus.inventario.dominio;

/**
 * Espejo local de Estadisticas (heroes.yaml), resuelto via
 * {@code ResolutorDeEstadisticasHeroe}. poder/vida/defensa son enteros;
 * ataque/dano/sanar son formulas, ausentes segun el prototipo (los sanadores
 * no tienen ataqueDetalle/danoDetalle; los demas no tienen sanarDetalle).
 */
public record EstadisticasHeroe(
        int poder,
        int vida,
        int defensa,
        FormulaDetalle ataqueDetalle,
        FormulaDetalle danoDetalle,
        FormulaDetalle sanarDetalle) {

    public EstadisticasHeroe aplicar(ModificadorEstadisticas modificador) {
        return new EstadisticasHeroe(
                poder,
                vida + modificador.deltaVida(),
                defensa + modificador.deltaDefensa(),
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
