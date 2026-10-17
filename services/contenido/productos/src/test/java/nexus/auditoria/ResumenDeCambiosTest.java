package nexus.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.Producto;
import nexus.dominio.Promocion;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Valores antes y después de una operación del catálogo (SA-18)")
class ResumenDeCambiosTest {

        private static final Instant FECHA = Instant.parse("2026-10-14T10:00:00Z");

        private static Producto armadura(int tiraje, BigDecimal precioReal, boolean premium, Promocion promocion,
                        String descripcion) {
                return new Producto(
                                "p-1", "Yelmo del alba", "img", descripcion, TipoProducto.ARMADURA, tiraje, null,
                                precioReal, premium, null, null, null, null, null, null, null, null, 4,
                                ParteArmadura.CASCO, null, null, null, EstadoProducto.ACTIVO, 1, FECHA, FECHA,
                                promocion, null, null, null, null, List.of());
        }

        @Test
        @DisplayName("un alta premium nombra su precio en moneda real y que es premium")
        void altaPremium() {
                assertThat(ResumenDeCambios.altaDe(armadura(3, new BigDecimal("25000.00"), true, null, "x")))
                                .isEqualTo("Tipo: Armadura · Estado: Activo · Tiraje: 3 · Precio en moneda real: 25000"
                                                + " · Premium: sí");
        }

        @Test
        @DisplayName("una promoción nueva se lee con su porcentaje y su periodo")
        void promocion() {
                Promocion promocion = new Promocion(20, Instant.parse("2026-10-15T05:00:00Z"),
                                Instant.parse("2026-10-20T05:00:00Z"));

                ResumenDeCambios.Cambio cambio = ResumenDeCambios.entre(
                                armadura(3, BigDecimal.TEN, false, null, "x"),
                                armadura(3, BigDecimal.TEN, false, promocion, "x"));

                assertThat(cambio.antes()).isEqualTo("Promoción: —");
                assertThat(cambio.despues())
                                .isEqualTo("Promoción: 20 % del 2026-10-15 05:00 UTC al 2026-10-20 05:00 UTC");
        }

        @Test
        @DisplayName("sin campos distintos no hay valores que mostrar")
        void sinCambios() {
                Producto igual = armadura(3, BigDecimal.TEN, false, null, "x");

                assertThat(ResumenDeCambios.entre(igual, igual)).isEqualTo(new ResumenDeCambios.Cambio(null, null));
                assertThat(ResumenDeCambios.tocaElBalance(igual, igual)).isFalse();
                assertThat(ResumenDeCambios.tocaElBalance(null, igual)).isFalse();
        }

        @Test
        @DisplayName("una descripción larga se recorta con puntos suspensivos")
        void textoLargo() {
                ResumenDeCambios.Cambio cambio = ResumenDeCambios.entre(
                                armadura(3, BigDecimal.TEN, false, null, "corta"),
                                armadura(3, BigDecimal.TEN, false, null, "larga ".repeat(60)));

                assertThat(cambio.despues()).startsWith("Descripción: larga larga").endsWith("…");
                assertThat(cambio.despues().length())
                                .isLessThanOrEqualTo("Descripción: ".length() + ResumenDeCambios.LARGO_DE_TEXTO);
        }

        @Test
        @DisplayName("los valores se dicen en palabras")
        void legible() {
                assertThat(ResumenDeCambios.legible(null)).isEqualTo("—");
                assertThat(ResumenDeCambios.legible(Boolean.FALSE)).isEqualTo("no");
                assertThat(ResumenDeCambios.legible(new BigDecimal("1.500"))).isEqualTo("1.5");
                assertThat(ResumenDeCambios.legible(EstadoProducto.SUSPENDIDO)).isEqualTo("Suspendido");
                assertThat(ResumenDeCambios.legible(TipoProducto.EPICA)).isEqualTo("Épica");
                assertThat(ResumenDeCambios.legible(ParteArmadura.PANTALON)).isEqualTo("Pantalón");
                assertThat(ResumenDeCambios.legible(12)).isEqualTo("12");
        }

        @Test
        @DisplayName("lo que falta no se resume")
        void nulos() {
                assertThat(ResumenDeCambios.altaDe(null)).isNull();
                assertThat(ResumenDeCambios.deBanner(null)).isNull();
                assertThat(ResumenDeCambios.deRecurso(null)).isNull();
                assertThat(ResumenDeCambios.recortado(null)).isNull();
        }
}
