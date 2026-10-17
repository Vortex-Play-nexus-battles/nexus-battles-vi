package db.migration;

import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ms-chatbot.yaml 1.4.0 (7.4.8 «no almacenamiento de información sensible»).
 *
 * <p>Hasta la 1.3.9 solo los tickets se redactaban: las preguntas del chat,
 * los comentarios de las calificaciones y los ejemplos de las brechas de
 * conocimiento se guardaban tal cual llegaban, con la contraseña o la tarjeta
 * que el usuario hubiera escrito. Desde 1.4.0 todo se redacta antes de
 * guardarse; esta migración redacta lo que ya estaba guardado, con la misma
 * implementación ({@link RedactorDeDatosSensibles}), para que la base no
 * conserve ningún secreto de antes.
 *
 * <ul>
 *   <li>{@code mensajes.contenido} de los mensajes del usuario (los del bot
 *       nunca repiten lo que escribió el usuario);</li>
 *   <li>{@code calificaciones.comentario};</li>
 *   <li>{@code tickets_soporte} (asunto, mensaje) y
 *       {@code tickets_soporte_contexto.contenido}: ya se guardaban
 *       redactados; redactar dos veces no cambia nada;</li>
 *   <li>{@code brechas_conocimiento}: el ejemplo se redacta y la clave
 *       normalizada se recalcula. Si al redactar dos brechas quedan iguales
 *       («mi clave es abc» y «mi clave es xyz» → «mi clave es»), se funden en
 *       una: se suman sus contadores y se conservan la primera y la última
 *       fecha. Una brecha que solo era el secreto (un token pegado, una
 *       contraseña sola) se borra: no queda pregunta que agrupar.</li>
 * </ul>
 *
 * <p>Una fila cuyo texto no cambia no se toca. Es idempotente.
 */
public class V10__redactar_datos_sensibles_guardados extends BaseJavaMigration {

    // Igual que BrechaConocimientoService (VARCHAR(500) de V3).
    static final int LONGITUD_MAXIMA_TEXTO_NORMALIZADO = 500;

    private final RedaccionDeDatosSensibles redaccion = new RedactorDeDatosSensibles();

    @Override
    public void migrate(Context context) throws Exception {
        Connection conexion = context.getConnection();
        redactarColumna(conexion, "SELECT id, contenido FROM mensajes WHERE remitente = 'USUARIO'",
            "UPDATE mensajes SET contenido = ? WHERE id = ?");
        redactarColumna(conexion, "SELECT id, comentario FROM calificaciones WHERE comentario IS NOT NULL",
            "UPDATE calificaciones SET comentario = ? WHERE id = ?");
        redactarColumna(conexion, "SELECT id, asunto FROM tickets_soporte",
            "UPDATE tickets_soporte SET asunto = ? WHERE id = ?");
        redactarColumna(conexion, "SELECT id, mensaje FROM tickets_soporte",
            "UPDATE tickets_soporte SET mensaje = ? WHERE id = ?");
        redactarContextoDeTickets(conexion);
        redactarBrechas(conexion);
    }

