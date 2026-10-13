package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.catalogo.ReservasDeTiraje;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import com.nexusbattles.ms_ecommerce.integracion.correo.ClienteDeCorreo;
import com.nexusbattles.ms_ecommerce.integracion.finanzas.ClienteDeCreditos;
import com.nexusbattles.ms_ecommerce.integracion.finanzas.ClienteDeFinanzas;
import com.nexusbattles.ms_ecommerce.integracion.identidad.ClienteDeIdentidad;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ClienteDeInventario;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ProductosPropios;
import com.nexusbattles.ms_ecommerce.compra.pago.PasarelaSimulada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Lo que pasa despues de cobrar (B5): reservar el tiraje, entregar, registrar
 * el asiento y enviar el correo, o compensar si no se puede entregar.
 *
 * <p><b>Cada paso es idempotente y deja su marca antes del siguiente.</b> La
 * reserva de cada unidad lleva la clave {@code orden-{id}-l{linea}-u{unidad}}
 * y su progreso se anota por linea; la entrega, {@code orden-{id}}; el
 * asiento, el {@code refId} = id de la orden; el correo, {@code compra-{id}}.
 * Repetir un paso —porque la respuesta no llego, porque el servicio cayo,
 * porque el proceso se reinicio— devuelve lo mismo sin hacerlo dos veces. Por
 * eso retomar una orden a medias es simplemente volver a llamar a
 * {@link #avanzar}: nunca cobra dos veces (el cobro ya no esta aqui) ni
 * entrega dos veces.
 *
 * <p><b>Averia o decision.</b> Un servicio que no responde
 * ({@link ServicioNoDisponibleException}) deja la orden en su estado y le
 * programa un reintento, con espera creciente. Una negativa del otro servicio
 * (el catalogo no reserva porque se agoto o se suspendio; el inventario no
 * entrega un producto suspendido) no se arregla reintentando: la orden se
 * compensa —reembolso simulado— y queda REEMBOLSADA con el motivo.
 *
 * <p><b>Por que el asiento va despues de entregar.</b> El libro de moneda real
 * de ms-finanzas (transacciones.yaml 1.0.0) no tiene reembolsos: si el asiento
 * APROBADO se escribiera antes de saber si se puede entregar, una orden
 * compensada dejaria un cobro sin reverso. Despues de entregar, la compra ya
 * no se puede compensar. Un rechazo de la pasarela si deja asiento (RECHAZADO):
 * es un intento de cobro, y ese libro los registra todos.
 *
 * <p>Quien llama tiene que tener la concesion de la orden
 * ({@link OrdenRepository#tomar}); cada escritura de aqui la renueva.
 *
 * <p><b>D-44 — pagada con creditos.</b> Los mismos pasos. Una orden con
 * creditos nace con el asiento NO_APLICA (el debito ya esta en el libro de
 * creditos de ms-finanzas) y el correo OMITIDO, asi que ENTREGADA pasa a
 * COMPLETA sin mas; y compensar es devolver los creditos
 * ({@code POST /creditos/reversar} con el mismo {@code refId}), que espera y
 * reintenta si ms-finanzas no responde.
 */
@Component
public class ProcesadorDeOrdenes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeOrdenes.class);

    /** Lo que se ve en el historial de transacciones y en el correo. */
    static final String CONCEPTO = "Compra en la tienda";
    private static final int LARGO_DEL_CONCEPTO = 128;
    private static final int LARGO_DEL_ERROR = 300;

    /** Pasos por llamada: el camino mas largo (COBRADA → ENTREGADA → COMPLETA, o compensar) cabe de sobra. */
    private static final int MAXIMO_DE_PASOS = 8;

    private final OrdenRepository ordenes;
    private final ReservasDeTiraje reservas;
    private final ClienteDeInventario inventario;
    private final ClienteDeFinanzas finanzas;
    private final ClienteDeIdentidad identidad;
    private final ClienteDeCorreo correo;
    private final PasarelaSimulada pasarela;
    private final PropiedadesDeLaTienda propiedades;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final ProductosPropios propios;
    private final ClienteDeCreditos creditos;

    /**
     * @param propios  la copia de lo que tiene cada jugador (marca «propio» de
     *     la vitrina y RF-CAR-004): una compra entregada la olvida
     * @param creditos el libro de creditos de ms-finanzas: devolver una compra
     *     pagada con creditos que no se pudo entregar (D-44)
     */
    public ProcesadorDeOrdenes(OrdenRepository ordenes, ReservasDeTiraje reservas, ClienteDeInventario inventario,
                               ClienteDeFinanzas finanzas, ClienteDeIdentidad identidad, ClienteDeCorreo correo,
                               PasarelaSimulada pasarela, PropiedadesDeLaTienda propiedades, Clock reloj,
                               PlatformTransactionManager gestorDeTransacciones, ProductosPropios propios,
                               ClienteDeCreditos creditos) {
        this.ordenes = ordenes;
        this.reservas = reservas;
        this.inventario = inventario;
        this.finanzas = finanzas;
        this.identidad = identidad;
        this.correo = correo;
        this.pasarela = pasarela;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestorDeTransacciones);
        this.propios = Objects.requireNonNull(propios);
        this.creditos = Objects.requireNonNull(creditos);
    }

    /**
     * Lleva la orden tan lejos como se pueda ahora: hasta un estado final, o
     * hasta el primer paso que tiene que esperar a un reintento.
     *
     * @return la orden como quedo
     */
    public Orden avanzar(UUID id) {
        for (int paso = 0; paso < MAXIMO_DE_PASOS; paso++) {
            Orden orden = cargar(id);
            boolean seguir = switch (orden.getEstado()) {
                case COBRADA -> pasoCobrada(orden);
                case ENTREGADA -> pasoEntregada(orden);
                case COMPENSACION_PENDIENTE -> pasoCompensar(orden);
                case RECHAZADA -> pasoRechazada(orden);
                case PENDIENTE, COMPLETA, REEMBOLSADA -> false;
            };
            if (!seguir) {
                break;
            }
        }
        return cargar(id);
    }

    // ------------------------------------------------------------ COBRADA

    private boolean pasoCobrada(Orden orden) {
        if (orden.getReservadaEn() == null) {
            try {
                String rechazo = reservarTiraje(orden);
                if (rechazo != null) {
                    compensar(orden.getId(), rechazo);
                    return true;
                }
            } catch (ServicioNoDisponibleException averia) {
                programarReintento(orden.getId(), averia);
                return false;
            }
            actualizar(orden.getId(), o -> o.setReservadaEn(reloj.instant()));
        }
        try {
            ClienteDeInventario.ResultadoDeEntrega entrega = inventario.entregar(orden.getUsuarioId(),
                    orden.getId().toString(), unidadesDe(orden), "orden-" + orden.getId());
            if (entrega == ClienteDeInventario.ResultadoDeEntrega.RECHAZADA) {
                compensar(orden.getId(), "El inventario no aceptó la entrega: un producto de la compra dejó de "
                        + "estar disponible.");
                return true;
            }
        } catch (ServicioNoDisponibleException averia) {
            programarReintento(orden.getId(), averia);
            return false;
        }
        actualizar(orden.getId(), o -> {
            o.setEstado(EstadoOrden.ENTREGADA);
            o.setEntregadaEn(reloj.instant());
        });
        // Lo comprado ya es suyo: la vitrina no puede seguir 30 s sin marcarlo
        // ni ofreciendo «Añadir» de lo que acaba de pagar (RF-CAR-004).
        propios.olvidar(orden.getUsuarioId());
        log.info("Orden {}: entregada al inventario", orden.getId());
        return true;
    }

    /**
     * Reserva una unidad de tiraje por cada unidad comprada, desde donde se
     * quedo la ultima vez.
     *
     * @return null si todo quedo reservado; el motivo, si el catalogo se nego
     * @throws ServicioNoDisponibleException si el catalogo no respondio
     */
    private String reservarTiraje(Orden orden) {
        for (LineaDeOrden linea : orden.getLineas()) {
            for (int unidad = linea.getUnidadesReservadas() + 1; unidad <= linea.getCantidad(); unidad++) {
                String clave = "orden-" + orden.getId() + "-l" + linea.getPosicion() + "-u" + unidad;
                ReservasDeTiraje.Resultado resultado = reservas.reservarUnaUnidad(linea.getProductoRef(), clave);
                if (!resultado.aceptada()) {
                    log.warn("Orden {}: el catalogo no reservo {} ({})", orden.getId(), linea.getProductoRef(),
                            resultado);
                    return motivoDeReserva(resultado, linea.getNombre());
                }
                ordenes.anotarReserva(linea.getId(), unidad);
                renovarConcesion(orden.getId());
            }
        }
        return null;
    }

    private static String motivoDeReserva(ReservasDeTiraje.Resultado resultado, String nombre) {
        return switch (resultado) {
            case AGOTADO -> "«" + nombre + "» se agotó mientras pagabas.";
            case SUSPENDIDO -> "«" + nombre + "» dejó de estar a la venta mientras pagabas.";
            case INEXISTENTE -> "«" + nombre + "» ya no está en el catálogo.";
            case RECHAZADA, ACEPTADA -> "El catálogo no aceptó la reserva de «" + nombre + "».";
        };
    }

    // ---------------------------------------------------------- ENTREGADA

    private boolean pasoEntregada(Orden orden) {
        ServicioNoDisponibleException averia = null;
        if (orden.getAsiento() == EstadoDelAsiento.PENDIENTE) {
            try {
                registrarAsiento(orden, ClienteDeFinanzas.Resultado.APROBADO);
            } catch (ServicioNoDisponibleException caido) {
                averia = caido;
            }
        }
        if (orden.getCorreo() == EstadoDelCorreo.PENDIENTE) {
            try {
                EstadoDelCorreo enviado = enviarCorreo(orden);
                actualizar(orden.getId(), o -> {
                    o.setCorreo(enviado);
                    o.setCorreoEn(reloj.instant());
                });
            } catch (ServicioNoDisponibleException caido) {
                averia = averia == null ? caido : averia;
            }
        }
        if (averia != null) {
            programarReintento(orden.getId(), averia);
            return false;
        }
        actualizar(orden.getId(), o -> o.setEstado(EstadoOrden.COMPLETA));
        log.info("Orden {}: completa", orden.getId());
        return false;
    }

    private void registrarAsiento(Orden orden, ClienteDeFinanzas.Resultado resultado) {
        finanzas.registrar(orden.getId().toString(), orden.getUsuarioId(), orden.getTotal(), orden.getMoneda().name(),
                concepto(orden), resultado, orden.getReferenciaPasarela());
        actualizar(orden.getId(), o -> {
            o.setAsiento(EstadoDelAsiento.REGISTRADO);
            o.setAsientoEn(reloj.instant());
        });
    }

    private EstadoDelCorreo enviarCorreo(Orden orden) {
        if (!propiedades.correo().habilitado()) {
            return EstadoDelCorreo.OMITIDO;
        }
        ClienteDeIdentidad.Contacto contacto = identidad.contacto(orden.getUsuarioId()).orElse(null);
        if (contacto == null) {
            log.warn("Orden {}: ms-identidad no tiene la cuenta del comprador; no se envia la confirmacion",
                    orden.getId());
            return EstadoDelCorreo.OMITIDO;
        }
        Instant cobrada = Objects.requireNonNullElse(orden.getCobradaEn(), orden.getCreadaEn());
        ClienteDeCorreo.ConfirmacionDeCompra confirmacion = new ClienteDeCorreo.ConfirmacionDeCompra(
                contacto.email(),
                contacto.apodo() == null || contacto.apodo().isBlank() ? "jugador" : contacto.apodo(),
                orden.getTotal(),
                orden.getMoneda().name(),
                concepto(orden),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(cobrada.atZone(propiedades.zonaHoraria())),
                orden.getLineas().stream()
                        .map(linea -> new ClienteDeCorreo.Linea(linea.getNombre(), linea.getCantidad(),
                                linea.getPrecioUnitario(), linea.getSubtotal()))
                        .toList(),
                orden.getId().toString());
        ClienteDeCorreo.Resultado resultado = correo.enviarConfirmacionDeCompra(confirmacion, "compra-" + orden.getId());
        if (resultado == ClienteDeCorreo.Resultado.RECHAZADO) {
            log.warn("Orden {}: el servicio de correo rechazo la confirmacion", orden.getId());
            return EstadoDelCorreo.RECHAZADO;
        }
        return EstadoDelCorreo.ENVIADO;
    }

    // ------------------------------------------------- compensacion y rechazo

    private void compensar(UUID id, String motivo) {
        actualizar(id, o -> {
            o.setEstado(EstadoOrden.COMPENSACION_PENDIENTE);
            o.setMotivo(recortar(motivo, LARGO_DEL_ERROR));
            o.setAsiento(EstadoDelAsiento.NO_APLICA);
            o.setCorreo(EstadoDelCorreo.OMITIDO);
        });
        log.warn("Orden {}: se compensa ({})", id, motivo);
    }

    private boolean pasoCompensar(Orden orden) {
        if (orden.pagadaConCreditos()) {
            return devolverCreditos(orden);
        }
        String reembolso = pasarela.reembolsar(orden.getReferenciaPasarela(), orden.getTotal(), orden.getMoneda());
        actualizar(orden.getId(), o -> o.setEstado(EstadoOrden.REEMBOLSADA));
        log.info("Orden {}: reembolsada ({})", orden.getId(), reembolso);
        return false;
    }

    /**
     * D-44: devuelve los creditos de una compra que no se pudo entregar, con el
     * mismo {@code refId} del cobro. Repetirlo no devuelve dos veces
     * (YA_REVERSADO); si ms-finanzas no responde, la orden sigue por compensar
     * y se reintenta.
     */
    private boolean devolverCreditos(Orden orden) {
        ClienteDeCreditos.Devolucion devolucion;
        try {
            devolucion = creditos.reversar(orden.referenciaDeCreditos(),
                    Objects.requireNonNullElse(orden.getMotivo(), "Compra no entregada"));
        } catch (ServicioNoDisponibleException averia) {
            programarReintento(orden.getId(), averia);
            return false;
        }
        if (devolucion == ClienteDeCreditos.Devolucion.NADA_QUE_DEVOLVER) {
            log.warn("Orden {}: ms-finanzas no tiene el cobro {}; no habia creditos que devolver", orden.getId(),
                    orden.referenciaDeCreditos());
        }
        actualizar(orden.getId(), o -> o.setEstado(EstadoOrden.REEMBOLSADA));
        log.info("Orden {}: creditos devueltos ({})", orden.getId(), devolucion);
        return false;
    }

    private boolean pasoRechazada(Orden orden) {
        if (orden.getAsiento() != EstadoDelAsiento.PENDIENTE) {
            return false;
        }
        try {
            registrarAsiento(orden, ClienteDeFinanzas.Resultado.RECHAZADO);
        } catch (ServicioNoDisponibleException averia) {
            programarReintento(orden.getId(), averia);
        }
        return false;
    }

    // ------------------------------------------------------------ apoyo

    private void programarReintento(UUID id, ServicioNoDisponibleException averia) {
        Orden actualizada = actualizar(id, o -> {
            o.setIntentos(o.getIntentos() + 1);
            o.setProximoIntentoEn(reloj.instant().plus(espera(o.getIntentos())));
            o.setUltimoError(recortar(averia.getMessage(), LARGO_DEL_ERROR));
        });
        log.warn("Orden {} en {}: {} no respondio; reintento {} a las {}", id, actualizada.getEstado(),
                averia.servicio(), actualizada.getIntentos(), actualizada.getProximoIntentoEn());
    }

    /** Espera antes del intento n: la inicial, duplicada en cada fallo, hasta el tope. */
    Duration espera(int intento) {
        PropiedadesDeLaTienda.Ordenes config = propiedades.ordenes();
        Duration espera = config.reintentoInicial();
        for (int i = 1; i < intento && espera.compareTo(config.reintentoMaximo()) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        return espera.compareTo(config.reintentoMaximo()) > 0 ? config.reintentoMaximo() : espera;
    }

    /** Lee la orden, le aplica el cambio y la guarda en una transaccion corta; renueva la concesion. */
    Orden actualizar(UUID id, Consumer<Orden> cambio) {
        return Objects.requireNonNull(transaccion.execute(estado -> {
            Orden orden = cargar(id);
            cambio.accept(orden);
            Instant ahora = reloj.instant();
            orden.setActualizadaEn(ahora);
            if (orden.getBloqueadaHasta() != null) {
                orden.setBloqueadaHasta(ahora.plus(propiedades.ordenes().concesion()));
            }
            return ordenes.save(orden);
        }));
    }

    private void renovarConcesion(UUID id) {
        ordenes.renovar(id, reloj.instant().plus(propiedades.ordenes().concesion()));
    }

    private Orden cargar(UUID id) {
        return ordenes.findById(id).orElseThrow(() -> new IllegalStateException("La orden " + id + " no existe"));
    }

    private static List<ClienteDeInventario.Unidades> unidadesDe(Orden orden) {
        return orden.unidadesPorProducto().entrySet().stream()
                .map(entrada -> new ClienteDeInventario.Unidades(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    /** «Compra en la tienda: Espada de fuego x2, Escudo de roble», recortado a lo que admite ms-finanzas. */
    static String concepto(Orden orden) {
        String detalle = orden.getLineas().stream()
                .map(linea -> linea.getCantidad() > 1 ? linea.getNombre() + " x" + linea.getCantidad()
                        : linea.getNombre())
                .collect(Collectors.joining(", "));
        return recortar(detalle.isBlank() ? CONCEPTO : CONCEPTO + ": " + detalle, LARGO_DEL_CONCEPTO);
    }

    private static String recortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
