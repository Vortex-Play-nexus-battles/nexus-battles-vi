package com.nexusbattles.plataforma.observabilidad;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Envuelve el {@code DataSource} de cualquier servicio para medir sus consultas
 * (HU-REN-003, CA-01).
 *
 * <p>Es un {@code BeanPostProcessor} y no una configuracion normal porque el
 * {@code DataSource} lo crea Spring Boot a partir de las propiedades del
 * servicio: no se puede sustituir sin entrar en un ciclo. Interceptarlo cuando
 * ya esta construido es la unica forma de instrumentarlo <b>sin pedirle a
 * ningun equipo que cambie su configuracion</b>, que es la razon de ser de esta
 * biblioteca.
 *
 * <p>Un servicio sin base de datos no se ve afectado: si no hay
 * {@code DataSource}, no hay nada que envolver.
 */
public class InstrumentadorDeDataSource implements BeanPostProcessor {

    private final ObjectProvider<RegistroDeConsultas> registro;
    private final ObjetivoDeReloj reloj;

    public InstrumentadorDeDataSource(
            ObjectProvider<RegistroDeConsultas> registro, ObjectProvider<Clock> reloj) {
        this.registro = registro;
        this.reloj = () -> reloj.getIfAvailable(Clock::systemUTC);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String nombre) throws BeansException {
        if (bean instanceof DataSource fuente && !(bean instanceof DataSourceInstrumentado)) {
            RegistroDeConsultas destino = registro.getIfAvailable();
            if (destino != null) {
                return new DataSourceInstrumentado(fuente, destino, reloj.obtener());
            }
        }
        return bean;
    }

    /** Resuelve el reloj tarde, para no forzar su creacion al arrancar. */
    @FunctionalInterface
    private interface ObjetivoDeReloj {
        Clock obtener();
    }
}
