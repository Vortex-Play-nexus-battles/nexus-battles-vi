package nexus.inventario.api;

import nexus.inventario.dominio.EstadisticasHeroe;

public record EstadisticasEquipadasResponse(
        String heroeId,
        int poder,
        int vida,
        int defensa,
        FormulaDetalleResponse ataque,
        FormulaDetalleResponse dano,
        FormulaDetalleResponse sanar) {

    static EstadisticasEquipadasResponse de(String heroeId, EstadisticasHeroe estadisticas) {
        return new EstadisticasEquipadasResponse(
                heroeId,
                estadisticas.poder(),
                estadisticas.vida(),
                estadisticas.defensa(),
                FormulaDetalleResponse.de(estadisticas.ataqueDetalle()),
                FormulaDetalleResponse.de(estadisticas.danoDetalle()),
                FormulaDetalleResponse.de(estadisticas.sanarDetalle()));
    }
}
