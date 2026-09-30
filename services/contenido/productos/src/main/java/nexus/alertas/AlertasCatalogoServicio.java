package nexus.alertas;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.ProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class AlertasCatalogoServicio {

    private final AlertaCatalogoRepository alertas;
    private final ConsultaAlertasJugadorRepository consultas;
    private final ProductoRepository productos;
    private final Clock reloj;

    public AlertasCatalogoServicio(
            AlertaCatalogoRepository alertas,
            ConsultaAlertasJugadorRepository consultas,
            ProductoRepository productos,
            Clock reloj) {
        this.alertas = Objects.requireNonNull(alertas, "Las alertas son obligatorias");
        this.consultas = Objects.requireNonNull(consultas, "Las consultas son obligatorias");
        this.productos = Objects.requireNonNull(productos, "Los productos son obligatorios");
        this.reloj = Objects.requireNonNull(reloj, "El reloj es obligatorio");
    }

    public AlertaCatalogo registrar(TipoCambioCatalogo tipo, Producto producto) {
        Objects.requireNonNull(tipo, "El tipo es obligatorio");
        Objects.requireNonNull(producto, "El producto es obligatorio");
        Instant fecha = reloj.instant();
        return alertas.save(new AlertaCatalogo(
                UUID.randomUUID().toString(),
                producto.id(),
                producto.nombre(),
                tipo,
                descripcion(tipo, producto.nombre()),
                fecha));
    }

    public AlertaCatalogo registrarEstado(
            TipoCambioCatalogo tipo,
            String productoId) {
        Producto producto = productos.findById(exigirTexto(productoId))
                .orElseThrow(ProductoNoEncontradoException::new);
        return registrar(tipo, producto);
    }

    public List<AlertaCatalogo> consultarAlIniciarSesion(String jugadorId) {
        String jugador = exigirTexto(jugadorId);
        Instant desde = consultas.findById(jugador)
                .map(ConsultaAlertasJugador::consultadoHasta)
                .orElse(Instant.EPOCH);
        Instant hasta = reloj.instant();
        List<AlertaCatalogo> pendientes = alertas
                .findByImplementadaEnAfterAndImplementadaEnLessThanEqualOrderByImplementadaEnAsc(
                        desde,
                        hasta);
        if (!pendientes.isEmpty()) {
            Instant ultimoCambioEntregado = pendientes.getLast().implementadaEn();
            consultas.save(new ConsultaAlertasJugador(jugador, ultimoCambioEntregado));
        }
        return List.copyOf(pendientes);
    }

    private String descripcion(TipoCambioCatalogo tipo, String nombre) {
        return switch (tipo) {
            case NUEVO_PRODUCTO -> "Nuevo producto disponible: " + nombre + ".";
            case PRODUCTO_MODIFICADO -> "El producto " + nombre + " fue modificado.";
            case PRODUCTO_SUSPENDIDO -> "El producto " + nombre + " fue suspendido del catalogo.";
            case PRODUCTO_REACTIVADO -> "El producto " + nombre + " volvio a estar disponible.";
            case CAMBIO_BALANCE -> "Se actualizo el balance de " + nombre + ".";
        };
    }

    private String exigirTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El identificador es obligatorio");
        }
        return valor.trim();
    }
}
