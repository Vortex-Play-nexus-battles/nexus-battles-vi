package nexus.inventario.persistencia;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registra {@link GeneradorDeIdInventarioDocumento} como auto-configuracion
 * de Spring Boot, no via {@code @Component}/component-scan. Los test
 * slices restrictivos como {@code @DataMongoTest} (que usa
 * RepositorioInventariosMongoIT, de Nicolay) excluyen los
 * {@code @Component} genericos salvo que el propio test los importe
 * explicitamente; las auto-configuraciones, en cambio, siempre se evaluan,
 * sin necesitar tocar ningun test existente.
 */
@AutoConfiguration
public class InventarioPersistenciaAutoConfiguration {

    @Bean
    GeneradorDeIdInventarioDocumento generadorDeIdInventarioDocumento() {
        return new GeneradorDeIdInventarioDocumento();
    }
}
