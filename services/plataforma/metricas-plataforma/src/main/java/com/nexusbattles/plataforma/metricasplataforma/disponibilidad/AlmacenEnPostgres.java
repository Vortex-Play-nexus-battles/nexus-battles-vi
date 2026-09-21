package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Interrupciones y ventanas en el esquema {@code metricas} de PostgreSQL —
 * HU-DIS-001.
 *
 * <p>JDBC a secas y no JPA: son dos tablas de pocas filas (una interrupcion
 * por caida, no por sondeo) y un servicio que comparte un {@code t3.small}
 * con otros ocho; Hibernate anadiria decenas de MB de metaspace para mapear
 * dos registros. Esquema propio dentro de la misma instancia, que es como
 * la plataforma cumple la regla 7 sin pagar una base por microservicio.
 *
 * <p>Los instantes viajan como {@code timestamptz} y se leen como
 * {@link Instant}: sin zona horaria de por medio.
 */
public final class AlmacenEnPostgres implements AlmacenDeDisponibilidad {

    private final JdbcClient jdbc;

    public AlmacenEnPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long abrir(Interrupcion interrupcion) {
        return jdbc.sql("""
                        INSERT INTO metricas.interrupciones (servicio, inicio, detalle)
                        VALUES (:servicio, :inicio, :detalle)
                        RETURNING id
                        """)
                .param("servicio", interrupcion.servicio())
                .param("inicio", Timestamp.from(interrupcion.inicio()))
                .param("detalle", interrupcion.detalle() == null ? "" : interrupcion.detalle())
                .query(Long.class)
                .single();
    }

    @Override
    public void cerrar(long id, Instant fin) {
        jdbc.sql("UPDATE metricas.interrupciones SET fin = :fin WHERE id = :id AND fin IS NULL")
                .param("fin", Timestamp.from(fin))
                .param("id", id)
                .update();
    }

    @Override
    public void guardarVentana(VentanaDeMantenimiento ventana) {
        jdbc.sql("""
                        INSERT INTO metricas.ventanas_mantenimiento (inicio, fin, motivo)
                        VALUES (:inicio, :fin, :motivo)
                        """)
                .param("inicio", Timestamp.from(ventana.inicio()))
                .param("fin", Timestamp.from(ventana.fin()))
                .param("motivo", ventana.motivo() == null ? "" : ventana.motivo())
                .update();
    }

    @Override
    public List<Interrupcion> interrupciones() {
        return jdbc.sql("SELECT id, servicio, inicio, fin, detalle FROM metricas.interrupciones ORDER BY inicio, id")
                .query((fila, n) -> Interrupcion.guardada(
                        fila.getLong("id"),
                        fila.getString("servicio"),
                        fila.getTimestamp("inicio").toInstant(),
                        fila.getTimestamp("fin") == null ? null : fila.getTimestamp("fin").toInstant(),
                        fila.getString("detalle")))
                .list();
    }

    @Override
    public List<VentanaDeMantenimiento> ventanas() {
        return jdbc.sql("SELECT inicio, fin, motivo FROM metricas.ventanas_mantenimiento ORDER BY inicio, id")
                .query((fila, n) -> new VentanaDeMantenimiento(
                        fila.getTimestamp("inicio").toInstant(),
                        fila.getTimestamp("fin").toInstant(),
                        fila.getString("motivo")))
                .list();
    }
}
