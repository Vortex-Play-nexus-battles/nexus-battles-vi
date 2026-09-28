package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Que sale hacia fuera cuando cambia una sancion — 7.3.2, sanciones
 * unificadas (moderacion-sanciones-admin.yaml 1.1.x).
 *
 * <p>Esta es la unica fuente de verdad de las sanciones; los demas servicios
 * se enteran por aqui. Cada evento deja sus salidas en la cola persistente
 * ({@link SalidaPendiente}), dentro de la MISMA transaccion que la sancion:
 * o se guardan las dos cosas o ninguna, y la entrega ocurre despues
 * ({@link EntregadorDeSalidas}). Una sancion nunca deja de registrarse porque
 * identidad o correo esten caidos.
 *
 * <table>
 *   <caption>Salidas por evento</caption>
 *   <tr><th>evento</th><th>aviso</th><th>proyeccion</th><th>correo</th></tr>
 *   <tr><td>emision de advertencia</td><td>si</td><td>no (no restringe)</td><td>ADVERTENCIA</td></tr>
 *   <tr><td>emision de suspension o baneo</td><td>si</td><td>si</td><td>SUSPENSION / BANEO</td></tr>
 *   <tr><td>apelacion revertida o reducida</td><td>si</td><td>si, si la sancion restringia</td>
 *       <td>APELACION_RESUELTA</td></tr>
 *   <tr><td>apelacion mantenida</td><td>si</td><td>no (nada cambia)</td><td>APELACION_RESUELTA</td></tr>
 *   <tr><td>levantamiento</td><td>si</td><td>si, si la sancion restringia</td>
 *       <td>no: correo.yaml 1.4.0 no tiene tipo para el</td></tr>
 * </table>
 *
 * <p>Cada salida lleva una clave estable ({@code sancion-<id>-emision},
 * {@code apelacion-<id>-resolucion}, {@code sancion-<id>-levantamiento}, con el
 * canal detras salvo en el correo), unica en la tabla: el mismo evento no puede
 * encolarse dos veces. La del correo viaja como {@code Idempotency-Key}.
 */
@Component
public class SalidasDeSancion {

    private final SalidaPendienteRepository salidas;

    public SalidasDeSancion(SalidaPendienteRepository salidas) {
        this.salidas = Objects.requireNonNull(salidas);
    }

    /** Emision de una advertencia, una suspension o un baneo. */
    public void emision(Sancion sancion, String tituloDelAviso, String cuerpoDelAviso, OffsetDateTime ahora) {
        String clave = "sancion-" + sancion.id() + "-emision";
        aviso(sancion, "SANCION_" + sancion.tipo().name(), tituloDelAviso, cuerpoDelAviso, clave, ahora);
        if (restringe(sancion)) {
            salidas.save(SalidaPendiente.de(CanalDeSalida.PROYECCION, EventoDeSancion.EMISION, sancion.usuarioId(),
                    sancion.id(), null, clave + "-proyeccion", ahora));
        }
        salidas.save(SalidaPendiente.de(CanalDeSalida.CORREO, EventoDeSancion.EMISION, sancion.usuarioId(),
                sancion.id(), null, clave, ahora));
    }

    /** Decision del panel sobre una apelacion. */
    public void resolucion(Apelacion apelacion, Sancion sancion, String tituloDelAviso, String cuerpoDelAviso,
                           OffsetDateTime ahora) {
        String clave = "apelacion-" + apelacion.id() + "-resolucion";
        aviso(sancion, "APELACION_" + apelacion.estado().name(), tituloDelAviso, cuerpoDelAviso, clave, ahora);
        boolean cambiaElAcceso = apelacion.estado() == Apelacion.Estado.REVERTIDA
                || apelacion.estado() == Apelacion.Estado.REDUCIDA;
        if (cambiaElAcceso && restringe(sancion)) {
            salidas.save(SalidaPendiente.de(CanalDeSalida.PROYECCION, EventoDeSancion.APELACION_RESUELTA,
                    sancion.usuarioId(), sancion.id(), apelacion.id(), clave + "-proyeccion", ahora));
        }
        salidas.save(SalidaPendiente.de(CanalDeSalida.CORREO, EventoDeSancion.APELACION_RESUELTA,
                sancion.usuarioId(), sancion.id(), apelacion.id(), clave, ahora));
    }

    /** Levantamiento de una sancion vigente por un administrador (1.1.0). */
    public void levantamiento(Sancion sancion, String tituloDelAviso, String cuerpoDelAviso, OffsetDateTime ahora) {
        String clave = "sancion-" + sancion.id() + "-levantamiento";
        aviso(sancion, "SANCION_LEVANTADA", tituloDelAviso, cuerpoDelAviso, clave, ahora);
        if (restringe(sancion)) {
            salidas.save(SalidaPendiente.de(CanalDeSalida.PROYECCION, EventoDeSancion.LEVANTAMIENTO,
                    sancion.usuarioId(), sancion.id(), null, clave + "-proyeccion", ahora));
        }
    }

    private void aviso(Sancion sancion, String tipo, String titulo, String cuerpo, String clave,
                       OffsetDateTime ahora) {
        salidas.save(SalidaPendiente.aviso(UUID.randomUUID(), sancion.usuarioId(), tipo, titulo, cuerpo,
                sancion.id(), clave + "-aviso", ahora));
    }

    /** Solo la suspension y el baneo tocan el acceso a la cuenta; la advertencia no (CA-02 de HU-USR-004). */
    private static boolean restringe(Sancion sancion) {
        return sancion.tipo() != Sancion.Tipo.ADVERTENCIA;
    }
}
