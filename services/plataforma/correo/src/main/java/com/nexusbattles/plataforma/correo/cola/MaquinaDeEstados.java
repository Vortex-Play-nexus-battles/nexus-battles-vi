package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.ClasificadorDeFallos;
import com.nexusbattles.plataforma.correo.envio.DestinoDeEntrega;
import com.nexusbattles.plataforma.correo.envio.ResultadoDeEntrega;
import com.nexusbattles.plataforma.correo.template.Plantilla;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Del resultado de un intento al estado siguiente del envio.
 *
 * <p>Pura: no toca la base ni el reloj. Toda la politica de la cola -cuando
 * se reintenta, cuando se da por perdido, que se borra al terminar- esta
 * aqui y se prueba sin contexto.
 *
 * <table>
 *   <caption>Transiciones</caption>
 *   <tr><th>Resultado</th><th>Estado</th></tr>
 *   <tr><td>entregado al proveedor</td><td>ENVIADO</td></tr>
 *   <tr><td>entregado al buzon de pruebas</td><td>DESVIADO</td></tr>
 *   <tr><td>omitido</td><td>OMITIDO</td></tr>
 *   <tr><td>fallo permanente (5xx de destinatario, correo imposible)</td><td>FALLIDO</td></tr>
 *   <tr><td>fallo transitorio con intentos agotados</td><td>FALLIDO</td></tr>
 *   <tr><td>fallo transitorio</td><td>ERROR_REINTENTABLE, con espera</td></tr>
 * </table>
 *
 * <p>En todo estado terminal los datos se guardan sin sus valores sensibles
 * ({@link Plantilla#sinDatosSensibles}): el codigo de un solo uso deja de
 * existir en cuanto ya no hace falta para reintentar.
 */
public final class MaquinaDeEstados {

    private final PoliticaDeReintentos politica;

    public MaquinaDeEstados(PoliticaDeReintentos politica) {
        this.politica = politica;
    }

    /**
     * @param plantilla la plantilla del envio, vacia si no se reconoce
     * @param datos     los datos que tiene guardados
     * @param intentos  intentos empezados, contando el que acaba de terminar
     * @param resultado lo que paso en este intento
     * @param ahora     el momento del cambio
     */
    public CambioDeEstado tras(
            Optional<Plantilla> plantilla,
            Map<String, Object> datos,
            int intentos,
            ResultadoDeEntrega resultado,
            Instant ahora) {

        return switch (resultado) {
            case ResultadoDeEntrega.Entregado entregado -> new CambioDeEstado(
                    entregado.destino() == DestinoDeEntrega.PROVEEDOR ? EstadoDeEnvio.ENVIADO : EstadoDeEnvio.DESVIADO,
                    ahora,
                    null,
                    entregado.destino(),
                    entregado.identificador().isBlank() ? null : entregado.identificador(),
                    minimizados(plantilla, datos),
                    ahora);
            case ResultadoDeEntrega.Omitido omitido -> terminal(
                    EstadoDeEnvio.OMITIDO, omitido.motivo(), plantilla, datos, ahora);
            case ResultadoDeEntrega.Fallido fallido when fallido.permanente() -> terminal(
                    EstadoDeEnvio.FALLIDO, fallido.motivo(), plantilla, datos, ahora);
            case ResultadoDeEntrega.Fallido fallido when politica.agotado(intentos) -> terminal(
                    EstadoDeEnvio.FALLIDO,
                    "agotados " + intentos + " intentos; el ultimo: " + fallido.motivo(),
                    plantilla,
                    datos,
                    ahora);
            case ResultadoDeEntrega.Fallido fallido -> new CambioDeEstado(
                    EstadoDeEnvio.ERROR_REINTENTABLE,
                    ahora.plus(politica.esperaTras(intentos)),
                    ClasificadorDeFallos.sanear(fallido.motivo()),
                    null,
                    null,
                    null,
                    null);
        };
    }

    private static CambioDeEstado terminal(
            EstadoDeEnvio estado,
            String motivo,
            Optional<Plantilla> plantilla,
            Map<String, Object> datos,
            Instant ahora) {
        return new CambioDeEstado(
                estado, ahora, ClasificadorDeFallos.sanear(motivo), null, null, minimizados(plantilla, datos), null);
    }

    /**
     * Sin plantilla reconocida no se sabe que es sensible: no se guarda nada.
     */
    private static Map<String, Object> minimizados(Optional<Plantilla> plantilla, Map<String, Object> datos) {
        return plantilla.map(p -> p.sinDatosSensibles(datos)).orElseGet(Map::of);
    }
}
