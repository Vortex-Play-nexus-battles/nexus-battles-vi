package com.nexusbattles.ms_subastas.config;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.service.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Evalua las dependencias despues del escaneo de componentes, no durante el mismo.
 *
 * <p>Desde B8 el incremento minimo no llega aqui como propiedad: lo lee el
 * servicio de {@link FuenteDeReglas} (admin-parametros) en cada publicacion.
 */
@AutoConfiguration
@ConditionalOnBean({InventarioPublicacionClient.class, CatalogoProductosClient.class, FinanzasPublicacionClient.class,
        IdentidadClient.class, SancionesClient.class})
public class PublicacionAutoConfiguration {
    @Bean
    PublicarSubastaApplicationService publicarSubastaApplicationService(SubastaRepository subastas,
            InventarioPublicacionClient inventario, CatalogoProductosClient catalogo, FinanzasPublicacionClient finanzas,
            IdentidadClient identidad, SancionesClient sanciones, IdempotenciaPublicacion idempotencia,
            CalculadorComisionPublicacion comisiones, Clock clock, FuenteDeReglas reglas, AvisosDeSubasta avisos,
            ApplicationEventPublisher eventos) {
        return new PublicarSubastaApplicationService(subastas, inventario, catalogo, finanzas, identidad,
                sanciones, idempotencia, comisiones, clock, reglas, avisos, eventos);
    }
}
