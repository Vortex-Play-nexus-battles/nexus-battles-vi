package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.ComposicionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.EnviadorCorreoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Cablea la cola persistente (B1).
 *
 * <p>La politica -reintentos, estados, minimizacion- no conoce Spring y se
 * prueba sin contexto. Aqui solo se decide quien es cada pieza en tiempo de
 * ejecucion y si hay rondas programadas: con
 * {@code correo.entrega.activa=false} (las pruebas de integracion) el
 * trabajador existe pero nadie lo mueve solo.
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeLaCola {

    @Bean
    PoliticaDeReintentos politicaDeReintentos(ConfiguracionDeEntrega configuracion) {
        return new PoliticaDeReintentos(configuracion.esperas(), configuracion.maxIntentos());
    }

    @Bean
    MaquinaDeEstados maquinaDeEstados(PoliticaDeReintentos politica) {
        return new MaquinaDeEstados(politica);
    }

    @Bean
    TrabajadorDeEntrega trabajadorDeEntrega(
            RepositorioDeEnvios repositorio,
            EnviadorCorreoService enviador,
            ComposicionDeCorreo composicion,
            MaquinaDeEstados maquina,
            ConfiguracionDeEntrega configuracion,
            MetricasDeCorreo metricas,
            PlatformTransactionManager transacciones,
            Clock reloj) {
        return new TrabajadorDeEntrega(
                repositorio,
                enviador,
                composicion,
                maquina,
                configuracion,
                metricas,
                new TransactionTemplate(transacciones),
                reloj);
    }

    /**
     * Las rondas programadas, salvo que se apaguen EXPLICITAMENTE con
     * {@code false}. Una variable vacia en el {@code .env} no puede dejar la
     * cola sin nadie que la vacie: los correos se acumularian con 202 sin
     * salir nunca, que es peor que cualquier fallo visible.
     */
    @Bean
    @ConditionalOnExpression("!'${correo.entrega.activa:true}'.trim().equalsIgnoreCase('false')")
    ProgramacionDeEntrega programacionDeEntrega(
            TrabajadorDeEntrega trabajador, ConfiguracionDeEntrega configuracion) {
        return new ProgramacionDeEntrega(trabajador, configuracion);
    }
}
