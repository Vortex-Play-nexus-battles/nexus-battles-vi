package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import nexus.api.ProductoCreado;
import nexus.configuracion.VisibilidadDelLlamador;
import nexus.dominio.EstadoProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.Producto;
import nexus.dominio.Promocion;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Que ve cada quien de un producto y cuando esta vigente una promocion (B4):
 * la vigencia la decide el reloj del servidor, no el cliente.
 */
class ProyeccionDeProductosTest {

        private static final Instant AHORA = Instant.parse("2026-10-05T12:00:00Z");

        private final ProyeccionDeProductos proyeccion = new ProyeccionDeProductos(
                Mappers.getMapper(ProductoMapper.class), Clock.fixed(AHORA, ZoneOffset.UTC));

        @Test
        @DisplayName("una promocion vigente sale para todos, con vigente=true")
        void promocionVigente() {
                Producto conPromocion = producto(new Promocion(20,
                        Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z")));

                ProductoCreado publico = proyeccion.proyectar(conPromocion, Visibilidad.PUBLICA);
                ProductoCreado completo = proyeccion.proyectar(conPromocion, Visibilidad.PRIVILEGIADA);

                assertEquals(20, publico.promocion().porcentaje());
                assertTrue(publico.promocion().vigente());
                assertTrue(completo.promocion().vigente());
        }

        @Test
        @DisplayName("una promocion vencida o futura no se anuncia al publico; la administracion la ve con vigente=false")
        void promocionFueraDeVigencia() {
                Producto vencida = producto(new Promocion(20,
                        Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-05T12:00:00Z")));
                Producto futura = producto(new Promocion(30,
                        Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z")));

                assertNull(proyeccion.proyectar(vencida, Visibilidad.PUBLICA).promocion());
                assertNull(proyeccion.proyectar(futura, Visibilidad.AUTENTICADA).promocion());
                assertFalse(proyeccion.proyectar(vencida, Visibilidad.PRIVILEGIADA).promocion().vigente());
                assertFalse(proyeccion.proyectar(futura, Visibilidad.PRIVILEGIADA).promocion().vigente());
        }

        @Test
        @DisplayName("la vista publica y la de jugador dejan fuera lo interno; la privilegiada lo trae todo")
        void camposInternos() {
                Producto p = producto(null);

                ProductoCreado publico = proyeccion.proyectar(p, Visibilidad.PUBLICA);
                ProductoCreado jugador = proyeccion.proyectar(p, Visibilidad.AUTENTICADA);
                ProductoCreado completo = proyeccion.proyectar(p, Visibilidad.PRIVILEGIADA);

                for (ProductoCreado sinInterno : List.of(publico, jugador)) {
                        assertNull(sinInterno.version());
                        assertNull(sinInterno.tasaDeCaida());
                        assertNull(sinInterno.origen());
                        assertNull(sinInterno.semillaVersion());
                        assertNull(sinInterno.modificadoPor());
                        assertEquals(7, sinInterno.tiraje());
                }
                assertEquals(3, completo.version());
                assertEquals(new BigDecimal("2"), completo.tasaDeCaida());
                assertEquals(OrigenProducto.SEMILLA, completo.origen());
                assertEquals(1, completo.semillaVersion());
                assertEquals("uid-admin", completo.modificadoPor());
        }

        @Test
        @DisplayName("la proyeccion publica clasifica la rareza y expone las habilidades existentes")
        void rarezaYHabilidadesPublicas() {
                Producto arma = new Producto(
                        "arma-1", "Espada", "img", "Arma. Efectos: +3 al ataque. Probabilidad de caída: 10%.",
                        TipoProducto.ARMA, 10, 300, null, false, null, null, null, null, null, null,
                        null, null, null, null, null, 3, new BigDecimal("10"), EstadoProducto.ACTIVO, 1,
                        AHORA, AHORA);
                Producto epica = new Producto(
                        "epica-1", "Tormenta", "img", "Habilidad épica", TipoProducto.EPICA, 1, 900,
                        null, true, null, "heroe-1", null, null, null, 2, "Daño en área",
                        "Duplica el daño", null, null, null, null, null, EstadoProducto.UNICO, 1,
                        AHORA, AHORA);

                ProductoCreado armaPublica = proyeccion.proyectar(arma, Visibilidad.PUBLICA);
                ProductoCreado epicaPublica = proyeccion.proyectar(epica, Visibilidad.PUBLICA);

                assertEquals("COMUN", armaPublica.rareza());
                assertEquals(List.of("+3 al ataque"), armaPublica.habilidades());
                assertEquals("EPICA", epicaPublica.rareza());
                assertEquals(List.of("Daño en área", "Duplica el daño"), epicaPublica.habilidades());
        }

        @Test
        @DisplayName("un SUSPENDIDO existe para un jugador y para la administracion, no para el publico")
        void suspendidos() {
                Producto suspendido = new Producto("s", "S", "img", "d", TipoProducto.ARMA, 1, 1, null, false,
                        null, null, null, null, null, null, null, null, null, null, null, 1,
                        BigDecimal.ONE, EstadoProducto.SUSPENDIDO, 1, AHORA, AHORA);

                assertFalse(Visibilidad.PUBLICA.ve(suspendido));
                assertTrue(Visibilidad.AUTENTICADA.ve(suspendido));
                assertTrue(Visibilidad.PRIVILEGIADA.ve(suspendido));
                assertTrue(Visibilidad.PUBLICA.ve(producto(null)));
        }

        @Test
        @DisplayName("la visibilidad sale de los roles del token: servicio y administradores ven todo")
        void visibilidadPorRol() {
                assertEquals(Visibilidad.PUBLICA, VisibilidadDelLlamador.de(null));
                assertEquals(Visibilidad.PUBLICA, VisibilidadDelLlamador.de(new AnonymousAuthenticationToken(
                        "clave", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))));
                assertEquals(Visibilidad.AUTENTICADA, VisibilidadDelLlamador.de(autenticado("ROLE_JUGADOR")));
                assertEquals(Visibilidad.AUTENTICADA, VisibilidadDelLlamador.de(autenticado("ROLE_MODERADOR")));
                assertEquals(Visibilidad.PRIVILEGIADA, VisibilidadDelLlamador.de(autenticado("ROLE_SERVICIO")));
                assertEquals(Visibilidad.PRIVILEGIADA, VisibilidadDelLlamador.de(autenticado("ROLE_ADMINISTRADOR")));
                assertEquals(Visibilidad.PRIVILEGIADA,
                        VisibilidadDelLlamador.de(autenticado("ROLE_SUPER_ADMINISTRADOR")));
        }

        private static TestingAuthenticationToken autenticado(String rol) {
                TestingAuthenticationToken token = new TestingAuthenticationToken("quien", null, rol);
                token.setAuthenticated(true);
                return token;
        }

        private static Producto producto(Promocion promocion) {
                return new Producto("p-1", "Espada", "img", "Arma", TipoProducto.ARMA, 7, 300,
                        new BigDecimal("6000"), false, null, null, null, null, null, null, null, null, null,
                        null, null, 1, new BigDecimal("2"), EstadoProducto.ACTIVO, 3, AHORA, AHORA,
                        promocion, OrigenProducto.SEMILLA, 1, "uid-admin", null, List.of());
        }
}
