package nexus.alertas;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.ProductoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AlertasCatalogoServicio {

    /** Limites del lote de {@link #consultarCambios} (productos.yaml 1.6.0). */
    static final int LIMITE_MINIMO = 1;
    static final int LIMITE_MAXIMO = 200;

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
        Instant hasta = reloj.instant();
        Optional<ConsultaAlertasJugador> consulta = consultas.findById(jugador);
        if (consulta.isEmpty()) {
            // Primer ingreso: el jugador no tiene "ultima sesion" contra la cual
            // comparar, asi que no hay cambios que avisarle. Volcarle todo el
            // historial del catalogo lo dejaba frente a un dialogo enorme de
            // cambios que nunca vio (y el login espera a que lo cierre). Se
            // guarda la linea base y desde el siguiente ingreso recibe solo lo
            // que cambie despues.
            consultas.save(new ConsultaAlertasJugador(jugador, hasta));
            return List.of();
        }
        List<AlertaCatalogo> pendientes = alertas.buscarImplementadasEntre(
                consulta.get().consultadoHasta(),
                hasta);
        if (!pendientes.isEmpty()) {
            Instant ultimoCambioEntregado = pendientes.getLast().implementadaEn();
            consultas.save(new ConsultaAlertasJugador(jugador, ultimoCambioEntregado));
        }
        return List.copyOf(pendientes);
    }

    /**
     * Cambios del catalogo para otro servicio — HU-NOT-001 (#532),
     * {@code GET /api/v1/productos/alertas/cambios} (productos.yaml 1.6.0).
     *
     * <p><b>Solo lectura.</b> No toca {@code consultas} (el cursor por jugador
     * de {@link #consultarAlIniciarSesion}) ni guarda nada: el punto de
     * lectura lo lleva quien consulta, con el {@code hasta} del lote.
     *
     * <ul>
     *   <li>Sin {@code desde}: linea base. Ninguna alerta, {@code hasta} =
     *       ahora, completo. No vuelca el historial, igual que el primer
     *       ingreso de inicio-sesion.</li>
     *   <li>Con {@code desde}: las alertas en {@code (desde, ahora]}, de la mas
     *       antigua a la mas reciente; {@code hasta} es la ultima entregada o
     *       el propio {@code desde} si no hubo ninguna, para que un cambio que
     *       se registra a la vez que la consulta llegue en la siguiente en vez
     *       de quedar detras del cursor.</li>
     *   <li>Mas de {@code limite}: se corta en el limite sin partir nunca el
     *       grupo con la misma marca de tiempo (el siguiente lote empieza en
     *       {@code (hasta, ...]} y lo dejaria fuera). {@code completo} dice si
     *       quedo algo despues de {@code hasta}.</li>
     * </ul>
     *
     * <p>Ahora va en milisegundos, la precision con la que MongoDB guarda
     * {@code implementadaEn}: asi el {@code hasta} de la linea base es una marca
     * que la base sabe comparar tal cual.
     *
     * @param desde  punto de lectura exclusivo; {@code null} pide la linea base
     * @param limite entre {@value #LIMITE_MINIMO} y {@value #LIMITE_MAXIMO}
     */
    public LoteDeAlertasCatalogo consultarCambios(Instant desde, int limite) {
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "limite debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
        }
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        if (desde == null) {
            return new LoteDeAlertasCatalogo(ahora, true, List.of());
        }

        List<AlertaCatalogo> primeras = enOrden(
                alertas.buscarPrimerasImplementadasEntre(desde, ahora, PageRequest.of(0, limite + 1)));
        if (primeras.size() <= limite) {
            Instant hasta = primeras.isEmpty() ? desde : primeras.getLast().implementadaEn();
            return new LoteDeAlertasCatalogo(hasta, true, primeras);
        }

        // Hay mas que el limite: el corte es la marca del ultimo que cabe, y
        // entra el grupo entero de esa marca, aunque pase del limite.
        Instant corte = primeras.get(limite - 1).implementadaEn();
        List<AlertaCatalogo> lote = enOrden(alertas.buscarImplementadasEntre(desde, corte));
        boolean completo = alertas
                .buscarPrimerasImplementadasEntre(corte, ahora, PageRequest.of(0, 1))
                .isEmpty();
        return new LoteDeAlertasCatalogo(corte, completo, lote);
    }

    /** De la mas antigua a la mas reciente y, dentro de la misma marca, por id: un orden estable. */
    private static List<AlertaCatalogo> enOrden(List<AlertaCatalogo> alertas) {
        return alertas.stream()
                .sorted(Comparator.comparing(AlertaCatalogo::implementadaEn)
                        .thenComparing(AlertaCatalogo::id))
                .toList();
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
