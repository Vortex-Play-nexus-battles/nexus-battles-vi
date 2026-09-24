package com.nexusbattles.plataforma.correo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CorreoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorreoApplication.class, args);
    }

    /**
     * El reloj se inyecta para que las pruebas puedan fijar el instante de un
     * envio. En produccion es el del sistema.
     */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * Quien entrega los correos.
     *
     * <p>Fuera del hilo de la peticion, porque un proveedor real tarda uno a
     * tres segundos y quien pide el correo no tiene por que esperarlos: el
     * cliente de ms-identidad corta a los dos y reintentaria un envio que si
     * salio.
     *
     * <p>Hilos virtuales: entregar un correo es esperar a la red casi todo el
     * tiempo, que es exactamente lo que un hilo virtual hace bien y lo que un
     * hilo de plataforma desperdicia. El limite de concurrencia evita que una
     * rafaga abra cien conexiones contra el proveedor y se gane un bloqueo por
     * exceso de envios.
     *
     * <p>{@code correo.envio-asincrono=false} lo vuelve sincrono en el mismo
     * hilo. Las pruebas lo usan para poder comprobar el resultado sin esperas.
     */
    @Bean
    public Executor ejecutorDeCorreo(
            @Value("${correo.envio-asincrono:true}") boolean asincrono,
            @Value("${correo.envios-simultaneos:4}") int simultaneos) {
        if (!asincrono) {
            return Runnable::run;
        }
        SimpleAsyncTaskExecutor ejecutor = new SimpleAsyncTaskExecutor("correo-");
        ejecutor.setVirtualThreads(true);
        ejecutor.setConcurrencyLimit(simultaneos);
        return ejecutor;
    }
}