package com.nexusbattles.ms_subastas.notificaciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cada tipo de {@link TipoNotificacion} tiene que caber en la restriccion
 * {@code chk_notificaciones_tipo}. Si falta, encolar ese aviso falla dentro de
 * la transaccion del hecho que lo motiva (un cierre, una compra inmediata) y
 * el hecho entero se revierte.
 *
 * <p>Las pruebas de integracion con PostgreSQL lo detectan, pero se omiten sin
 * Docker ({@code disabledWithoutDocker}); esta no lo necesita: lee la ultima
 * migracion que define la restriccion, sin sus comentarios.
 */
@DisplayName("Tipos de aviso frente a chk_notificaciones_tipo")
class RestriccionDeTiposDeAvisoTest {

    /** «chk_notificaciones_tipo CHECK (tipo IN», sin importar mayusculas ni espacios. */
    private static final Pattern DEFINE_LA_RESTRICCION =
            Pattern.compile("(?i)chk_notificaciones_tipo\\s+check\\s*\\(\\s*tipo\\s+in\\s*\\(");

    @Test
    @DisplayName("la ultima migracion que define la restriccion admite todos los tipos del enum")
    void laRestriccionAdmiteTodosLosTipos() throws IOException {
        Resource[] migraciones = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");
        Resource ultima = Arrays.stream(migraciones)
                .filter(migracion -> DEFINE_LA_RESTRICCION.matcher(sinComentarios(migracion)).find())
                .max(Comparator.comparingInt(RestriccionDeTiposDeAvisoTest::version))
                .orElseThrow(() -> new AssertionError("ninguna migracion define chk_notificaciones_tipo"));
        String sql = sinComentarios(ultima);

        List<String> faltan = Arrays.stream(TipoNotificacion.values())
                .map(Enum::name)
                .filter(tipo -> !sql.contains("'" + tipo + "'"))
                .toList();

        assertEquals(List.of(), faltan, "tipos sin sitio en chk_notificaciones_tipo (" + ultima.getFilename()
                + "): anadelos con una migracion nueva");
    }

    private static int version(Resource migracion) {
        String nombre = migracion.getFilename();
        return Integer.parseInt(nombre.substring(1, nombre.indexOf("__")));
    }

    /** El SQL de la migracion sin los comentarios de linea ({@code -- ...}). */
    private static String sinComentarios(Resource migracion) {
        try {
            String sql = new String(migracion.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return sql.replaceAll("--[^\\n]*", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
