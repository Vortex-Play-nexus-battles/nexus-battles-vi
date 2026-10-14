package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Una accion administrativa de este servicio camino de la bitacora central,
 * pendiente o ya entregada — §7.3.5 (SA-5), tabla {@code auditoria_pendiente}
 * (V13).
 *
 * <p>Se guarda en la MISMA transaccion que la accion: una sancion emitida no
 * puede quedar sin su asiento porque ms-cumplimiento estuviera caido en ese
 * momento. Lo entrega despues {@link EntregaDeAuditoria}, con reintentos y con
 * su {@link #referencia()}, para que un reintento no duplique la entrada.
 *
 * <p>Los textos se recortan al largo de las columnas de ms-cumplimiento (el
 * motivo a 500, el afectado a 255): ms-cumplimiento rechaza con 400 lo que no
 * cabe, y un asiento que nunca entra no sirve de nada.
 */
@Entity
@Table(name = "auditoria_pendiente")
public class AsientoDeAuditoria {

    static final int LARGO_AFECTADO = 255;
    static final int LARGO_MOTIVO = 500;
    static final int LARGO_VALOR = 2000;

    /** Prefijo de la referencia en ms-cumplimiento: unica entre todos los servicios que registran. */
    static final String ORIGEN = "moderacion-sanciones:";

    @Id
    private UUID id;

    @Column(name = "tipo_accion", nullable = false, length = 40)
    private String tipoAccion;

    @Column(name = "administrador_id", nullable = false, length = 64)
    private String administradorId;

    @Column(nullable = false, length = LARGO_AFECTADO)
    private String afectado;

    @Column(name = "valor_anterior", length = LARGO_VALOR)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = LARGO_VALOR)
    private String valorNuevo;

    @Column(nullable = false, length = LARGO_MOTIVO)
    private String motivo;

    @Column(name = "ip_origen", nullable = false, length = 45)
    private String ipOrigen;

    @Column(name = "ocurrido_en", nullable = false)
    private OffsetDateTime ocurridoEn;

    @Column(name = "entregado_en")
    private OffsetDateTime entregadoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "proximo_intento_en")
    private OffsetDateTime proximoIntentoEn;

    protected AsientoDeAuditoria() {
    }

    private AsientoDeAuditoria(UUID id, TipoDeAccion tipo, UUID administradorId, String afectado,
                               String valorAnterior, String valorNuevo, String motivo, String ipOrigen,
                               OffsetDateTime ocurridoEn) {
        this.id = Objects.requireNonNull(id);
        this.tipoAccion = Objects.requireNonNull(tipo).name();
        this.administradorId = Objects.requireNonNull(administradorId).toString();
        this.afectado = recortar(Objects.requireNonNull(afectado), LARGO_AFECTADO);
        this.valorAnterior = recortar(valorAnterior, LARGO_VALOR);
        this.valorNuevo = recortar(valorNuevo, LARGO_VALOR);
        this.motivo = recortar(Objects.requireNonNull(motivo), LARGO_MOTIVO);
        this.ipOrigen = Objects.requireNonNull(ipOrigen);
        this.ocurridoEn = Objects.requireNonNull(ocurridoEn);
    }

    public static AsientoDeAuditoria nuevo(TipoDeAccion tipo, UUID administradorId, String afectado,
                                           String valorAnterior, String valorNuevo, String motivo,
                                           String ipOrigen, OffsetDateTime ocurridoEn) {
        return new AsientoDeAuditoria(UUID.randomUUID(), tipo, administradorId, afectado, valorAnterior,
                valorNuevo, motivo, ipOrigen, ocurridoEn);
    }

    public void entregado(OffsetDateTime ahora) {
        this.entregadoEn = Objects.requireNonNull(ahora);
        this.intentos++;
        this.ultimoError = null;
        this.proximoIntentoEn = null;
    }

    /** Un intento que no entro: la espera crece con cada fallo ({@code base}, 2x, 4x...) hasta {@code maxima}. */
    public void fallo(String motivoDelFallo, OffsetDateTime ahora, Duration base, Duration maxima) {
        this.intentos++;
        this.ultimoError = recortar(motivoDelFallo == null ? "" : motivoDelFallo, 500);
        Duration espera = base;
        for (int i = 1; i < intentos && espera.compareTo(maxima) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        this.proximoIntentoEn = Objects.requireNonNull(ahora).plus(espera.compareTo(maxima) > 0 ? maxima : espera);
    }

    private static String recortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= largo ? limpio : limpio.substring(0, largo - 1) + "…";
    }

    /** La clave del evento en ms-cumplimiento: {@code moderacion-sanciones:<id>}. */
    public String referencia() {
        return ORIGEN + id;
    }

    public UUID id() {
        return id;
    }

    public String tipoAccion() {
        return tipoAccion;
    }

    public String administradorId() {
        return administradorId;
    }

    public String afectado() {
        return afectado;
    }

    public String valorAnterior() {
        return valorAnterior;
    }

    public String valorNuevo() {
        return valorNuevo;
    }

    public String motivo() {
        return motivo;
    }

    public String ipOrigen() {
        return ipOrigen;
    }

    public OffsetDateTime ocurridoEn() {
        return ocurridoEn;
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
