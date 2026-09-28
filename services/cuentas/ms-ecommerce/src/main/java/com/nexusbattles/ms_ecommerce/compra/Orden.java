package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.precios.Moneda;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una orden de compra (tabla {@code ordenes}, V4): lo que se compro, a que
 * precio, en que moneda, y hasta donde llego.
 *
 * <p>Las lineas se cargan con la orden (son pocas y siempre se leen juntas),
 * asi la tarea programada puede trabajar con ella fuera de una transaccion.
 *
 * <p>{@link #version} es el bloqueo optimista: si dos procesos escribieran la
 * misma orden a la vez (no deberian, la concesion lo impide), el segundo
 * falla en lugar de pisar al primero.
 */
@Entity
@Table(name = "ordenes")
@Getter
@Setter
public class Orden {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "clave_idempotencia", nullable = false, length = 100)
    private String claveIdempotencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOrden estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Moneda moneda;

    @Column(nullable = false)
    private BigDecimal total;

    /** Pesos por una unidad de {@link #moneda}; null en COP. */
    @Column(name = "tasa_de_cambio")
    private BigDecimal tasaDeCambio;

    @Column(name = "medio_marca", length = 20)
    private String medioMarca;

    @Column(name = "medio_ultimos4", length = 4)
    private String medioUltimos4;

    @Column(name = "referencia_pasarela", length = 64)
    private String referenciaPasarela;

    @Column(length = 300)
    private String motivo;

    /** El trace-id de la peticion que la creo: la tarea programada lo reabre al retomarla. */
    @Column(length = 32)
    private String traza;

    @Column(name = "reservada_en")
    private Instant reservadaEn;

    @Column(name = "entregada_en")
    private Instant entregadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoDelAsiento asiento = EstadoDelAsiento.PENDIENTE;

    @Column(name = "asiento_en")
    private Instant asientoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoDelCorreo correo = EstadoDelCorreo.PENDIENTE;

    @Column(name = "correo_en")
    private Instant correoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "ultimo_error", length = 300)
    private String ultimoError;

    @Column(name = "bloqueada_hasta")
    private Instant bloqueadaHasta;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "cobrada_en")
    private Instant cobradaEn;

    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    /** Envoltorio y no primitivo: null = orden nueva, que se inserta en vez de fusionarse. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("posicion ASC")
    private List<LineaDeOrden> lineas = new ArrayList<>();

    public void agregarLinea(LineaDeOrden linea) {
        linea.setOrden(this);
        lineas.add(linea);
    }

    /** Las unidades de cada producto, como las pide la entrega y como salen del carrito. */
    public Map<String, Integer> unidadesPorProducto() {
        Map<String, Integer> unidades = new LinkedHashMap<>();
        for (LineaDeOrden linea : lineas) {
            unidades.merge(linea.getProductoRef(), linea.getCantidad(), Integer::sum);
        }
        return unidades;
    }
}
