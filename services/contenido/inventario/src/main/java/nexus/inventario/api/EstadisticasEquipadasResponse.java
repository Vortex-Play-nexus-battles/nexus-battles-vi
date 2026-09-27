package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarEstadisticasEquipadas.EstadisticasEnSuNivel;
import nexus.inventario.dominio.EstadisticasHeroe;

/**
 * @param nivel B4: el nivel del heroe en que se calcularon (inventario 1.5.0)
 */
public record EstadisticasEquipadasResponse(
        String heroeId,
        int nivel,
        int poder,
        int vida,
        int defensa,
        FormulaDetalleResponse ataque,
        FormulaDetalleResponse dano,
        FormulaDetalleResponse sanar) {

    static EstadisticasEquipadasResponse de(String heroeId, EstadisticasEnSuNivel enSuNivel) {
        EstadisticasHeroe estadisticas = enSuNivel.estadisticas();
        return new EstadisticasEquipadasResponse(
                heroeId,
                enSuNivel.nivel(),
                estadisticas.poder(),
                estadisticas.vida(),
                estadisticas.defensa(),
                FormulaDetalleResponse.de(estadisticas.ataqueDetalle()),
                FormulaDetalleResponse.de(estadisticas.danoDetalle()),
                FormulaDetalleResponse.de(estadisticas.sanarDetalle()));
    }
}
