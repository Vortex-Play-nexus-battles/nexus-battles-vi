package nexus.misiones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.aplicacion.EstrategiaDeEnemigos;
import nexus.misiones.aplicacion.EstrategiasPredefinidas;
import nexus.misiones.catalogo.CatalogoDeEstrategiasDesdeSemilla;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El cableado de las estrategias predefinidas de los enemigos (HU-SIM-004): lo que el servicio arma al arrancar. */
class ConfiguracionDeEstrategiasTest {

    private final ConfiguracionDeMisiones configuracion = new ConfiguracionDeMisiones();

    @Test
    @DisplayName("al arrancar se carga la semilla de estrategias completa y sin rechazos")
    void elCatalogoDeLaSemilla() {
        CatalogoDeEstrategiasDeEnemigos catalogo = configuracion.catalogoDeEstrategiasDeEnemigos();

        assertThat(catalogo).isInstanceOf(CatalogoDeEstrategiasDesdeSemilla.class);
        CatalogoDeEstrategiasDesdeSemilla semilla = (CatalogoDeEstrategiasDesdeSemilla) catalogo;
        assertThat(semilla.rechazadas()).isEmpty();
        assertThat(semilla.todas()).hasSize(24);
    }

    @Test
    @DisplayName("la estrategia de los enemigos es la predefinida con la heuristica de respaldo")
    void laEstrategiaDeLosEnemigos() {
        Dobles.Heroes heroes = new Dobles.Heroes();
        EstrategiaDeEnemigos estrategia = configuracion.estrategiaDeEnemigos(
                configuracion.catalogoDeEstrategiasDeEnemigos(), heroes);

        assertThat(estrategia).isInstanceOf(EstrategiasPredefinidas.class);
        assertThat(estrategia.elegir("Guerrero Tanque", 1).origen()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
        // Un prototipo que la Tabla 7 no conoce cae a la heuristica y no rompe.
        assertThat(estrategia.elegir("Bardo", 1).origen()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
    }
}
