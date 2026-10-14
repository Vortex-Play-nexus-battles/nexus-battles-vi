package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Las fuentes de avisos que se incorporan antes de entregar lo pendiente:
 * los cambios del catalogo (HU-NOT-001) y, despues, los cambios en las
 * politicas de uso (SA-11, §7.3.7). Cada una la arma su configuracion y se
 * apaga sola si no puede funcionar; aqui solo se juntan, por su nombre, para
 * que la bandeja siga sin conocer a ninguna.
 */
@Configuration
public class FuentesDeAvisos {

    @Bean
    @Primary
    public AvisosPorIncorporar avisosPorIncorporar(
            @Qualifier("avisosDelCatalogo") AvisosPorIncorporar catalogo,
            @Qualifier("avisosDePoliticas") AvisosPorIncorporar politicas) {
        return AvisosPorIncorporar.todas(List.of(catalogo, politicas));
    }
}
