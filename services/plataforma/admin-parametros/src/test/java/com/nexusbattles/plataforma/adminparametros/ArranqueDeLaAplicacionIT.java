package com.nexusbattles.plataforma.adminparametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Levanta la aplicacion completa, igual que hace el contenedor en el
 * servidor.
 *
 * <p>Por que existe: el 2026-09-17 el servicio de comentarios no arranco en
 * el host de desarrollo por una dependencia de ejecucion que faltaba, con el
 * build en verde, porque ninguna de sus pruebas cargaba el contexto de
 * Spring. Este modulo estaba en la misma situacion: cero pruebas. Aunque hoy
 * solo contenga la clase de arranque, esta prueba avisa en cuanto se le
 * agregue la primera pieza y algo no case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueDeLaAplicacionIT {

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertNotNull(contexto);
    }
}
