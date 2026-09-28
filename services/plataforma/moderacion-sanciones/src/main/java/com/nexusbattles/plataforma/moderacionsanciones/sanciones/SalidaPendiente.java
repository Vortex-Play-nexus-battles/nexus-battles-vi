package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Una salida de una sancion hacia otro servicio, pendiente o ya entregada —
 * 7.3.2 y HU-NOT-005 (CA-04: si el destino no responde se registra el intento
 * y se reintenta; nunca se pierde en silencio).
 *
 * <p>Hasta B2 se llamaba {@code AvisoPendiente} y solo habia un destino, la
 * bandeja del jugador. Ahora cada cambio de una sancion deja hasta tres
 * salidas en la misma tabla ({@code avisos_pendientes}, V2 + V8), una por
 * {@link CanalDeSalida}: el aviso, la proyeccion sobre la cuenta en
 * ms-identidad y el correo. Se guardan en la misma transaccion que la sancion
 * y las entrega {@link EntregadorDeSalidas}.
 *
 * <p>Que lleva cada una:
 * <ul>
 *   <li>AVISO: {@code tipo}, {@code titulo} y {@code cuerpo} del aviso. El
 *       {@code id} viaja al modulo de notificaciones, que responde 409 si ya lo
 *       tenia: un reintento no duplica el aviso.</li>
 *   <li>PROYECCION y CORREO: solo referencias ({@code sancionId},
 *       {@code apelacionId}) y el evento en {@code tipo}. El contenido se
 *       compone al entregar, con el estado de ese momento: la proyeccion lleva
 *       la restriccion VIGENTE de la cuenta (asi dos proyecciones que llegan
 *       desordenadas no dejan la cuenta en un estado viejo) y el correo pide la
 *       direccion a ms-identidad en ese momento, sin guardar copia.</li>
 * </ul>
 */
@Entity
@Table(name = "avisos_pendientes")
public class SalidaPendiente {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalDeSalida canal;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(length = 200)
    private String titulo;

    @Column(length = 2000)
    private String cuerpo;

    @Column(name = "sancion_id")
    private UUID sancionId;

    @Column(name = "apelacion_id")
    private UUID apelacionId;

    @Column(length = 160, unique = true)
    private String clave;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "entregado_en")
    private OffsetDateTime entregadoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "proximo_intento_en")
    private OffsetDateTime proximoIntentoEn;

    protected SalidaPendiente() {
    }

    private SalidaPendiente(UUID id, CanalDeSalida canal, UUID usuarioId, String tipo, String titulo, String cuerpo,
                            UUID sancionId, UUID apelacionId, String clave, OffsetDateTime creadoEn) {
        this.id = Objects.requireNonNull(id);
        this.canal = Objects.requireNonNull(canal);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tipo = Objects.requireNonNull(tipo);
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.sancionId = sancionId;
        this.apelacionId = apelacionId;
        this.clave = clave;
        this.creadoEn = Objects.requireNonNull(creadoEn);
    }

    /** Aviso a la bandeja del jugador (HU-NOT-005). */
    public static SalidaPendiente aviso(UUID id, UUID usuarioId, String tipo, String titulo, String cuerpo,
                                        UUID sancionId, String clave, OffsetDateTime creadoEn) {
        return new SalidaPendiente(id, CanalDeSalida.AVISO, usuarioId, tipo, Objects.requireNonNull(titulo),
                Objects.requireNonNull(cuerpo), sancionId, null, clave, creadoEn);
    }

    /** Aviso sin sancion de referencia: la forma anterior a B2, que siguen usando las pruebas. */
    public static SalidaPendiente aviso(UUID id, UUID usuarioId, String tipo, String titulo, String cuerpo,
                                        OffsetDateTime creadoEn) {
        return aviso(id, usuarioId, tipo, titulo, cuerpo, null, null, creadoEn);
    }

    /** Proyeccion o correo de un evento de la sancion. */
    public static SalidaPendiente de(CanalDeSalida canal, EventoDeSancion evento, UUID usuarioId, UUID sancionId,
                                     UUID apelacionId, String clave, OffsetDateTime creadoEn) {
        if (canal == CanalDeSalida.AVISO) {
            throw new IllegalArgumentException("un aviso lleva titulo y cuerpo: usa SalidaPendiente.aviso");
        }
        return new SalidaPendiente(UUID.randomUUID(), canal, usuarioId, evento.name(), null, null,
                Objects.requireNonNull(sancionId), apelacionId, Objects.requireNonNull(clave), creadoEn);
    }

    public void entregado(OffsetDateTime ahora) {
        this.entregadoEn = Objects.requireNonNull(ahora);
        this.intentos++;
        this.ultimoError = null;
        this.proximoIntentoEn = null;
    }

    /**
     * Un intento que no llego. La siguiente espera crece con cada fallo
     * ({@code base}, 2x, 4x... hasta {@code maxima}): un destino caido no se
     * reintenta cada vuelta ni tapa a las salidas que vienen detras.
     */
    public void fallo(String motivo, OffsetDateTime ahora, Duration base, Duration maxima) {
        this.intentos++;
        this.ultimoError = motivo == null ? "" : motivo.substring(0, Math.min(motivo.length(), 500));
        this.proximoIntentoEn = Objects.requireNonNull(ahora).plus(espera(intentos, base, maxima));
    }

    /** {@code base * 2^(intentos-1)}, sin pasar de {@code maxima}. */
    static Duration espera(int intentos, Duration base, Duration maxima) {
        Duration espera = base;
        for (int i = 1; i < intentos && espera.compareTo(maxima) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        return espera.compareTo(maxima) > 0 ? maxima : espera;
    }

    /** Evento que origina una proyeccion o un correo (en un aviso, su tipo de notificacion). */
    public EventoDeSancion evento() {
        return EventoDeSancion.valueOf(tipo);
    }

    public UUID id() {
        return id;
    }

    public CanalDeSalida canal() {
        return canal;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public String tipo() {
        return tipo;
    }

    public String titulo() {
        return titulo;
    }

    public String cuerpo() {
        return cuerpo;
    }

    public UUID sancionId() {
        return sancionId;
    }

    public UUID apelacionId() {
        return apelacionId;
    }

    public String clave() {
        return clave;
    }

    public OffsetDateTime creadoEn() {
        return creadoEn;
    }

    public OffsetDateTime entregadoEn() {
        return entregadoEn;
    }

    public int intentos() {
        return intentos;
    }

    public String ultimoError() {
        return ultimoError;
    }

    public OffsetDateTime proximoIntentoEn() {
        return proximoIntentoEn;
    }
}
