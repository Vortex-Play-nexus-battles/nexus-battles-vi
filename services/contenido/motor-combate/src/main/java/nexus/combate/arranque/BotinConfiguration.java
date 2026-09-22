package nexus.combate.arranque;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import nexus.combate.CatalogoBotin;
import nexus.combate.ClienteCatalogoBotinHttp;
import nexus.combate.ClienteInventarioBotinHttp;
import nexus.combate.EjecutorAtaqueConBotin;
import nexus.combate.EvaluadorCaida;
import nexus.combate.InventarioBotin;
import nexus.combate.ProcesadorBotin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotinConfiguration {

    @Bean
    CatalogoBotin catalogoBotin(
            @Value("${motor.productos.url}") String productosUrl) {
        return new ClienteCatalogoBotinHttp(URI.create(productosUrl));
    }

    @Bean
    InventarioBotin inventarioBotin(
            @Value("${motor.inventario.url}") String inventarioUrl) {
        return new ClienteInventarioBotinHttp(URI.create(inventarioUrl));
    }

    @Bean
    EvaluadorCaida evaluadorCaida() {
        return new EvaluadorCaida(() -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    ProcesadorBotin procesadorBotin(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            EvaluadorCaida evaluador) {
        return new ProcesadorBotin(inventario, catalogo, evaluador);
    }

    @Bean
    EjecutorAtaqueConBotin ejecutorAtaqueConBotin(ProcesadorBotin procesador) {
        return new EjecutorAtaqueConBotin(procesador);
    }
}
