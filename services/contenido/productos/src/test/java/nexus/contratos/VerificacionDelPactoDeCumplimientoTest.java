package nexus.contratos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Consumer;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.time.Instant;
import java.util.List;
import nexus.auditoria.ObjetoAuditado;
import nexus.auditoria.OperacionAdministrativa;
import nexus.auditoria.OperacionAdministrativaRepository;
import nexus.auditoria.TipoOperacion;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Productos entrega sus operaciones administrativas como la bitácora central
 * espera leerlas — SA-18. Cierra {@code contracts/pactos/ms-cumplimiento-productos.json}
 * (lo genera {@code ProductosPactoTest} de ms-cumplimiento): el lote con
 * {@code hasta} y {@code completo}, y de cada operación lo que pasa a la
 * bitácora inmutable.
 *
 * <p>El servicio arrancado en un puerto real, con la cadena de seguridad de
 * verdad (la ruta es solo para servicios, y de ellos solo para la bitácora) y
 * el caso de uso de verdad ({@code RegistroDeOperaciones}, con su corte de
 * lotes); el almacén es el doble de {@code ConfiguracionOperacionesParaPruebas},
 * que cada estado siembra. Sin MongoDB: la verificación no se salta en una
 * máquina sin Docker. La consulta contra Mongo de verdad la cubre
 * {@code OperacionesAdministrativasMongoTest}.
 *
 * <p>El pacto no graba {@code Authorization}: cada petición se firma con un
 * token de servicio de ms-cumplimiento del emisor de prueba (RS256 y JWKS por
 * HTTP, como en producción).
 */
@Provider("productos")
@Consumer("ms-cumplimiento")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Pacto: productos cumple lo que la bitácora central espera de las operaciones del catálogo")
class VerificacionDelPactoDeCumplimientoTest {

        /** El cliente de servicio de la bitácora: el único lector autorizado por omisión. */
        private static final String BITACORA = "ms-cumplimiento";

        @DynamicPropertySource
        static void identidad(DynamicPropertyRegistry registro) {
                EmisorDeTokensDePrueba.registrarJwks(registro);
        }

        @LocalServerPort
        private int puerto;

        @Autowired
        @Qualifier("operacionesAdministrativasSimuladas")
        private OperacionAdministrativaRepository operaciones;

        @BeforeEach
        void apuntarAlServicioArrancado(PactVerificationContext contexto) {
                contexto.setTarget(new HttpTestTarget("localhost", puerto));
        }

        @TestTemplate
        @ExtendWith(PactVerificationInvocationContextProvider.class)
        void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
                peticion.addHeader("Authorization",
                                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio(BITACORA));
                contexto.verifyInteraction();
        }

        // ------------------------------------------------------------- estados

        @State("hay operaciones administrativas del catalogo")
        void conOperaciones() {
                reset(operaciones);
                when(operaciones.buscarPrimerasEntre(any(), any(), any())).thenReturn(List.of(
                                new OperacionAdministrativa(
                                                "7b1f0c9e-2a44-4f7b-9d1e-3c5a8b2e6f10",
                                                Instant.parse("2026-10-14T15:00:00.250Z"),
                                                "8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1",
                                                TipoOperacion.SUSPENSION_PRODUCTO,
                                                ObjetoAuditado.PRODUCTO,
                                                "550e8400-e29b-41d4-a716-446655440000",
                                                "Espada solar",
                                                "Estado: Activo",
                                                "Estado: Suspendido",
                                                "Rompe el balance de la temporada",
                                                "Suspensión del producto",
                                                "181.52.10.4")));
        }

        @State("no hay operaciones administrativas del catalogo")
        void sinOperaciones() {
                reset(operaciones);
                when(operaciones.buscarPrimerasEntre(any(), any(), any())).thenReturn(List.of());
        }
}
