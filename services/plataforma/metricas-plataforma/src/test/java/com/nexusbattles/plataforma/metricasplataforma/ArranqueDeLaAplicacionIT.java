package com.nexusbattles.plataforma.metricasplataforma;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.AlmacenDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.AlmacenEnPostgres;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.InformeDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Interrupcion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.RegistroDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.VentanaDeMantenimiento;
import com.nexusbattles.plataforma.metricasplataforma.latencia.LatenciaController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Levanta la aplicacion completa, igual que hace el contenedor en el
 * servidor: los controladores de latencia, consultas y disponibilidad, el
 * monitor con su tarea programada y la instrumentacion que aporta
 * shared/libs/plataforma-observabilidad desde las convenciones.
 *
 * <p>Por que existe: el 2026-09-17 el servicio de comentarios no arranco en
 * el host de desarrollo por una dependencia de ejecucion que faltaba, con el
 * build en verde, porque ninguna de sus pruebas cargaba el contexto de
 * Spring. Este modulo tenia seis pruebas, todas rebanadas {@code @WebMvcTest}:
 * ninguna habria detectado lo mismo aqui.
 *
 * <p>El monitor sondea servicios que en una maquina de pruebas no existen, y
 * eso esta bien: la sonda registra la caida y sigue. Lo que se comprueba es
 * que el contexto levanta, no que los servicios respondan.
 *
 * <p>La segunda prueba fija los puertos del mapa de disponibilidad, que hasta
 * el 2026-09-17 tenia cuatro entradas apuntando al puerto de OTRO servicio
 * (HU-DIS-001 medía servicios distintos de los que nombraba).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueDeLaAplicacionIT {

    /**
     * HU-DIS-001: desde que las interrupciones se guardan, el contexto
     * necesita la PostgreSQL con la que Flyway crea el esquema {@code metricas}.
     * Sin {@code disabledWithoutDocker}, como en salas-partidas: una prueba
     * omitida no es una prueba que pasa.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("HU-DIS-001: una caida sobrevive al reinicio del servicio y la cierra la primera comprobacion sana")
    void lasInterrupcionesSobrevivenAlReinicio() {
        // El monitor real de este contexto ya esta sondeando (y anotando como
        // caidos) los siete servicios del bloque en la misma base: por eso la
        // prueba usa un servicio propio y un periodo del pasado, para que sus
        // filas no se mezclen con las que el monitor va escribiendo en vivo.
        AlmacenDeDisponibilidad almacen = new AlmacenEnPostgres(jdbc);
        String servicio = "sonda-de-prueba";
        Instant t0 = Instant.parse("2026-01-10T10:00:00Z");

        // Antes del «reinicio»: un registro ve caer al servicio y programa
        // una ventana de mantenimiento.
        RegistroDeDisponibilidad antes = new RegistroDeDisponibilidad(almacen);
        antes.registrar(Comprobacion.caido(servicio, t0, "connection refused"));
        antes.programarMantenimiento(new VentanaDeMantenimiento(
                t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(3)), "parche"));

        // Despues: un registro nuevo (como tras redesplegar) recarga lo guardado.
        RegistroDeDisponibilidad despues = new RegistroDeDisponibilidad(almacen);
        List<Interrupcion> recargadas = despues.interrupcionesEn(t0, t0.plus(Duration.ofHours(1))).stream()
                .filter(i -> i.servicio().equals(servicio))
                .toList();
        assertAll(
                () -> assertEquals(1, recargadas.size()),
                () -> assertTrue(recargadas.get(0).abierta(), "sigue abierta hasta que alguien la vea sana"),
                () -> assertEquals("connection refused", recargadas.get(0).detalle()),
                () -> assertNotNull(recargadas.get(0).id()));

        // La primera comprobacion sana la cierra, y el cierre tambien se guarda.
        despues.registrar(Comprobacion.disponible(servicio, t0.plus(Duration.ofMinutes(10))));
        RegistroDeDisponibilidad tercero = new RegistroDeDisponibilidad(almacen);
        InformeDeDisponibilidad informe = tercero.informe(
                List.of(servicio), t0, t0.plus(Duration.ofHours(4)), 99.95);

        assertAll(
                () -> assertEquals(Duration.ofMinutes(10),
                        informe.servicios().get(0).indisponible()),
                () -> assertFalse(informe.servicios().get(0).interrupciones().get(0).abierta()),
                () -> assertTrue(almacen.ventanas().stream().anyMatch(v -> "parche".equals(v.motivo())),
                        "la ventana tambien sobrevive"));
    }

    @Autowired
    private ConfiguracionDeDisponibilidad disponibilidad;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertAll(
                () -> assertNotNull(contexto),
                () -> assertNotNull(contexto.getBean(LatenciaController.class)),
                () -> assertNotNull(contexto.getBean(MonitorDeDisponibilidad.class))
        );
    }

    @Test
    @DisplayName("cada servicio vigilado apunta a su propio puerto")
    void puertosDeLosServiciosVigilados() {
        assertAll(
                () -> assertEquals("http://localhost:8081/actuator/health", disponibilidad.servicios().get("comentarios")),
                () -> assertEquals("http://localhost:8082/actuator/health", disponibilidad.servicios().get("correo")),
                () -> assertEquals("http://localhost:8083/actuator/health", disponibilidad.servicios().get("torneos")),
                () -> assertEquals("http://localhost:8084/actuator/health", disponibilidad.servicios().get("salas-partidas")),
                () -> assertEquals("http://localhost:8085/actuator/health", disponibilidad.servicios().get("notificaciones")),
                () -> assertEquals("http://localhost:8086/actuator/health", disponibilidad.servicios().get("moderacion-sanciones")),
                () -> assertEquals("http://localhost:8088/actuator/health", disponibilidad.servicios().get("admin-parametros"))
        );
    }
}
