package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Destino del canal CORREO: la notificacion al correo del jugador que exige el
 * 7.3.2 («notificacion automatica al correo electronico» de la suspension,
 * «notificacion formal» del baneo, y la decision de la apelacion).
 *
 * <p>Tres pasos al entregar, sin guardar nada del jugador: la sancion (y la
 * apelacion, si es su resolucion) de este servicio; el correo y el apodo, que
 * ms-identidad da por {@code contacto}; y el envio a correo con la clave de la
 * salida como {@code Idempotency-Key}. El plazo para apelar sale de los
 * limites vigentes (admin-parametros), el mismo numero con el que
 * {@link SancionesService#apelar} decide.
 *
 * <p>El levantamiento no se envia por correo: {@code correo.yaml} 1.4.0 no
 * tiene tipo para el ({@code ADVERTENCIA}, {@code SUSPENSION}, {@code BANEO},
 * {@code APELACION_RESUELTA}) y mandarlo como una apelacion revertida
 * contaria algo que no paso. {@link SalidasDeSancion} no lo encola; si una
 * salida asi llegara, se rechaza a la vista.
 */
public class CorreoDeSancion implements DestinoDeSalidas {

    private static final Logger BITACORA = LoggerFactory.getLogger(CorreoDeSancion.class);

    private final ClienteIdentidad identidad;
    private final ClienteCorreo correo;
    private final SancionRepository sanciones;
    private final ApelacionRepository apelaciones;
    private final LimitesDeSancion limites;

    public CorreoDeSancion(ClienteIdentidad identidad, ClienteCorreo correo, SancionRepository sanciones,
                           ApelacionRepository apelaciones, LimitesDeSancion limites) {
        this.identidad = Objects.requireNonNull(identidad);
        this.correo = Objects.requireNonNull(correo);
        this.sanciones = Objects.requireNonNull(sanciones);
        this.apelaciones = Objects.requireNonNull(apelaciones);
        this.limites = Objects.requireNonNull(limites);
    }

    @Override
    public CanalDeSalida canal() {
        return CanalDeSalida.CORREO;
    }

    @Override
    public Resultado entregar(SalidaPendiente salida) {
        Optional<Contenido> contenido = contenido(salida);
        if (contenido.isEmpty()) {
            BITACORA.error("Correo de la salida {} sin contenido ({}): se deja a la vista", salida.id(),
                    salida.tipo());
            return Resultado.RECHAZADO;
        }
        ClienteIdentidad.Contacto contacto;
        try {
            contacto = identidad.contacto(salida.usuarioId());
        } catch (HttpClientErrorException error) {
            return Respuestas.rechazoOReintento(error);
        }
        if (contacto.email() == null || contacto.email().isBlank()) {
            return Resultado.RECHAZADO;
        }
        Contenido c = contenido.get();
        try {
            correo.enviar(salida.clave(), new ClienteCorreo.CorreoSancion(contacto.email(), contacto.apodo(),
                    c.tipo(), c.motivo(), c.hasta(), c.apelableHasta(), c.resultadoApelacion()));
            return Resultado.ENTREGADO;
        } catch (HttpClientErrorException error) {
            return Respuestas.rechazoOReintento(error);
        }
    }

    /** Lo que dice el correo, compuesto con el estado de la sancion y de la apelacion. */
    record Contenido(String tipo, String motivo, OffsetDateTime hasta, OffsetDateTime apelableHasta,
                     String resultadoApelacion) {
    }

    Optional<Contenido> contenido(SalidaPendiente salida) {
        Optional<Sancion> encontrada = sanciones.findById(salida.sancionId());
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        Sancion sancion = encontrada.get();
        return switch (salida.evento()) {
            case EMISION -> Optional.of(new Contenido(sancion.tipo().name(), sancion.motivo(),
                    sancion.tipo() == Sancion.Tipo.SUSPENSION ? sancion.vigenteHasta() : null,
                    sancion.emitidaEn().plus(limites.plazoDeApelacion()), null));
            case APELACION_RESUELTA -> Optional.ofNullable(salida.apelacionId()).flatMap(apelaciones::findById)
                    .filter(apelacion -> apelacion.estado() != Apelacion.Estado.PENDIENTE)
                    .map(apelacion -> new Contenido("APELACION_RESUELTA", apelacion.decisionMotivo(),
                            apelacion.estado() == Apelacion.Estado.REDUCIDA ? apelacion.nuevaVigencia() : null,
                            null, apelacion.estado().name()));
            case LEVANTAMIENTO -> Optional.empty();
        };
    }
}
