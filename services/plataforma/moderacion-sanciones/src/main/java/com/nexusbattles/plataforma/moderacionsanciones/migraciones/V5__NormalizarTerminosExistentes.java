package com.nexusbattles.plataforma.moderacionsanciones.migraciones;

import com.nexusbattles.plataforma.moderacionsanciones.listanegra.ModoDeCoincidencia;
import com.nexusbattles.plataforma.moderacionsanciones.listanegra.NormalizadorDeTexto;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * V5 — calcula la forma normalizada y el modo de los terminos que ya estaban
 * en la lista negra antes de la 2.0.0 (V4 creo las columnas vacias).
 *
 * <p><b>Por que en Java y no en SQL.</b> La forma guardada solo sirve si es
 * exactamente la que {@link NormalizadorDeTexto} calcula para el texto que se
 * verifica. Reescribir la normalizacion en SQL (NFKC, diacriticos, ancho cero,
 * leetspeak, rachas) daria un segundo algoritmo con sus propias diferencias
 * —por ejemplo en lo que PostgreSQL considera letra segun la configuracion
 * regional de la base— y la comparacion fallaria en silencio. Esta clase la
 * registra Spring Boot como migracion de Flyway (es un bean
 * {@code JavaMigration}); se aplica una sola vez, entre V4 y V6, como las de
 * SQL.
 *
 * <p><b>Choques.</b> Dos terminos anteriores pueden escribirse distinto y
 * normalizar igual («spider-man» y «spiderman»). V6 hace unica la forma, asi
 * que el segundo (por {@code id}) no puede quedarse con ella: se conserva la
 * fila —ninguna migracion borra datos—, se APAGA y su forma queda marcada con
 * su id ({@code spiderman#12}). Un {@code #} nunca sale de la normalizacion,
 * asi que esa forma no casa con ningun texto ni choca con un alta futura; el
 * panel la muestra inactiva y un moderador decide si borrarla. Lo mismo con un
 * termino cuya forma queda vacia (solo simbolos).
 */
@Component
public class V5__NormalizarTerminosExistentes extends BaseJavaMigration {

    /** Una fila anterior a la 2.0.0: solo tenia id y termino. */
    record FilaExistente(long id, String termino) {
    }

    /** Lo que se escribe en cada fila. */
    record Asignacion(long id, String normalizado, ModoDeCoincidencia modo, boolean apagar) {
    }

    @Override
    public void migrate(Context contexto) throws Exception {
        Connection conexion = contexto.getConnection();
        List<FilaExistente> filas = new ArrayList<>();
        Set<String> ocupadas = new HashSet<>();
        try (Statement consulta = conexion.createStatement();
             ResultSet resultado = consulta.executeQuery(
                     "SELECT id, termino, normalizado FROM terminos_prohibidos ORDER BY id")) {
            while (resultado.next()) {
                String normalizado = resultado.getString(3);
                if (normalizado == null) {
                    filas.add(new FilaExistente(resultado.getLong(1), resultado.getString(2)));
                } else {
                    ocupadas.add(normalizado);
                }
            }
        }
        try (PreparedStatement actualizacion = conexion.prepareStatement(
                "UPDATE terminos_prohibidos SET normalizado = ?, modo = ?, activo = activo AND ? WHERE id = ?")) {
            for (Asignacion asignacion : planificar(filas, ocupadas)) {
                actualizacion.setString(1, asignacion.normalizado());
                actualizacion.setString(2, asignacion.modo().name());
                actualizacion.setBoolean(3, !asignacion.apagar());
                actualizacion.setLong(4, asignacion.id());
                actualizacion.addBatch();
            }
            actualizacion.executeBatch();
        }
    }

    /**
     * La decision, sin base de datos: forma y modo de cada fila, en orden de
     * {@code id}, apagando la que choca con una forma ya tomada o se queda
     * vacia.
     *
     * @param ocupadas formas que ya tienen dueno; se van anadiendo las asignadas
     */
    static List<Asignacion> planificar(List<FilaExistente> filas, Set<String> ocupadas) {
        List<Asignacion> asignaciones = new ArrayList<>();
        for (FilaExistente fila : filas) {
            String forma = NormalizadorDeTexto.compacta(fila.termino());
            ModoDeCoincidencia modo = ModoDeCoincidencia.porOmision(forma);
            if (forma.isEmpty() || !ocupadas.add(forma)) {
                asignaciones.add(new Asignacion(fila.id(), forma + "#" + fila.id(), modo, true));
            } else {
                asignaciones.add(new Asignacion(fila.id(), forma, modo, false));
            }
        }
        return asignaciones;
    }
}
