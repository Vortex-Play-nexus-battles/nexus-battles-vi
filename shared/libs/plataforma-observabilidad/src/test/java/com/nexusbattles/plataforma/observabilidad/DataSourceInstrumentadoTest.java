package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HU-REN-003, CA-01 — la instrumentacion que mide las consultas.
 *
 * <p>Se prueba contra dobles de JDBC y no contra una base de datos real: aqui
 * lo que se verifica es que el envoltorio mide, delega fielmente y no cambia el
 * comportamiento de nadie. Que los indices se usen de verdad es CA-02 y se
 * comprueba con el plan de ejecucion del motor, no con una prueba unitaria.
 */
class DataSourceInstrumentadoTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");
    private static final String SQL =
            "select t.id from terminos_prohibidos t where upper(t.termino) = upper(?)";

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final RegistroDeConsultas registro = new RegistroDeConsultas("moderacion-sanciones", 500);

    private DataSource real;
    private Connection conexionReal;
    private PreparedStatement sentenciaReal;
    private DataSourceInstrumentado instrumentado;

    @BeforeEach
    void prepararDobles() throws SQLException {
        real = mock(DataSource.class);
        conexionReal = mock(Connection.class);
        sentenciaReal = mock(PreparedStatement.class);

        given(real.getConnection()).willReturn(conexionReal);
        given(conexionReal.prepareStatement(anyString())).willReturn(sentenciaReal);

        instrumentado = new DataSourceInstrumentado(real, registro, reloj);
    }

    @Test
    void mideCadaConsultaSinQueElServicioTengaQueInstrumentarNada() throws SQLException {
        try (Connection conexion = instrumentado.getConnection()) {
            conexion.prepareStatement(SQL).executeQuery();
        }

        assertThat(registro.cuantasMuestras()).isEqualTo(1);
        MuestraDeConsulta muestra = registro.muestras().get(0);
        assertThat(muestra.servicio()).isEqualTo("moderacion-sanciones");
        assertThat(muestra.sentencia()).isEqualTo(SQL);
        assertThat(muestra.duracionMs()).isNotNegative();
        assertThat(muestra.instante()).isEqualTo(AHORA);
        assertThat(muestra.fallo()).isFalse();
    }

    @Test
    void laSentenciaSeGuardaConMarcadoresYNuncaConLosValoresDelJugador() throws SQLException {
        // Si se guardaran los valores, el informe y la bitacora acabarian con
        // datos de jugadores dentro, y ademas cada busqueda seria una sentencia
        // distinta y no habria percentil que calcular.
        Connection conexion = instrumentado.getConnection();
        PreparedStatement sentencia = conexion.prepareStatement(SQL);
        sentencia.setString(1, "Espada de Fuego");
        sentencia.executeQuery();

        assertThat(registro.muestras().get(0).sentencia()).contains("upper(?)").doesNotContain("Espada");
    }

    @Test
    void unaConsultaQueRevientaSeMideIgualYLaExcepcionLlegaIntacta() throws SQLException {
        // Un tiempo de espera agotado es la consulta lenta por excelencia: si
        // solo se midiera el camino feliz, no apareceria en ningun percentil.
        given(sentenciaReal.executeQuery()).willThrow(new SQLException("tiempo de espera agotado"));

        Connection conexion = instrumentado.getConnection();
        PreparedStatement sentencia = conexion.prepareStatement(SQL);

        assertThatThrownBy(sentencia::executeQuery)
                .isInstanceOf(SQLException.class)
                .hasMessage("tiempo de espera agotado");

        assertThat(registro.cuantasMuestras()).isEqualTo(1);
        assertThat(registro.muestras().get(0).fallo()).isTrue();
    }

    @Test
    void unStatementSinPrepararTomaElSqlDeLaLlamadaAExecute() throws SQLException {
        Statement sentenciaSuelta = mock(Statement.class);
        given(conexionReal.createStatement()).willReturn(sentenciaSuelta);

        instrumentado.getConnection().createStatement().execute("select 1");

        assertThat(registro.muestras().get(0).sentencia()).isEqualTo("select 1");
    }

    @Test
    void loQueNoEjecutaNadaNoSeMide() throws SQLException {
        Connection conexion = instrumentado.getConnection();
        PreparedStatement sentencia = conexion.prepareStatement(SQL);
        sentencia.setString(1, "algo");
        sentencia.getMetaData();
        sentencia.close();

        assertThat(registro.cuantasMuestras()).isZero();
    }

    @Test
    void laSentenciaEnvueltaSigueSiendoUnPreparedStatement() throws SQLException {
        // Media capa de persistencia hace este cast. Si el envoltorio expusiera
        // solo Statement, el servicio reventaria al arrancar.
        Object sentencia = instrumentado.getConnection().prepareStatement(SQL);

        assertThat(sentencia).isInstanceOf(PreparedStatement.class).isInstanceOf(Statement.class);
    }

    @Test
    void unCallableStatementSigueSiendoCallable() throws SQLException {
        CallableStatement procedimiento = mock(CallableStatement.class);
        given(conexionReal.prepareCall(anyString())).willReturn(procedimiento);

        Object envuelto = instrumentado.getConnection().prepareCall("{call algo(?)}");

        assertThat(envuelto).isInstanceOf(CallableStatement.class);
    }

    @Test
    void todoLoQueNoSeaEjecutarSePasaTalCualAlDataSourceReal() throws SQLException {
        // unwrap e isWrapperFor son los que usan Hibernate y el pool para
        // llegar al objeto concreto: un envoltorio que no los delegue rompe el
        // arranque del servicio.
        instrumentado.getLoginTimeout();
        then(real).should().getLoginTimeout();

        assertThat(instrumentado.isWrapperFor(DataSourceInstrumentado.class)).isTrue();
        assertThat(instrumentado.unwrap(DataSourceInstrumentado.class)).isSameAs(instrumentado);
        assertThat(instrumentado.delegado()).isSameAs(real);

        instrumentado.isWrapperFor(DataSource.class);
        instrumentado.unwrap(Connection.class);
        then(real).should().unwrap(Connection.class);
    }

    @Test
    void medirNuncaPuedeTumbarUnaConsulta() throws SQLException {
        // Si la instrumentacion fallara, el jugador perderia una operacion real
        // por culpa de la observabilidad. Se traga el error a proposito.
        RegistroDeConsultas registroQueFalla = new RegistroDeConsultas("s", 500) {
            @Override
            public synchronized void registrar(MuestraDeConsulta muestra) {
                throw new IllegalStateException("el registro se rompio");
            }
        };
        DataSourceInstrumentado fragil = new DataSourceInstrumentado(real, registroQueFalla, reloj);

        Connection conexion = fragil.getConnection();
        PreparedStatement sentencia = conexion.prepareStatement(SQL);

        assertThat(sentencia.executeQuery()).isNull();
    }

    @Test
    void unaConsultaLentaQuedaMarcadaParaOptimizar() throws SQLException {
        // CA-03 extremo a extremo sobre el envoltorio: se fuerza la duracion
        // desde el doble, sin dormir el hilo de la prueba.
        given(sentenciaReal.executeUpdate()).willAnswer(invocacion -> {
            Thread.sleep(2);
            return 1;
        });
        RegistroDeConsultas exigente = new RegistroDeConsultas("s", 1);
        DataSourceInstrumentado medidor = new DataSourceInstrumentado(real, exigente, reloj);

        medidor.getConnection().prepareStatement(SQL).executeUpdate();

        assertThat(exigente.totalLentas()).isEqualTo(1);
        assertThat(exigente.consultasLentas()).singleElement()
                .extracting(MuestraDeConsulta::sentencia)
                .isEqualTo(SQL);
    }
}
