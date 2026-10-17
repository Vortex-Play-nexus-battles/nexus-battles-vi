package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.conocimiento.BaseConocimientoAdminService;
import com.nexusbattles.ms_chatbot.chat.conocimiento.DespliegueProgramadoTarea;
import com.nexusbattles.ms_chatbot.chat.conocimiento.EvaluacionBaseConocimientoService;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 7.4.9 «Sistema de cache para respuestas frecuentes» (ms-chatbot.yaml 1.9.0)
 * de punta a punta: la aplicacion entera, PostgreSQL real con la base de V11
 * en produccion y {@code POST /chat/mensajes} de verdad.
 *
 * <p>La medida no es la metrica del propio codigo: es el repositorio de temas
 * (espiado) y las estadisticas de Hibernate, que cuentan cuantas filas de
 * {@code temas_conocimiento} se cargaron. Una pregunta repetida no carga
 * ninguna; desplegar una version nueva vuelve a leer los temas (los de la
 * nueva) y descarta lo guardado con la anterior.
 */
@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@DisplayName("Chatbot · respuestas frecuentes en cache contra PostgreSQL (7.4.9)")
class RespuestasFrecuentesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry metricas;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private BaseConocimientoAdminService baseAdmin;

    @Autowired
    private EvaluacionBaseConocimientoService evaluacion;

    @Autowired
    private VersionBaseConocimientoRepository versiones;

    @MockitoSpyBean
    private TemaConocimientoRepository temas;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    // La publicacion programada no se mete en las versiones de la prueba.
    @MockitoBean
    private DespliegueProgramadoTarea tareaProgramada;

    private Statistics estadisticas;

    @BeforeEach
    void preparar() {
        estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
    }

    private String preguntar(String texto) throws Exception {
        String cuerpo = mvc.perform(post("/chat/mensajes")
                .header(CABECERA, "visitante-" + UUID.randomUUID())
                .contentType("application/json")
                .content("{\"contenido\":\"" + texto + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.contenido");
    }

    private double contador(String nombre) {
        return metricas.counter(nombre).count();
    }

    private long temasCargados() {
        return estadisticas.getEntityStatistics(TemaConocimiento.class.getName()).getLoadCount();
    }

    private UUID produccion() {
        return versiones.buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION).orElseThrow();
    }

    @Test
    @DisplayName("una pregunta repetida se responde sin leer los temas de la base ni volver a puntuarlos")
    void unaPreguntaRepetidaNoVuelveALaBase() throws Exception {
        String primera = preguntar("¿Cómo publicar un producto en subasta?");
        double aciertos = contador("chatbot.cache.respuestas.aciertos");
        clearInvocations(temas);
        long cargadosAntes = temasCargados();

        // Otro visitante, la misma pregunta escrita de otra forma.
        String segunda = preguntar("como publicar un PRODUCTO en subasta");

        assertThat(segunda).isEqualTo(primera);
        verify(temas, never()).findByVersionIdAndActivoTrue(any());
        verify(temas, never()).findByVersionEstadoAndActivoTrue(any());
        assertThat(temasCargados() - cargadosAntes).as("filas de temas_conocimiento cargadas").isZero();
        assertThat(contador("chatbot.cache.respuestas.aciertos")).isEqualTo(aciertos + 1);
    }

    @Test
    @DisplayName("desplegar y revertir la base cambian la version: la misma pregunta se calcula con los temas nuevos")
    void desplegarYRevertirDescartanLoGuardado() throws Exception {
        String pregunta = "¿Cómo crear una cuenta?";
        String antes = preguntar(pregunta);
        UUID original = produccion();
        double desalojosPorVersion = metricas.counter("chatbot.cache.respuestas.desalojos", "causa", "version").count();

        // Una candidata identica a produccion pasa la evaluacion y se despliega.
        baseAdmin.crearCandidata("Copia para la prueba de la cache (7.4.9)");
        evaluacion.desplegarCandidata();
        UUID nueva = produccion();
        assertThat(nueva).isNotEqualTo(original);

        clearInvocations(temas);
        double fallos = contador("chatbot.cache.respuestas.fallos");
        String despues = preguntar(pregunta);

        assertThat(despues).isEqualTo(antes);
        verify(temas, times(1)).findByVersionIdAndActivoTrue(nueva);
        assertThat(contador("chatbot.cache.respuestas.fallos")).isEqualTo(fallos + 1);
        assertThat(metricas.counter("chatbot.cache.respuestas.desalojos", "causa", "version").count())
            .isGreaterThan(desalojosPorVersion);

        // Revertir vuelve a la original: otra version, otra lectura de sus temas.
        evaluacion.revertir();
        clearInvocations(temas);
        preguntar(pregunta);
        verify(temas, times(1)).findByVersionIdAndActivoTrue(original);
    }
}
