package nexus.inventario.api;

import nexus.inventario.aplicacion.ConsultarHeroeActivo.HeroeActivo;
import nexus.inventario.dominio.EstadisticasHeroe;

public record HeroeActivoResponse(String nombre, int vida, Integer ataque, int defensa, int nivel) {

    static HeroeActivoResponse de(HeroeActivo heroeActivo) {
        EstadisticasHeroe estadisticas = heroeActivo.estadisticas();
        Integer ataque = estadisticas.ataqueDetalle() == null ? null : estadisticas.ataqueDetalle().base();
        return new HeroeActivoResponse(
                heroeActivo.nombre(),
                estadisticas.vida(),
                ataque,
                estadisticas.defensa(),
                estadisticas.nivel());
    }
}
