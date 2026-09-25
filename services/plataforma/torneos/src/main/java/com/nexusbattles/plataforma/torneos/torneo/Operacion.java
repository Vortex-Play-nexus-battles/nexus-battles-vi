package com.nexusbattles.plataforma.torneos.torneo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Algo que hay que hacer para un participante fuera de esta base de datos
 * (torneos.yaml 1.2.0): cobrar o devolver su inscripcion, entregarle su premio
 * o avisarle de un hito.
 *
 * <p><b>Por que existe.</b> Hasta la 1.1.0, iniciar y cancelar llamaban al
 * libro de creditos en un bucle dentro de la transaccion que cambiaba el
 * estado del torneo. Una caida a mitad deshacia el cambio de estado pero no
 * los cobros ya hechos, y cancelar despues «liberaba» reservas consumidas sin
 * devolver nada. Ahora el cambio de estado y la operacion pendiente se guardan
 * juntos, y la llamada al proveedor ocurre despues, fuera de la transaccion.
 *
 * <p><b>Idempotencia.</b> {@link #clave()} es estable
 * ({@code torneo-<id>-jugador-<uid>-<que>}) y unica en la base: una segunda
 * ejecucion del mismo caso de uso no puede crear una segunda operacion para el
 * mismo participante. Al proveedor se le manda esa misma clave cuando la admite
 * ({@code refId} del libro, {@code Idempotency-Key} de inventario y correo,
 * {@code id} del aviso); cobrar y liberar una reserva ya son idempotentes por
 * su {@code reservaId}.
 *
 * <p><b>Ciclo de vida.</b> PENDIENTE → EN_CURSO (reclamada por quien la
 * ejecuta, con un plazo) → HECHA, o REINTENTABLE (vuelve a la cola con espera
 * creciente), o FALLIDA (el proveedor la rechazo o se agotaron los intentos;
 * la reabre un administrador), EXCLUIDA (premio de un sancionado) u OMITIDA
 * (correo sin configurar o sin contacto). Si quien la ejecuta muere a mitad, el
 * plazo vence y otra ejecucion la retoma: por eso la llamada al proveedor
 * tiene que ser idempotente.
 */
@Entity
@Table(name = "operaciones")
public class Operacion {

    public enum Tipo {
        /** Consumir la reserva de la inscripcion al iniciar el torneo. */
        COBRO_INSCRIPCION,
        /** Liberar (o, si ya estaba cobrada, acreditar) la inscripcion al cancelar. */
        DEVOLUCION_INSCRIPCION,
        /** Creditos y epica del integrante del equipo campeon (RF-TOR-007). */
        PREMIO,
        /** Aviso en la bandeja de notificaciones. */
        AVISO,
        /** Correo de un hito (correo.yaml 1.5.0). */
        CORREO
    }

    public enum Estado {
        PENDIENTE,
        EN_CURSO,
        REINTENTABLE,
        HECHA,
        FALLIDA,
        EXCLUIDA,
        OMITIDA;

        /** Todavia tiene que pasar algo con ella. */
        public boolean abierta() {
            return this == PENDIENTE || this == EN_CURSO || this == REINTENTABLE;
        }
    }

    /** Resultado de la verificacion de sancion antes de premiar (CA-02 de HU-TOR-007). */
    public enum Sancion { SIN_SANCION, SANCIONADO }

    @Id
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "equipo_id")
    private UUID equipoId;

    @Column(name = "jugador_uid", nullable = false)
    private UUID jugadorUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    @Column(nullable = false, length = 150)
    private String clave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "reserva_id")
    private UUID reservaId;

    private Integer monto;

    @Column(name = "producto_id", length = 100)
    private String productoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sancion_verificada", length = 20)
    private Sancion sancionVerificada;

    @Column(name = "creditos_entregados", nullable = false)
    private boolean creditosEntregados;

    @Column(name = "epica_entregada", nullable = false)
    private boolean epicaEntregada;

    @Column(length = 200)
    private String titulo;

    @Column(length = 1000)
    private String cuerpo;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento", nullable = false)
    private OffsetDateTime proximoIntento;

    @Column(name = "bloqueada_hasta")
    private OffsetDateTime bloqueadaHasta;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(length = 200)
    private String resultado;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn;

    protected Operacion() {
    }

    private Operacion(UUID torneoId, UUID equipoId, UUID jugadorUid, Tipo tipo, String sufijo, OffsetDateTime ahora) {
        this.id = UUID.randomUUID();
        this.torneoId = Objects.requireNonNull(torneoId);
        this.equipoId = equipoId;
        this.jugadorUid = Objects.requireNonNull(jugadorUid);
        this.tipo = Objects.requireNonNull(tipo);
        this.clave = clave(torneoId, jugadorUid, sufijo);
        this.estado = Estado.PENDIENTE;
        this.proximoIntento = Objects.requireNonNull(ahora);
        this.creadaEn = ahora;
        this.actualizadaEn = ahora;
    }

    /**
     * La clave estable de un participante en un torneo.
     *
     * <p>No lleva el id del equipo a proposito: el libro guarda la clave en 128
     * caracteres e inventario acepta 100, y con tres UUID la clave pasaba de
     * 138. Un jugador esta en un solo equipo por torneo (se comprueba con la
     * fila del torneo bloqueada), asi que torneo + jugador ya la hace unica; el
     * equipo queda en {@link #equipoId()}.
     */
    public static String clave(UUID torneoId, UUID jugadorUid, String sufijo) {
        return "torneo-" + torneoId + "-jugador-" + jugadorUid + "-" + sufijo;
    }

    public static Operacion cobro(UUID torneoId, UUID equipoId, UUID pagador, UUID reservaId, int monto,
                                  OffsetDateTime ahora) {
        Operacion op = new Operacion(torneoId, equipoId, pagador, Tipo.COBRO_INSCRIPCION, "cobro", ahora);
        op.reservaId = Objects.requireNonNull(reservaId);
        op.monto = monto;
        return op;
    }

    public static Operacion devolucion(UUID torneoId, UUID equipoId, UUID pagador, UUID reservaId, int monto,
                                       OffsetDateTime ahora) {
        Operacion op = new Operacion(torneoId, equipoId, pagador, Tipo.DEVOLUCION_INSCRIPCION, "devolucion", ahora);
        op.reservaId = Objects.requireNonNull(reservaId);
        op.monto = monto;
        return op;
    }

    public static Operacion premio(UUID torneoId, UUID equipoId, UUID integrante, int creditos, String epicaProductoId,
                                   OffsetDateTime ahora) {
        Operacion op = new Operacion(torneoId, equipoId, integrante, Tipo.PREMIO, "premio", ahora);
        op.monto = creditos;
        op.productoId = epicaProductoId == null || epicaProductoId.isBlank() ? null : epicaProductoId.strip();
        return op;
    }

    public static Operacion aviso(UUID torneoId, UUID equipoId, UUID destinatario, String hito, String titulo,
                                  String cuerpo, OffsetDateTime ahora) {
        Operacion op = new Operacion(torneoId, equipoId, destinatario, Tipo.AVISO, "aviso-" + hito, ahora);
        op.titulo = recortar(titulo, 200);
        op.cuerpo = recortar(cuerpo, 1000);
        return op;
    }

    public static Operacion correo(UUID torneoId, UUID equipoId, UUID destinatario, String hito, String asunto,
                                   String mensaje, OffsetDateTime ahora) {
        Operacion op = new Operacion(torneoId, equipoId, destinatario, Tipo.CORREO, "correo-" + hito, ahora);
        op.titulo = recortar(asunto, 200);
        op.cuerpo = recortar(mensaje, 1000);
        return op;
    }

    /** Clave de la epica en inventario (Idempotency-Key, 100 caracteres como mucho). */
    public String claveDeEpica() {
        return clave(torneoId, jugadorUid, "epica");
    }

    // ------------------------------------------------------------ transiciones

    /** Sale bien: no se vuelve a intentar. */
    public void hecha(String resultado, OffsetDateTime ahora) {
        this.estado = Estado.HECHA;
        this.resultado = recortar(resultado, 200);
        this.ultimoError = null;
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    /** Fallo pasajero: vuelve a la cola para {@code siguiente}. */
    public void reintentable(String error, OffsetDateTime siguiente, OffsetDateTime ahora) {
        this.estado = Estado.REINTENTABLE;
        this.ultimoError = recortar(error, 500);
        this.proximoIntento = siguiente;
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    /** El proveedor la rechazo o se agotaron los intentos: la reabre un administrador. */
    public void fallida(String error, OffsetDateTime ahora) {
        this.estado = Estado.FALLIDA;
        this.ultimoError = recortar(error, 500);
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    /** Premio de un integrante sancionado: no se entrega (CA-02 de HU-TOR-007). */
    public void excluida(String motivo, OffsetDateTime ahora) {
        this.estado = Estado.EXCLUIDA;
        this.resultado = recortar(motivo, 200);
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    /** No aplica (correo sin configurar, jugador sin contacto): no es un error. */
    public void omitida(String motivo, OffsetDateTime ahora) {
        this.estado = Estado.OMITIDA;
        this.resultado = recortar(motivo, 200);
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    /** Un administrador la vuelve a poner en la cola con la misma clave. */
    public void reabrir(OffsetDateTime ahora) {
        if (estado != Estado.FALLIDA) {
            return;
        }
        this.estado = Estado.PENDIENTE;
        this.intentos = 0;
        this.proximoIntento = ahora;
        this.bloqueadaHasta = null;
        this.actualizadaEn = ahora;
    }

    public void sancionVerificada(Sancion sancion, OffsetDateTime ahora) {
        this.sancionVerificada = sancion;
        this.actualizadaEn = ahora;
    }

    public void creditosEntregados(OffsetDateTime ahora) {
        this.creditosEntregados = true;
        this.actualizadaEn = ahora;
    }

    public void epicaEntregada(OffsetDateTime ahora) {
        this.epicaEntregada = true;
        this.actualizadaEn = ahora;
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    public UUID id() { return id; }
    public UUID torneoId() { return torneoId; }
    public UUID equipoId() { return equipoId; }
    public UUID jugadorUid() { return jugadorUid; }
    public Tipo tipo() { return tipo; }
    public String clave() { return clave; }
    public Estado estado() { return estado; }
    public UUID reservaId() { return reservaId; }
    public Integer monto() { return monto; }
    public String productoId() { return productoId; }
    public Sancion sancionVerificada() { return sancionVerificada; }
    public boolean creditosEntregados() { return creditosEntregados; }
    public boolean epicaEntregada() { return epicaEntregada; }
    public String titulo() { return titulo; }
    public String cuerpo() { return cuerpo; }
    public int intentos() { return intentos; }
    public OffsetDateTime proximoIntento() { return proximoIntento; }
    public OffsetDateTime bloqueadaHasta() { return bloqueadaHasta; }
    public String ultimoError() { return ultimoError; }
    public String resultado() { return resultado; }
    public OffsetDateTime creadaEn() { return creadaEn; }
    public OffsetDateTime actualizadaEn() { return actualizadaEn; }
}
