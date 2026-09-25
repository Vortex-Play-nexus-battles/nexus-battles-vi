package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.web.client.HttpClientErrorException;

import java.util.Objects;
import java.util.Optional;

/**
 * Destino del canal PROYECCION: el estado de acceso de la cuenta en
 * ms-identidad (moderacion-sanciones-admin.yaml 1.1.x; ms-identidad-admin.yaml,
 * {@code estado-sancion}).
 *
 * <p><b>Se proyecta el estado de AHORA, no el del evento.</b> La salida solo
 * dice que algo cambio en las sanciones de un jugador; al entregarla se
 * calcula su restriccion vigente con la misma regla que
 * {@code GET /sanciones/usuarios/{uid}/activa} ({@link SancionesService#activaDe}):
 * el baneo antes que la suspension, y de las suspensiones la que mas dura; sin
 * ninguna, {@code ACTIVO}. Asi, si identidad estuvo caida y dos salidas del
 * mismo jugador (una suspension y su levantamiento) se entregan tarde o
 * desordenadas, la cuenta acaba en el estado correcto: cada entrega manda el
 * estado vigente, y la ultima gana con el valor bueno. Una suspension que vence
 * sola no necesita salida: ms-identidad la expira por su {@code hasta}.
 */
public class ProyeccionEnIdentidad implements DestinoDeSalidas {

    private final ClienteIdentidad identidad;
    private final SancionesService sanciones;
    private final SancionRepository repositorio;

    public ProyeccionEnIdentidad(ClienteIdentidad identidad, SancionesService sanciones,
                                 SancionRepository repositorio) {
        this.identidad = Objects.requireNonNull(identidad);
        this.sanciones = Objects.requireNonNull(sanciones);
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    @Override
    public CanalDeSalida canal() {
        return CanalDeSalida.PROYECCION;
    }

    @Override
    public Resultado entregar(SalidaPendiente salida) {
        ClienteIdentidad.ProyeccionDeSancion proyeccion = proyeccionVigente(salida);
        try {
            identidad.proyectar(salida.usuarioId(), proyeccion);
            return Resultado.ENTREGADO;
        } catch (HttpClientErrorException error) {
            return Respuestas.rechazoOReintento(error);
        }
    }

    /** El estado de acceso que le toca hoy al jugador de la salida. */
    ClienteIdentidad.ProyeccionDeSancion proyeccionVigente(SalidaPendiente salida) {
        Optional<Sancion> vigente = sanciones.activaDe(salida.usuarioId());
        if (vigente.isPresent()) {
            Sancion sancion = vigente.get();
            boolean baneo = sancion.tipo() == Sancion.Tipo.BANEO;
            return new ClienteIdentidad.ProyeccionDeSancion(baneo ? "BANEADO" : "SUSPENDIDO",
                    baneo ? null : sancion.vigenteHasta(), sancion.id(), sancion.motivo());
        }
        String motivo = repositorio.findById(salida.sancionId()).map(Sancion::motivoReversion).orElse(null);
        return new ClienteIdentidad.ProyeccionDeSancion("ACTIVO", null, salida.sancionId(), motivo);
    }
}
