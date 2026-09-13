package com.nexusbattles.plataforma.observabilidad;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Mide el tiempo de cada consulta a la base de datos (HU-REN-003, CA-01).
 *
 * <p>Misma idea que {@link FiltroDeLatencia} pero una capa mas abajo: en vez de
 * pedirle a cada servicio que instrumente sus repositorios uno por uno, se
 * envuelve el {@code DataSource} y se mide todo lo que pasa por el. Asi la
 * medicion cubre las consultas que genera Hibernate, las de Spring Data y las
 * escritas a mano, sin que ningun equipo tenga que tocar su codigo ni acordarse
 * de anadir una linea al crear un repositorio nuevo.
 *
 * <p>La sentencia se registra con sus marcadores {@code ?}, nunca con los
 * valores: ver {@link MuestraDeConsulta}.
 *
 * <p><b>Delegacion fiel.</b> Todo lo que no sea ejecutar una sentencia se pasa
 * tal cual al {@code DataSource} real, incluidos {@code unwrap} e
 * {@code isWrapperFor}, que es lo que usan Hibernate y los pools para llegar al
 * objeto concreto. Un envoltorio que no los delegue rompe el arranque del
 * servicio.
 */
public class DataSourceInstrumentado implements DataSource {

    /** Metodos de {@code Statement} que realmente ejecutan algo contra el motor. */
    private static final Set<String> METODOS_QUE_EJECUTAN =
            Set.of("execute", "executeQuery", "executeUpdate", "executeBatch",
                    "executeLargeUpdate", "executeLargeBatch");

    private static final Set<String> METODOS_QUE_PREPARAN =
            Set.of("prepareStatement", "prepareCall", "createStatement");

    private final DataSource delegado;
    private final RegistroDeConsultas registro;
    private final Clock reloj;

    public DataSourceInstrumentado(DataSource delegado, RegistroDeConsultas registro, Clock reloj) {
        this.delegado = delegado;
        this.registro = registro;
        this.reloj = reloj;
    }

    /** El {@code DataSource} real, por si alguien necesita llegar a el. */
    public DataSource delegado() {
        return delegado;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return envolver(delegado.getConnection());
    }

    @Override
    public Connection getConnection(String usuario, String clave) throws SQLException {
        return envolver(delegado.getConnection(usuario, clave));
    }

    private Connection envolver(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                DataSourceInstrumentado.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ManejadorDeConexion(real));
    }

    /**
     * Intercepta la creacion de sentencias para poder envolverlas.
     *
     * <p>El SQL se captura aqui, al prepararla, porque es donde viene como
     * argumento. {@code createStatement()} no lo trae: en ese caso el SQL llega
     * despues, en la llamada a {@code execute(sql)}.
     */
    private final class ManejadorDeConexion implements InvocationHandler {

        private final Connection real;

        private ManejadorDeConexion(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            Object resultado = invocar(real, metodo, argumentos);

            if (resultado instanceof java.sql.Statement sentencia
                    && METODOS_QUE_PREPARAN.contains(metodo.getName())) {

                String sql = argumentos != null && argumentos.length > 0 && argumentos[0] instanceof String texto
                        ? texto
                        : null;
                return envolverSentencia(sentencia, sql);
            }
            return resultado;
        }
    }

    /**
     * Envuelve la sentencia exponiendo la interfaz mas especifica que cumple.
     *
     * <p>Se mira de la mas especifica a la mas general porque
     * {@code CallableStatement} extiende {@code PreparedStatement} y este a
     * {@code Statement}: exponer solo {@code Statement} haria fallar con
     * {@code ClassCastException} el primer cast a {@code PreparedStatement},
     * que es lo que hace media capa de persistencia.
     *
     * <p>Las tres son interfaces de {@code java.sql}, visibles desde cualquier
     * cargador de clases, asi que no hay que adivinar el del pool.
     */
    private Object envolverSentencia(java.sql.Statement real, String sqlPreparado) {
        Class<?> interfaz;
        if (real instanceof java.sql.CallableStatement) {
            interfaz = java.sql.CallableStatement.class;
        } else if (real instanceof java.sql.PreparedStatement) {
            interfaz = java.sql.PreparedStatement.class;
        } else {
            interfaz = java.sql.Statement.class;
        }

        return Proxy.newProxyInstance(
                DataSourceInstrumentado.class.getClassLoader(),
                new Class<?>[] {interfaz},
                new ManejadorDeSentencia(real, sqlPreparado));
    }

    /** Mide la ejecucion y registra la muestra, pase lo que pase. */
    private final class ManejadorDeSentencia implements InvocationHandler {

        private final java.sql.Statement real;
        private final String sqlPreparado;

        private ManejadorDeSentencia(java.sql.Statement real, String sqlPreparado) {
            this.real = real;
            this.sqlPreparado = sqlPreparado;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            if (!METODOS_QUE_EJECUTAN.contains(metodo.getName())) {
                return invocar(real, metodo, argumentos);
            }

            String sql = sqlPreparado != null
                    ? sqlPreparado
                    : (argumentos != null && argumentos.length > 0 && argumentos[0] instanceof String texto
                            ? texto
                            : null);

            // nanoTime y no el reloj de pared: un ajuste de NTP puede mover el
            // reloj hacia atras y producir duraciones negativas.
            long inicio = System.nanoTime();
            boolean fallo = false;
            try {
                return invocar(real, metodo, argumentos);
            } catch (Throwable e) {
                fallo = true;
                throw e;
            } finally {
                // En finally: una consulta que revienta suele ser una consulta
                // lenta —un tiempo de espera agotado, por ejemplo— y es
                // justamente la que interesa ver en el registro de lentas.
                long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
                registrarSinRomper(sql, duracionMs, fallo);
            }
        }
    }

    /**
     * La instrumentacion nunca puede tumbar una consulta.
     *
     * <p>Si medir fallara, el jugador perderia una operacion real por culpa de
     * la observabilidad. Se traga el error a proposito.
     */
    private void registrarSinRomper(String sql, long duracionMs, boolean fallo) {
        try {
            registro.registrar(new MuestraDeConsulta(
                    registro.servicio(), sql, duracionMs, reloj.instant(), fallo));
        } catch (RuntimeException ignorada) {
            // sin efecto: medir no puede cambiar el resultado de la consulta
        }
    }

    private static Object invocar(Object destino, Method metodo, Object[] argumentos) throws Throwable {
        try {
            return metodo.invoke(destino, argumentos);
        } catch (InvocationTargetException e) {
            // Sin esto, una SQLException legitima llegaria al servicio envuelta
            // en InvocationTargetException y nadie la reconoceria.
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    // --- Delegacion fiel del resto de DataSource ---

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegado.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegado.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int segundos) throws SQLException {
        delegado.setLoginTimeout(segundos);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegado.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegado.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> tipo) throws SQLException {
        return tipo.isInstance(this) ? tipo.cast(this) : delegado.unwrap(tipo);
    }

    @Override
    public boolean isWrapperFor(Class<?> tipo) throws SQLException {
        return tipo.isInstance(this) || delegado.isWrapperFor(tipo);
    }
}
