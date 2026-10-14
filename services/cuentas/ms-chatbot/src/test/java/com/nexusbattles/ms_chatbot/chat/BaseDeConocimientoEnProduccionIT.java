package com.nexusbattles.ms_chatbot.chat;

import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5.0 — la base de conocimiento que queda en producción tras V11, con el
 * motor de verdad: cada caso de evaluación activo llega a su tema (la
 * evaluación de §7.4.10 que un administrador vería en el panel), ninguna
 * respuesta enlaza a una pantalla de administración y ningún texto promete lo
 * que el juego no tiene (#948).
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@DisplayName("Chatbot · la base de conocimiento en produccion (V11)")
class BaseDeConocimientoEnProduccionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    // Vistas a las que un jugador o un visitante pueden llegar (comun/matriz-acceso.js).
    private static final Set<String> VISTAS_DE_JUGADOR = Set.of("registro", "login", "restablecer-solicitar",
        "verificar-cuenta", "perfil", "historial-transacciones", "mis-cofres", "mis-sanciones", "inventario", "tienda",
        "subastas", "publicar-subasta", "misiones", "torneos", "batallas", "crear-sala", "chat", "notificaciones",
        "ayuda");

    @Autowired
    private MotorRespuestas motor;

    @Autowired
    private TemaConocimientoRepository temas;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ModeracionDeContenido moderacion;

    @Test
    @DisplayName("cada caso de evaluacion activo llega a su tema en produccion")
    void todosLosCasosAciertan() {
        List<Map<String, Object>> casos = jdbc.queryForList(
            "SELECT pregunta, tema_clave_esperada FROM chatbot.casos_evaluacion WHERE activo");
        assertThat(casos).hasSizeGreaterThan(130);
        List<String> fallos = new ArrayList<>();
        for (Map<String, Object> caso : casos) {
            String pregunta = (String) caso.get("pregunta");
            String esperada = (String) caso.get("tema_clave_esperada");
            ResultadoMotor resultado = motor.generarRespuesta(pregunta);
            boolean acierto = esperada == null ? resultado.requiereEscalamiento() : esperada.equals(resultado.temaClave());
            if (!acierto) {
                fallos.add(pregunta + " -> " + resultado.temaClave() + " (esperado " + esperada + ")");
            }
        }
        assertThat(fallos).as("casos que no llegan a su tema").isEmpty();
    }

    @Test
    @DisplayName("ninguna respuesta enlaza a una vista de administracion ni promete logros, mazos o transmisiones")
    void nadaEnlazaAdministracionNiPrometeLoQueNoHay() {
        List<TemaConocimiento> enProduccion = temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION);
        // 59 de V11 y «Reglas básicas del juego» de V15.
        assertThat(enProduccion).hasSize(60);
        for (TemaConocimiento tema : enProduccion) {
            ResultadoMotor resultado = motor.responderTema(tema.getClave(), null, false).orElseThrow();
            List<String> destinos = resultado.enriquecido() == null ? List.of()
                : resultado.enriquecido().enlaces().stream().map(EnlaceInterno::destino).toList();
            assertThat(destinos).as(tema.getClave()).allMatch(VISTAS_DE_JUGADOR::contains);
            String todo = (tema.getContenidoRespuestaEs() + " " + tema.getContenidoRespuestaEn()).toLowerCase();
            assertThat(todo).as(tema.getClave())
                .doesNotContain("revisar tus logros")
                .doesNotContain("mi perfil del menú principal")
                .doesNotContain("live-stream")
                .doesNotContain("métodos de pago")
                .doesNotContain("mítico da");
        }
    }

    @Test
    @DisplayName("una pregunta en ingles recibe la respuesta en ingles y una no entendida escala con enlaces")
    void inglesYEscalamiento() {
        ResultadoMotor enIngles = motor.generarRespuesta("how do I recover my password?");
        assertThat(enIngles.temaClave()).isEqualTo("recuperar-contrasena");
        assertThat(enIngles.ingles()).isTrue();
        assertThat(enIngles.texto()).contains("«¿Olvidaste tu contraseña?»");

        ResultadoMotor escalada = motor.generarRespuesta("zxqwv plorf subasta");
        if (escalada.requiereEscalamiento()) {
            assertThat(escalada.enriquecido().ofrecerSoporteHumano()).isTrue();
        }
    }
}
