package com.nexusbattles.plataforma.metricasplataforma;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.latencia.LatenciaController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueDeLaAplicacionIT {

    @Autowired
    private ApplicationContext contexto;

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
