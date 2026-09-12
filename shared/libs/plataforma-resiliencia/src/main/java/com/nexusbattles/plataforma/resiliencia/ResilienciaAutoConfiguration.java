package com.nexusbattles.plataforma.resiliencia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Deja lista la degradacion controlada en el servicio que declare esta
 * biblioteca (HU-DIS-003).
 *
 * <p>No registra ningun {@link CortaCircuitos}: cada uno protege una dependencia
 * concreta y necesita su nombre, su seccion y sus umbrales, que solo conoce el
 * servicio que hace la llamada. Adivinarlos aqui seria inventar configuracion.
 *
 * <p>Lo que si se comparte es el registro —para que el panel sepa que secciones
 * estan limitadas— y el manejador que traduce la excepcion al problem detail,
 * porque ese formato tiene que ser identico en los veinte modulos (regla 4).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
public class ResilienciaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RegistroDeDegradacion registroDeDegradacion() {
        return new RegistroDeDegradacion();
    }

    @Bean
    @ConditionalOnMissingBean
    public ManejadorDeDegradacion manejadorDeDegradacion(
            @Value("${resiliencia.reintentar-en-segundos:30}") long reintentarEnSegundos) {
        return new ManejadorDeDegradacion(reintentarEnSegundos);
    }
}
