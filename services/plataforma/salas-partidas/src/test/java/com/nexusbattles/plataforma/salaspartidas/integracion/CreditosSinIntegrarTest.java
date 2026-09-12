package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El camino real del 503 mientras no exista el modulo de creditos.
 *
 * <p>Aqui no hay dobles del puerto: se usa el adaptador de verdad,
 * {@link CreditosSinIntegrar}, que es el unico bean del puerto en el contexto.
 * Lo que se demuestra es la consecuencia visible de HU-SAL-001 hoy: una sala
 * sin recompensa se crea; una con recompensa se rechaza con 503 y su tipo, y
 * no deja nada escrito.
 */
class CreditosSinIntegrarTest {

    private static final UUID ANFITRION = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final URI TIPO_ESPERADO =
            URI.create("https://nexusbattles.local/errores/creditos-sin-integrar");

    private final CreditosSinIntegrar creditos = new CreditosSinIntegrar();

    @Test
    @DisplayName("reservar no finge saldo: falla con 503 y el tipo creditos-sin-integrar")
    void reservarFallaCon503() {
        assertThatThrownBy(() -> creditos.reservar(ANFITRION, 150, UUID.randomUUID()))
                .isInstanceOf(CreditosSinIntegrar.IntegracionDeCreditosPendiente.class)
                .satisfies(error -> {
                    var pendiente = (CreditosSinIntegrar.IntegracionDeCreditosPendiente) error;
                    assertThat(pendiente.estado()).isEqualTo(503);
                    assertThat(pendiente.tipo()).isEqualTo(TIPO_ESPERADO);
                    assertThat(pendiente.titulo()).isEqualTo("Las apuestas todavia no estan disponibles");
                    assertThat(pendiente.detalle()).contains("salas sin recompensa");
                    assertThat(pendiente.errores()).isEmpty();
                });
    }

    @Test
    @DisplayName("liberar tampoco finge: no hay reservas que devolver")
    void liberarFallaCon503() {
        assertThatThrownBy(() -> creditos.liberar(UUID.randomUUID()))
                .isInstanceOf(CreditosSinIntegrar.IntegracionDeCreditosPendiente.class);
    }

    @Test
    @DisplayName("con el adaptador real, una sala sin recompensa se crea de extremo a extremo")
    void sinRecompensaSeCrea() {
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        CrearSala crearSala = new CrearSala(repositorio, creditos);

        Sala sala = crearSala.ejecutar(parametros(0), ANFITRION);

        assertThat(sala.recompensaCreditos()).isZero();
        assertThat(repositorio.cuantasHay()).isEqualTo(1);
    }

    @Test
    @DisplayName("con el adaptador real, una sala con recompensa responde 503 y no se guarda")
    void conRecompensaNoSeGuarda() {
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        CrearSala crearSala = new CrearSala(repositorio, creditos);

        assertThatThrownBy(() -> crearSala.ejecutar(parametros(320), ANFITRION))
                .isInstanceOf(CreditosSinIntegrar.IntegracionDeCreditosPendiente.class);

        assertThat(repositorio.cuantasHay())
                .as("una sala cuya apuesta nadie pudo reservar no debe existir")
                .isZero();
    }

    private static ParametrosDeSala parametros(int recompensa) {
        return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, recompensa, false, false, null);
    }

    /** Almacen minimo: solo importa si algo quedo escrito. */
    private static final class RepositorioEnMemoria implements RepositorioDeSalas {
        private final Map<UUID, Sala> almacen = new LinkedHashMap<>();

        @Override
        public Sala guardar(Sala sala) {
            almacen.put(sala.id(), sala);
            return sala;
        }

        @Override
        public Optional<Sala> buscarPorId(UUID id) {
            return Optional.ofNullable(almacen.get(id));
        }

        /** Esta prueba nunca lista: una pagina vacia con la forma exacta del puerto. */
        @Override
        public PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado, int pagina, int tamano) {
            return new PaginaDeSalas(List.of(), pagina, tamano, 0L, 0);
        }

        int cuantasHay() {
            return almacen.size();
        }
    }
}
