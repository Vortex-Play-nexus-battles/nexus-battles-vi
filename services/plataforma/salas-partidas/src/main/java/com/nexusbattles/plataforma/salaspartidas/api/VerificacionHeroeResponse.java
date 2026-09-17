package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.VerificacionDeIngreso;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;

/**
 * Cuerpo de {@code GET /salas/{idSala}/verificacion-heroe} — HU-SAL-003.
 *
 * <p>Forma exacta del esquema {@code VerificacionHeroe} de
 * {@code contracts/openapi/salas-partidas.yaml}. Los campos anulables se
 * serializan aunque valgan {@code null}: el contrato los declara
 * {@code [tipo, 'null']}, no opcionales, y {@code validacion-heroe.js} decide
 * la variante leyendolos. Omitirlos obligaria a la interfaz a distinguir entre
 * «no aplica» y «no vino», que son cosas distintas.
 *
 * @param resultado           variante del dialogo
 * @param puedeIngresar       si el boton Entrar queda habilitado
 * @param heroe               heroe implicado, o {@code null}
 * @param salaQueLoOcupa      actividad que retiene al heroe, o {@code null}
 * @param creditosDisponibles saldo del jugador, o {@code null} si no se conoce
 * @param creditosRequeridos  recompensa comprometida de la sala
 */
record VerificacionHeroeResponse(
        ResultadoVerificacion resultado,
        boolean puedeIngresar,
        HeroeResponse heroe,
        String salaQueLoOcupa,
        Integer creditosDisponibles,
        Integer creditosRequeridos) {

    static VerificacionHeroeResponse desde(VerificacionDeIngreso verificacion) {
        return new VerificacionHeroeResponse(
                verificacion.resultado(),
                verificacion.puedeIngresar(),
                HeroeResponse.desde(verificacion.heroe()),
                verificacion.salaQueLoOcupa(),
                verificacion.creditosDisponibles(),
                verificacion.creditosRequeridos());
    }

    /**
     * Espejo de {@code HeroeEnPartida}.
     *
     * <p>{@code vidaActual} y {@code vidaMaxima} viajan siempre juntas porque la
     * barra de vida muestra el numero ademas del color (RF-JUE-009): el color
     * nunca es el unico indicador.
     */
    record HeroeResponse(
            String id,
            String nombre,
            String retratoUrl,
            Integer nivel,
            int vidaActual,
            int vidaMaxima) {

        static HeroeResponse desde(HeroeDeCombate heroe) {
            if (heroe == null) {
                return null;
            }
            return new HeroeResponse(heroe.id(), heroe.nombre(), heroe.retratoUrl(),
                    heroe.nivel(), heroe.vidaActual(), heroe.vidaMaxima());
        }
    }
}
