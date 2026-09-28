package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cofre ganado por un jugador — HU-JUE-012/013, cofres.yaml 1.1.0 (B7).
 *
 * <p><b>El identificador lo pone el servicio, no la base.</b> Hace falta antes
 * de guardar: es la clave de idempotencia de la entrega al inventario
 * ({@code cofre-{id}}). Hasta B7 la entidad llevaba además
 * {@code @GeneratedValue}, y un identificador asignado a mano sobre un campo
 * generado hacía que Spring Data tomara el cofre por uno ya existente
 * ({@code merge}) — Hibernate lo rechaza como fila «modificada por otra
 * transacción». Ahora es {@link Persistable}: nuevo hasta que se guarda.
 *
 * <p><b>Contenido real.</b> {@link #premios} es lo que se sorteó con
 * {@link #semilla} sobre la tabla {@link #tablaVersion}; con las dos se puede
 * repetir el sorteo. {@link #contenido} conserva el identificador del premio
 * (en los cofres anteriores a B7 es el marcador {@code COFRE_ESTANDAR_v1}).
 *
 * <p><b>Entrega.</b> {@code PENDIENTE} hasta que inventario confirma la
 * entrega ({@code ENTREGADO}, con {@link #entregaId}); los cofres anteriores a
 * B7 son {@code SIN_CONTENIDO}.
 */
@Entity
@Table(name = "cofre_entregado")
@Getter
@Setter
@NoArgsConstructor
public class CofreEntregado implements Persistable<UUID> {

    /** Cómo va la entrega del cofre al inventario (cofres.yaml 1.1.0). */
    public enum EstadoEntrega { PENDIENTE, ENTREGADO, SIN_CONTENIDO }

    @Id
    private UUID id;

    @Column(name = "uid_jugador", length = 64, nullable = false)
    private String uidJugador;

    @Column(name = "semana_iso", length = 10, nullable = false)
    private String semanaIso;

    @Column(nullable = false, length = 64)
    private String contenido;

    @Column(name = "entregado_en", nullable = false)
    private Instant entregadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_entrega", length = 20, nullable = false)
    private EstadoEntrega estadoEntrega = EstadoEntrega.SIN_CONTENIDO;

    @Column(name = "entrega_id", length = 64)
    private String entregaId;

    @Column(name = "semilla")
    private Long semilla;

    @Column(name = "tabla_version", length = 40)
    private String tablaVersion;

    @Column(name = "intentos_entrega", nullable = false)
    private int intentosEntrega;

    @Column(name = "proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "ultimo_error", length = 300)
    private String ultimoError;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cofre_premio", joinColumns = @JoinColumn(name = "cofre_id"))
    @OrderColumn(name = "orden")
    private List<PremioDeCofre> premios = new ArrayList<>();

    /**
     * Bloqueo optimista (V5): el intento de entrega inmediato y el programado
     * pueden coincidir, y el que llega tarde no debe pisar al otro.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Nuevo hasta que se guarda o se lee de la base (ver la nota de la clase). */
    @Transient
    private boolean nuevo = true;

    /**
     * Un cofre recién ganado, con su contenido ya sorteado y la entrega por
     * hacer.
     *
     * @param semilla semilla del sorteo, para poder repetirlo
     * @param tabla   la tabla con la que se sorteó
     */
    public static CofreEntregado sorteado(String uidJugador, String semanaIso, Instant ganadoEn, long semilla,
                                          TablaDeCofre tabla) {
        TablaDeCofre.Entrada premio = tabla.sortear(semilla);
        CofreEntregado cofre = new CofreEntregado();
        cofre.id = UUID.randomUUID();
        cofre.uidJugador = uidJugador;
        cofre.semanaIso = semanaIso;
        cofre.entregadoEn = ganadoEn;
        cofre.contenido = premio.productoId();
        cofre.premios = new ArrayList<>(List.of(new PremioDeCofre(premio.productoId(), 1)));
        cofre.semilla = semilla;
        cofre.tablaVersion = tabla.version();
        cofre.estadoEntrega = EstadoEntrega.PENDIENTE;
        cofre.proximoIntentoEn = ganadoEn;
        return cofre;
    }

    /** Clave de idempotencia de la entrega: reintentar nunca duplica (cofres.yaml 1.1.0). */
    public String claveDeEntrega() {
        return "cofre-" + id;
    }

    /** Inventario confirmó la entrega. */
    public void entregado(String idDeEntrega) {
        this.estadoEntrega = EstadoEntrega.ENTREGADO;
        this.entregaId = idDeEntrega;
        this.ultimoError = null;
        this.proximoIntentoEn = null;
        this.intentosEntrega++;
    }

    /**
     * La entrega no se pudo hacer: queda pendiente y se vuelve a intentar más
     * tarde, un minuto más tarde por cada intento fallido y como mucho cada
     * {@code esperaMaximaMinutos}.
     */
    public void entregaFallida(String motivo, Instant ahora, int esperaMaximaMinutos) {
        if (estadoEntrega != EstadoEntrega.PENDIENTE) {
            return;
        }
        this.intentosEntrega++;
        this.ultimoError = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 300));
        this.proximoIntentoEn = ahora.plusSeconds(60L * Math.min(intentosEntrega, esperaMaximaMinutos));
    }

    public boolean pendienteDeEntrega() {
        return estadoEntrega == EstadoEntrega.PENDIENTE;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void yaNoEsNuevo() {
        this.nuevo = false;
    }
}
