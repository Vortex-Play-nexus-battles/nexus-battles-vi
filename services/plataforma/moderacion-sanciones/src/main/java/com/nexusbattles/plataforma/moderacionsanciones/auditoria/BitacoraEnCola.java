package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La bitacora de este servicio: cada accion administrativa que entra por el
 * borde deja un {@link AsientoDeAuditoria} en la cola, dentro de la
 * transaccion de la accion, y una linea en la bitacora JSON (regla 6).
 *
 * <p>Una accion que llega sin {@code X-Real-IP} viene de otro servicio (ver
 * {@link OrigenDeLaAccion}) y la registra quien llamo: aqui no se repite.
 */
public class BitacoraEnCola implements BitacoraDeAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(BitacoraEnCola.class);

    private final AsientoDeAuditoriaRepository asientos;
    private final Clock reloj;

    public BitacoraEnCola(AsientoDeAuditoriaRepository asientos, Clock reloj) {
        this.asientos = Objects.requireNonNull(asientos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public void registrar(TipoDeAccion tipo, String afectado, String valorAnterior, String valorNuevo,
                          String motivo) {
        Optional<String> ip = OrigenDeLaAccion.ipDelBorde();
        if (ip.isEmpty()) {
            BITACORA.debug("Accion {} sobre {} sin X-Real-IP: llego de otro servicio, que la registra",
                    tipo, afectado);
            return;
        }
        Optional<UUID> actor = OrigenDeLaAccion.actor();
        if (actor.isEmpty()) {
            // No deberia ocurrir: toda ruta administrativa exige token de persona.
            BITACORA.error("Accion {} sobre {} desde {} sin una persona identificable en el token: "
                    + "no se puede atribuir en la bitacora central", tipo, afectado, ip.get());
            return;
        }
        AsientoDeAuditoria asiento = AsientoDeAuditoria.nuevo(tipo, actor.get(), afectado, valorAnterior,
                valorNuevo, motivo == null || motivo.isBlank() ? "Sin motivo indicado" : motivo, ip.get(),
                OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC));
        asientos.save(asiento);
        BITACORA.info("AUDITORIA accion={} afectado={} por={} desde={} asiento={}", tipo, asiento.afectado(),
                actor.get(), ip.get(), asiento.id());
    }
}