    private void redactarColumna(Connection conexion, String consulta, String actualizacion) throws SQLException {
        List<Object[]> cambios = new ArrayList<>();
        try (PreparedStatement select = conexion.prepareStatement(consulta);
             ResultSet filas = select.executeQuery()) {
            while (filas.next()) {
                String original = filas.getString(2);
                String redactado = redaccion.redactar(original);
                if (original != null && !original.equals(redactado)) {
                    cambios.add(new Object[] {redactado, filas.getObject(1)});
                }
            }
        }
        try (PreparedStatement update = conexion.prepareStatement(actualizacion)) {
            for (Object[] cambio : cambios) {
                update.setString(1, (String) cambio[0]);
                update.setObject(2, cambio[1]);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void redactarContextoDeTickets(Connection conexion) throws SQLException {
        List<Object[]> cambios = new ArrayList<>();
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT ticket_id, orden, contenido FROM tickets_soporte_contexto");
             ResultSet filas = select.executeQuery()) {
            while (filas.next()) {
                String original = filas.getString(3);
                String redactado = redaccion.redactar(original);
                if (original != null && !original.equals(redactado)) {
                    cambios.add(new Object[] {redactado, filas.getObject(1), filas.getInt(2)});
                }
            }
        }
        try (PreparedStatement update = conexion.prepareStatement(
                "UPDATE tickets_soporte_contexto SET contenido = ? WHERE ticket_id = ? AND orden = ?")) {
            for (Object[] cambio : cambios) {
                update.setString(1, (String) cambio[0]);
                update.setObject(2, cambio[1]);
                update.setInt(3, (Integer) cambio[2]);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private record Brecha(Object id, String normalizado, String ejemplo, int noUtil, int escalamiento,
                          Timestamp primera, Timestamp ultima) {
    }

    // Cada brecha se lee de la base en su turno (no de una foto tomada al
    // principio): una fusion anterior pudo cambiar sus contadores o borrarla.
    private void redactarBrechas(Connection conexion) throws SQLException {
        List<Object> ids = new ArrayList<>();
        try (PreparedStatement select = conexion.prepareStatement("SELECT id FROM brechas_conocimiento ORDER BY id");
             ResultSet filas = select.executeQuery()) {
            while (filas.next()) {
                ids.add(filas.getObject(1));
            }
        }
        for (Object id : ids) {
            Optional<Brecha> leida = buscar(conexion, BRECHA_POR_ID, id);
            if (leida.isEmpty()) {
                continue;
            }
            Brecha brecha = leida.get();
            String ejemplo = redaccion.redactar(brecha.ejemplo());
            if (ejemplo.equals(brecha.ejemplo())) {
                continue;
            }
            String normalizado = acotar(NormalizadorTexto.normalizar(ejemplo));
            if (normalizado.isBlank()
                || NormalizadorTexto.normalizar(RedactorDeDatosSensibles.sinMarcas(ejemplo)).isBlank()) {
                // Lo unico que habia era el secreto: no queda pregunta que agrupar.
                borrar(conexion, brecha.id());
                continue;
            }
            Optional<Brecha> otra = normalizado.equals(brecha.normalizado())
                ? Optional.empty() : buscar(conexion, BRECHA_POR_CLAVE, normalizado);
            if (otra.isPresent()) {
                fundir(conexion, otra.get(), brecha);
            } else {
                try (PreparedStatement update = conexion.prepareStatement(
                        "UPDATE brechas_conocimiento SET ejemplo_pregunta = ?, texto_normalizado = ? WHERE id = ?")) {
                    update.setString(1, ejemplo);
                    update.setString(2, normalizado);
                    update.setObject(3, brecha.id());
                    update.executeUpdate();
                }
            }
        }
    }

    private static String acotar(String normalizado) {
        return normalizado.length() > LONGITUD_MAXIMA_TEXTO_NORMALIZADO
            ? normalizado.substring(0, LONGITUD_MAXIMA_TEXTO_NORMALIZADO).trim()
            : normalizado;
    }

    private static final String COLUMNAS_DE_BRECHA = "SELECT id, texto_normalizado, ejemplo_pregunta, "
        + "contador_no_util, contador_escalamiento, fecha_primera_ocurrencia, fecha_ultima_ocurrencia "
        + "FROM brechas_conocimiento ";
    private static final String BRECHA_POR_ID = COLUMNAS_DE_BRECHA + "WHERE id = ?";
    private static final String BRECHA_POR_CLAVE = COLUMNAS_DE_BRECHA + "WHERE texto_normalizado = ?";

    private static Optional<Brecha> buscar(Connection conexion, String consulta, Object valor) throws SQLException {
        try (PreparedStatement select = conexion.prepareStatement(consulta)) {
            select.setObject(1, valor);
            try (ResultSet filas = select.executeQuery()) {
                if (!filas.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Brecha(filas.getObject(1), filas.getString(2), filas.getString(3),
                    filas.getInt(4), filas.getInt(5), filas.getTimestamp(6), filas.getTimestamp(7)));
            }
        }
    }

    // La que queda es la que ya tenia la clave; la otra se borra y le pasa sus
    // contadores y sus fechas.
    private static void fundir(Connection conexion, Brecha queda, Brecha seVa) throws SQLException {
        Timestamp primera = queda.primera().before(seVa.primera()) ? queda.primera() : seVa.primera();
        Timestamp ultima = queda.ultima().after(seVa.ultima()) ? queda.ultima() : seVa.ultima();
        try (PreparedStatement update = conexion.prepareStatement(
                "UPDATE brechas_conocimiento SET contador_no_util = contador_no_util + ?, "
                    + "contador_escalamiento = contador_escalamiento + ?, fecha_primera_ocurrencia = ?, "
                    + "fecha_ultima_ocurrencia = ? WHERE id = ?")) {
            update.setInt(1, seVa.noUtil());
            update.setInt(2, seVa.escalamiento());
            update.setTimestamp(3, primera);
            update.setTimestamp(4, ultima);
            update.setObject(5, queda.id());
            update.executeUpdate();
        }
        borrar(conexion, seVa.id());
    }

    private static void borrar(Connection conexion, Object id) throws SQLException {
        try (PreparedStatement delete = conexion.prepareStatement("DELETE FROM brechas_conocimiento WHERE id = ?")) {
            delete.setObject(1, id);
            delete.executeUpdate();
        }
    }
}
