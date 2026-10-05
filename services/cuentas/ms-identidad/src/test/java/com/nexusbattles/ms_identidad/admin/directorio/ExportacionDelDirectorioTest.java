package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exportacion del directorio en CSV — HU-USR-008 (#561), 7.3.4 «Exportar
 * listados de usuarios».
 *
 * <p>Lo que importa: que salgan TODAS las cuentas de los filtros (no una
 * pagina), con las columnas que el directorio muestra y ninguna mas, que una
 * celda no se pueda ejecutar como formula en una hoja de calculo, y que por
 * encima del tope no se entregue un archivo a medias.
 */
@DisplayName("Exportacion del directorio (HU-USR-008)")
class ExportacionDelDirectorioTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-05T20:07:00Z"), ZoneId.of("America/Bogota"));

    private final DirectorioDeCuentas directorio = mock(DirectorioDeCuentas.class);
    private final CuentasDePrueba cuentasDePrueba = new CuentasDePrueba("nexus.test", "qa_");

    private ExportacionDelDirectorio exportador(int maximo) {
        return new ExportacionDelDirectorio(directorio, cuentasDePrueba, RELOJ, maximo);
    }

    private static Usuario cuenta(String apodo, String email, String rol, String estado) {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setApodo(apodo);
        usuario.setEmail(email);
        usuario.setPassword("hash-que-nunca-deberia-salir");
        usuario.setEstado(estado);
        usuario.setRol(new RolEntity(rol, "rol de prueba"));
        usuario.setCreadoEn(LocalDateTime.of(2026, 9, 1, 8, 5, 33));
        usuario.setUltimoAcceso(null);
        return usuario;
    }

    @SuppressWarnings("unchecked")
    private void devolviendo(List<Usuario> cuentas, long total) {
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocacion -> new PageImpl<>(cuentas, invocacion.getArgument(1), total));
    }

    private static String texto(ExportacionDelDirectorio.Exportacion exportacion) {
        return new String(exportacion.contenido(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("cabecera con las columnas del directorio, BOM y una fila por cuenta")
    void columnasDelDirectorio() {
        Usuario ana = cuenta("Ana", "ana@ejemplo.org", "JUGADOR", "ACTIVO");
        ana.setUltimoAcceso(LocalDateTime.of(2026, 10, 4, 21, 15));
        devolviendo(List.of(ana), 1);

        String csv = texto(exportador(100).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null)));

        assertThat(csv).startsWith("\uFEFF");
        List<String> lineas = List.of(csv.substring(1).split("\r\n"));
        assertThat(lineas).containsExactly(
                "Apodo,Correo,Rol,Estado,Registro,Última entrada",
                "\"Ana\",\"ana@ejemplo.org\",\"JUGADOR\",\"ACTIVO\",2026-09-01 08:05,2026-10-04 21:15");
    }

    @Test
    @DisplayName("sin datos de mas: ni clave, ni identificadores, ni la contrasena")
    void sinDatosDeMas() {
        devolviendo(List.of(cuenta("Ana", "ana@ejemplo.org", "JUGADOR", "ACTIVO")), 1);

        String csv = texto(exportador(100).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null)));

        assertThat(csv).doesNotContain("hash-que-nunca-deberia-salir").doesNotContain(",7,");
        // -1: la ultima celda (nunca ha entrado) esta vacia y tambien cuenta.
        assertThat(csv.split("\r\n")[1].split(",", -1)).hasSize(6);
    }

    @Test
    @DisplayName("nunca ha entrado y sin fecha de alta: celdas vacias, no una fecha inventada")
    void fechasNulas() {
        Usuario sinFechas = cuenta("Beto", "beto@ejemplo.org", "JUGADOR", "ACTIVO");
        sinFechas.setCreadoEn(null);
        devolviendo(List.of(sinFechas), 1);

        String csv = texto(exportador(100).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null)));

        assertThat(csv.split("\r\n")[1]).isEqualTo("\"Beto\",\"beto@ejemplo.org\",\"JUGADOR\",\"ACTIVO\",,");
    }

    @Test
    @DisplayName("el estado es el de la tabla: BLOQUEADA con un bloqueo vigente; las formas viejas, como el contrato")
    void estadoComoLaTabla() {
        Usuario bloqueada = cuenta("Cris", "cris@ejemplo.org", "JUGADOR", "ACTIVO");
        bloqueada.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        Usuario vieja = cuenta("Dani", "dani@ejemplo.org", "JUGADOR", "SUSPENDIDA");
        devolviendo(List.of(bloqueada, vieja), 2);

        String[] filas = texto(exportador(100).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null)))
                .split("\r\n");

        assertThat(filas[1]).contains(",\"BLOQUEADA\",");
        assertThat(filas[2]).contains(",\"SUSPENDIDO\",");
    }

    @Test
    @DisplayName("comillas, comas y saltos de linea no rompen la fila; una formula no se ejecuta")
    void celdasSeguras() {
        assertThat(ExportacionDelDirectorio.celda("Ana \"la\" grande, de Bucaramanga"))
                .isEqualTo("\"Ana \"\"la\"\" grande, de Bucaramanga\"");
        assertThat(ExportacionDelDirectorio.celda("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(ExportacionDelDirectorio.celda("+57")).isEqualTo("\"'+57\"");
        assertThat(ExportacionDelDirectorio.celda("-1")).isEqualTo("\"'-1\"");
        assertThat(ExportacionDelDirectorio.celda("@suma")).isEqualTo("\"'@suma\"");
        assertThat(ExportacionDelDirectorio.celda("\tx")).isEqualTo("\"'\tx\"");
        assertThat(ExportacionDelDirectorio.celda("linea\nnueva")).isEqualTo("\"linea\nnueva\"");
        assertThat(ExportacionDelDirectorio.celda(null)).isEmpty();
    }

    @Test
    @DisplayName("todas las cuentas de los filtros en una consulta, en el orden del directorio")
    @SuppressWarnings("unchecked")
    void unaSolaConsultaConElOrdenDelDirectorio() {
        devolviendo(List.of(), 0);

        exportador(250).exportar(FiltroDelDirectorio.de("ana", true, "JUGADOR", null, null, null));

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(directorio).findAll(any(Specification.class), pedido.capture());
        assertThat(pedido.getValue()).isEqualTo(PageRequest.of(0, 250, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    @DisplayName("por encima del tope: 422 con las dos cifras y ningun archivo")
    void porEncimaDelTope() {
        devolviendo(List.of(cuenta("Ana", "ana@ejemplo.org", "JUGADOR", "ACTIVO")), 3);

        assertThatThrownBy(() -> exportador(1).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null)))
                .isInstanceOf(ExportacionDemasiadoGrandeException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("el nombre del archivo lleva el dia y la hora del reloj del servicio")
    void nombreDelArchivo() {
        devolviendo(List.of(), 0);

        ExportacionDelDirectorio.Exportacion exportacion =
                exportador(10).exportar(FiltroDelDirectorio.de(null, false, null, null, null, null));

        assertThat(exportacion.nombreArchivo()).isEqualTo("directorio-de-cuentas-20261005-1507.csv");
        assertThat(texto(exportacion)).isEqualTo("\uFEFFApodo,Correo,Rol,Estado,Registro,Última entrada\r\n");
    }

    @Test
    @DisplayName("un tope imposible no arranca")
    void topeImposible() {
        assertThatThrownBy(() -> exportador(0)).isInstanceOf(IllegalStateException.class);
    }
}
