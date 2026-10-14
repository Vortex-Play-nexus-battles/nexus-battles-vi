package nexus.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * SA-18 — el lote de operaciones contra MongoDB de verdad.
 *
 * <p>Las pruebas de API usan el doble de {@code ConfiguracionOperacionesParaPruebas};
 * aquí el repositorio es el que arma Spring Data y se fijan los bordes de los
 * que depende la bitácora central para no perder ni repetir nada: {@code desde}
 * exclusivo, el límite que no parte una marca de tiempo, el orden estable y el
 * historial de un objeto.
 */
@DataMongoTest
@Testcontainers
class OperacionesAdministrativasMongoTest {

        @Container
        @ServiceConnection
        static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

        private static final Instant AHORA = Instant.parse("2026-10-14T16:00:00Z");

        @Autowired
        private OperacionAdministrativaRepository repositorio;

        private RegistroDeOperaciones registro;

        @BeforeEach
        void limpiar() {
                repositorio.deleteAll();
                registro = new RegistroDeOperaciones(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        private void guardar(String id, String instante, ObjetoAuditado objeto, String objetoId) {
                repositorio.insert(new OperacionAdministrativa(id, Instant.parse(instante), "uid-admin",
                                TipoOperacion.ALTA_BANNER, objeto, objetoId, "n", null, "v", null, "d", "1.1.1.1"));
        }

        @Test
        @DisplayName("sin desde entran todas, de la más antigua a la más reciente, y nada posterior a ahora")
        void todasDesdeLaPrimera() {
                guardar("b", "2026-10-12T10:00:00Z", ObjetoAuditado.BANNER, "x");
                guardar("a", "2026-10-11T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");
                guardar("futura", "2026-10-15T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");

                LoteDeOperaciones lote = registro.consultarLote(null, 50);

                assertThat(lote.operaciones()).extracting(OperacionAdministrativa::id).containsExactly("a", "b");
                assertThat(lote.hasta()).isEqualTo(Instant.parse("2026-10-12T10:00:00Z"));
                assertThat(lote.completo()).isTrue();
        }

        @Test
        @DisplayName("desde es exclusivo: lo ya entregado no vuelve")
        void desdeExclusivo() {
                guardar("a", "2026-10-11T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");
                guardar("b", "2026-10-12T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");

                LoteDeOperaciones lote = registro.consultarLote(Instant.parse("2026-10-11T10:00:00Z"), 50);

                assertThat(lote.operaciones()).extracting(OperacionAdministrativa::id).containsExactly("b");
        }

        @Test
        @DisplayName("el límite no parte una marca de tiempo y el siguiente lote sigue donde quedó")
        void corteYContinuacion() {
                guardar("a", "2026-10-11T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");
                guardar("c", "2026-10-12T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");
                guardar("b", "2026-10-12T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");
                guardar("d", "2026-10-13T10:00:00Z", ObjetoAuditado.PRODUCTO, "p");

                LoteDeOperaciones primero = registro.consultarLote(null, 2);
                LoteDeOperaciones segundo = registro.consultarLote(primero.hasta(), 2);

                assertThat(primero.operaciones()).extracting(OperacionAdministrativa::id)
                                .containsExactly("a", "b", "c");
                assertThat(primero.completo()).isFalse();
                assertThat(segundo.operaciones()).extracting(OperacionAdministrativa::id).containsExactly("d");
                assertThat(segundo.completo()).isTrue();
        }

        @Test
        @DisplayName("el historial de un banner trae solo lo suyo, de lo más reciente a lo más antiguo")
        void historialDeUnObjeto() {
                guardar("alta", "2026-10-11T10:00:00Z", ObjetoAuditado.BANNER, "banner-1");
                guardar("retiro", "2026-10-12T10:00:00Z", ObjetoAuditado.BANNER, "banner-1");
                guardar("otro", "2026-10-12T11:00:00Z", ObjetoAuditado.BANNER, "banner-2");
                guardar("producto", "2026-10-12T12:00:00Z", ObjetoAuditado.PRODUCTO, "banner-1");

                assertThat(registro.historialDe(ObjetoAuditado.BANNER, "banner-1"))
                                .extracting(OperacionAdministrativa::id)
                                .containsExactly("retiro", "alta");
        }
}
