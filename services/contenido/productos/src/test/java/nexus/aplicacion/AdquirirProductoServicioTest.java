package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import nexus.dominio.AdquisicionRegistrada;
import nexus.dominio.ClaveDeIdempotenciaReutilizadaException;
import nexus.dominio.EstadoProducto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.AdquisicionRegistradaRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.RepositorioDisponibilidadEnMemoria;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * La idempotencia de la reserva de tiraje (B4) sin base: el catalogo en
 * memoria y el registro de claves sobre un mapa. La version contra MongoDB real
 * esta en CatalogoContraMongoIT.
 */
class AdquirirProductoServicioTest {

        private CatalogoProductos catalogo;
        private Map<String, AdquisicionRegistrada> registradas;
        private AdquisicionRegistradaRepository registro;
        private AdquirirProductoServicio servicio;

        @BeforeEach
        void preparar() {
                catalogo = new CatalogoProductos(new RepositorioDisponibilidadEnMemoria());
                catalogo.registrar(DisponibilidadProducto.nueva("dos-unidades", 2, EstadoProducto.ACTIVO));
                registradas = new HashMap<>();
                registro = mock(AdquisicionRegistradaRepository.class);
                when(registro.findById(anyString()))
                        .thenAnswer(i -> Optional.ofNullable(registradas.get(i.getArgument(0, String.class))));
                when(registro.insert(any(AdquisicionRegistrada.class))).thenAnswer(i -> {
                        AdquisicionRegistrada nueva = i.getArgument(0, AdquisicionRegistrada.class);
                        if (registradas.putIfAbsent(nueva.clave(), nueva) != null) {
                                throw new DuplicateKeyException("clave repetida " + nueva.clave());
                        }
                        return nueva;
                });
                when(registro.save(any(AdquisicionRegistrada.class))).thenAnswer(i -> {
                        AdquisicionRegistrada guardada = i.getArgument(0, AdquisicionRegistrada.class);
                        registradas.put(guardada.clave(), guardada);
                        return guardada;
                });
                org.mockito.Mockito.doAnswer(i -> registradas.remove(i.getArgument(0, String.class)))
                        .when(registro).deleteById(anyString());
                servicio = new AdquirirProductoServicio(
                        catalogo, registro, Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC));
        }

        @Test
        @DisplayName("reserva, guarda la clave con su resultado y quien la pidio")
        void reservaYGuardaElResultado() {
                ResultadoAdquisicion resultado = servicio.adquirir("dos-unidades", "orden-1", "ms-ecommerce");

                assertEquals(EstadoAdquisicion.ACEPTADA, resultado.estado());
                AdquisicionRegistrada guardada = registradas.get("orden-1");
                assertEquals(EstadoAdquisicion.ACEPTADA, guardada.estado());
                assertEquals("dos-unidades", guardada.productoId());
                assertEquals("ms-ecommerce", guardada.solicitante());
                assertEquals(1, catalogo.consultar("dos-unidades").unidadesDisponibles());
        }

        @Test
        @DisplayName("la misma clave devuelve el mismo resultado y no vuelve a descontar")
        void mismaClaveMismoResultado() {
                servicio.adquirir("dos-unidades", "orden-1", "ms-ecommerce");
                ResultadoAdquisicion repetida = servicio.adquirir("dos-unidades", "orden-1", "ms-ecommerce");

                assertEquals(EstadoAdquisicion.ACEPTADA, repetida.estado());
                assertEquals(1, catalogo.consultar("dos-unidades").unidadesDisponibles());
        }

        @Test
        @DisplayName("un resultado negativo tambien se repite con su clave: la clave no se reintenta, se cambia")
        void elAgotadoSeRepite() {
                servicio.adquirir("dos-unidades", "a", "s");
                servicio.adquirir("dos-unidades", "b", "s");
                assertEquals(EstadoAdquisicion.AGOTADO, servicio.adquirir("dos-unidades", "c", "s").estado());

                catalogo.registrar(DisponibilidadProducto.nueva("dos-unidades", 5, EstadoProducto.ACTIVO));

                assertEquals(EstadoAdquisicion.AGOTADO, servicio.adquirir("dos-unidades", "c", "s").estado());
                assertEquals(EstadoAdquisicion.ACEPTADA, servicio.adquirir("dos-unidades", "d", "s").estado());
        }

        @Test
        @DisplayName("la misma clave para otro producto es un conflicto y no reserva nada")
        void claveReutilizadaConOtroProducto() {
                catalogo.registrar(DisponibilidadProducto.nueva("otro", 3, EstadoProducto.ACTIVO));
                servicio.adquirir("dos-unidades", "orden-1", "s");

                assertThrows(ClaveDeIdempotenciaReutilizadaException.class,
                        () -> servicio.adquirir("otro", "orden-1", "s"));
                assertEquals(3, catalogo.consultar("otro").unidadesDisponibles());
        }

        @Test
        @DisplayName("un intento que registro la clave y murio antes del resultado se termina sin descontar dos veces")
        void reanudaUnIntentoEnCurso() {
                // Primer intento: la clave quedo en curso y la reserva SI llego a
                // descontar (el catalogo recuerda la clave), pero el resultado no se
                // guardo.
                registradas.put("orden-9", new AdquisicionRegistrada(
                        "orden-9", "dos-unidades", null, null, "s", Instant.EPOCH));
                catalogo.adquirir("dos-unidades", "orden-9");

                ResultadoAdquisicion reintento = servicio.adquirir("dos-unidades", "orden-9", "s");

                assertEquals(EstadoAdquisicion.ACEPTADA, reintento.estado());
                assertEquals(1, catalogo.consultar("dos-unidades").unidadesDisponibles());
                assertEquals(EstadoAdquisicion.ACEPTADA, registradas.get("orden-9").estado());
        }

        @Test
        @DisplayName("si otra peticion con la misma clave la registra primero, se sigue sin reservar dos veces")
        void carreraAlRegistrarLaClave() {
                when(registro.findById("orden-7"))
                        .thenReturn(Optional.empty())
                        .thenAnswer(i -> Optional.ofNullable(registradas.get("orden-7")));
                registradas.put("orden-7", new AdquisicionRegistrada(
                        "orden-7", "dos-unidades", null, null, "s", Instant.EPOCH));

                ResultadoAdquisicion resultado = servicio.adquirir("dos-unidades", "orden-7", "s");

                assertEquals(EstadoAdquisicion.ACEPTADA, resultado.estado());
                assertEquals(1, catalogo.consultar("dos-unidades").unidadesDisponibles());
        }

        @Test
        @DisplayName("un producto inexistente es 404 y no consume la clave")
        void inexistenteNoConsumeLaClave() {
                assertThrows(ProductoNoEncontradoException.class,
                        () -> servicio.adquirir("no-existe", "orden-404", "s"));

                assertFalse(registradas.containsKey("orden-404"));
        }

        @Test
        @DisplayName("un suspendido responde SUSPENDIDO y queda registrado con su clave")
        void suspendido() {
                catalogo.suspender("dos-unidades");

                ResultadoAdquisicion resultado = servicio.adquirir("dos-unidades", "orden-s", "s");

                assertEquals(EstadoAdquisicion.SUSPENDIDO, resultado.estado());
                assertTrue(registradas.containsKey("orden-s"));
                assertEquals(EstadoAdquisicion.SUSPENDIDO, registradas.get("orden-s").estado());
        }

        @Test
        @DisplayName("una clave recien registrada esta en curso hasta que se guarda su resultado")
        void enCursoMientrasNoHayResultado() {
                AdquisicionRegistrada enCurso = new AdquisicionRegistrada(
                        "k", "p", null, null, "s", Instant.EPOCH);

                assertTrue(enCurso.enCurso());
                assertNull(enCurso.mensaje());
        }
}
