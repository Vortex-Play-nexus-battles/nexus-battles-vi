package com.nexusbattles.plataforma.torneos.torneo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Equipo de dos jugadores (RF-TOR-003) o equipo de la maquina que rellena
 * una posicion vacia (RF-TOR-005, {@code ia = true}, sin integrantes).
 *
 * <p>Registrar el equipo y pagar la inscripcion (RF-TOR-002) son dos pasos:
 * asi el capitan puede sustituir al companero antes de pagar y el cupo solo
 * se ocupa cuando hay reserva de creditos (o el torneo es gratuito).
 */
@Entity
@Table(name = "equipos")
public class Equipo {

    @Id
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(nullable = false, length = 300)
    private String avatar;

    @Column(nullable = false)
    private boolean ia;

    @Column(name = "capitan_uid")
    private UUID capitanUid;

    @Column(name = "integrante_1")
    private UUID integrante1;

    @Column(name = "integrante_2")
    private UUID integrante2;

    @Column(nullable = false)
    private boolean inscrito;

    @Column(name = "pagado_por")
    private UUID pagadoPor;

    @Column(name = "reserva_id")
    private UUID reservaId;

    private Integer posicion;

    @Column(nullable = false)
    private int derrotas;

    @Column(nullable = false)
    private boolean eliminado;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    protected Equipo() {
    }

    public static Equipo deJugadores(UUID id, UUID torneoId, String nombre, String avatar,
                                     UUID capitan, UUID companero, OffsetDateTime ahora) {
        if (capitan.equals(companero)) {
            throw new IllegalArgumentException("los dos integrantes tienen que ser jugadores distintos");
        }
        Equipo equipo = new Equipo();
        equipo.id = Objects.requireNonNull(id);
        equipo.torneoId = Objects.requireNonNull(torneoId);
        equipo.nombre = Objects.requireNonNull(nombre).strip();
        equipo.avatar = Objects.requireNonNull(avatar).strip();
        equipo.capitanUid = Objects.requireNonNull(capitan);
        equipo.integrante1 = capitan;
        equipo.integrante2 = Objects.requireNonNull(companero);
        equipo.creadoEn = Objects.requireNonNull(ahora);
        return equipo;
    }

    public static Equipo deLaMaquina(UUID id, UUID torneoId, int numero, int posicion, OffsetDateTime ahora) {
        Equipo equipo = new Equipo();
        equipo.id = Objects.requireNonNull(id);
        equipo.torneoId = Objects.requireNonNull(torneoId);
        equipo.nombre = "Maquina " + numero;
        equipo.avatar = "maquina";
        equipo.ia = true;
        equipo.inscrito = true;
        equipo.posicion = posicion;
        equipo.creadoEn = Objects.requireNonNull(ahora);
        return equipo;
    }

    public boolean tieneIntegrante(UUID uid) {
        return uid != null && (uid.equals(integrante1) || uid.equals(integrante2));
    }

    public boolean esCapitan(UUID uid) {
        return uid != null && uid.equals(capitanUid);
    }

    public void sustituirCompanero(UUID nuevo) {
        if (inscrito) {
            throw new IllegalStateException("el equipo ya esta inscrito; ya no se sustituye");
        }
        if (capitanUid.equals(nuevo)) {
            throw new IllegalArgumentException("el capitan no puede ser su propio companero");
        }
        this.integrante2 = Objects.requireNonNull(nuevo);
    }

    public void inscribir(int posicion, UUID pagadoPor, UUID reservaId) {
        if (inscrito) {
            throw new IllegalStateException("el equipo ya esta inscrito");
        }
        this.inscrito = true;
        this.posicion = posicion;
        this.pagadoPor = pagadoPor;
        this.reservaId = reservaId;
    }

    public void anotarDerrota() {
        derrotas++;
        if (derrotas >= 2) {
            eliminado = true;
        }
    }

    public void eliminar() {
        eliminado = true;
    }

    public List<UUID> integrantes() {
        List<UUID> lista = new ArrayList<>(2);
        if (integrante1 != null) {
            lista.add(integrante1);
        }
        if (integrante2 != null) {
            lista.add(integrante2);
        }
        return lista;
    }

    public UUID id() { return id; }
    public UUID torneoId() { return torneoId; }
    public String nombre() { return nombre; }
    public String avatar() { return avatar; }
    public boolean ia() { return ia; }
    public UUID capitanUid() { return capitanUid; }
    public boolean inscrito() { return inscrito; }
    public UUID pagadoPor() { return pagadoPor; }
    public UUID reservaId() { return reservaId; }
    public Integer posicion() { return posicion; }
    public int derrotas() { return derrotas; }
    public boolean eliminado() { return eliminado; }
    public OffsetDateTime creadoEn() { return creadoEn; }
}
