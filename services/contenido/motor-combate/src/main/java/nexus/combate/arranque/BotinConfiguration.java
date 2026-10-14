package nexus.combate.arranque;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import nexus.combate.CatalogoBotin;
import nexus.combate.ClienteCatalogoBotinHttp;
import nexus.combate.ClienteInventarioBotinHttp;
import nexus.combate.ClienteTransferidorEquipoHttp;
import nexus.combate.EjecutorAtaqueConBotin;
import nexus.combate.EvaluadorCaida;
import nexus.combate.FabricaProcesadorPerdidaEquipo;
import nexus.combate.InventarioBotin;
import nexus.combate.ProcesadorBotin;
import nexus.combate.TransferidorEquipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotinConfiguration {

    @Bean
    CatalogoBotin catalogoBotin(
            @Value("${motor.productos.url}") String productosUrl,
            ObjectProvider<TokenDeServicio> token) {
        return new ClienteCatalogoBotinHttp(
                URI.create(productosUrl),
                java.net.http.HttpClient.newHttpClient(),
                token.getIfAvailable());
    }

    @Bean
    InventarioBotin inventarioBotin(
            @Value("${motor.inventario.url}") String inventarioUrl,
            ObjectProvider<TokenDeServicio> token) {
        return new ClienteInventarioBotinHttp(
                URI.create(inventarioUrl),
                java.net.http.HttpClient.newHttpClient(),
                token.getIfAvailable());
    }

    @Bean
    TransferidorEquipo transferidorEquipo(
            @Value("${motor.inventario.url}") String inventarioUrl,
            ObjectProvider<TokenDeServicio> token) {
        return new ClienteTransferidorEquipoHttp(
                URI.create(inventarioUrl),
                java.net.http.HttpClient.newHttpClient(),
                token.getIfAvailable());
    }

    @Bean
    EvaluadorCaida evaluadorCaida() {
        return new EvaluadorCaida(() -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    FabricaProcesadorPerdidaEquipo fabricaProcesadorPerdidaEquipo(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            TransferidorEquipo transferidor) {
        return new FabricaProcesadorPerdidaEquipo(
                inventario,
                catalogo,
                transferidor,
                limite -> ThreadLocalRandom.current().nextInt(limite));
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
