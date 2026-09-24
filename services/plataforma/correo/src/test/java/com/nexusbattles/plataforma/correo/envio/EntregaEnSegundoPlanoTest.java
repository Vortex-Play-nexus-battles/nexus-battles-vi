package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * La entrega fuera del hilo de la peticion.
 *
 * <p>El defecto que evita: con la entrega dentro de la peticion, un proveedor
 * real tarda uno a tres segundos y el cliente de ms-identidad corta a los dos.
 * Daba por fallido un correo que si habia salido y su reintento mandaba el
 * mismo mensaje otra vez -- cuota doble y el jugador con dos copias del mismo
 * codigo.
 */
class EntregaEnSegundoPlanoTest {

    private static final Map<String, Object> SIN_VARIABLES = Map.of();

    /** Guarda las tareas en vez de ejecutarlas: deja ver que no se hizo en linea. */
    private static class EjecutorQueAnota implements Executor {
        private final List<Runnable> pendientes = new CopyOnWriteArrayList<>();

        @Override
        public void execute(Runnable tarea) {
            pendientes.add(tarea);
        }

        void correrTodo() {
            pendientes.forEach(Runnable::run);
            pendientes.clear();
        }
    }

    @Test
    void laPeticionNoEsperaAlServidorDeCorreo() {
        EnviadorCorreoService enviador = mock(EnviadorCorreoService.class);
        EjecutorQueAnota ejecutor = new EjecutorQueAnota();

        new EntregaEnSegundoPlano(ejecutor, enviador)
                .entregar("ana@nexus.test", "Asunto", "email/bienvenida", SIN_VARIABLES);

        // Nada ha pasado todavia: la peticion ya puede responder 202.
        verify(enviador, org.mockito.Mockito.never())
                .enviar(anyString(), anyString(), anyString(), any());

        ejecutor.correrTodo();

        verify(enviador).enviar("ana@nexus.test", "Asunto", "email/bienvenida", SIN_VARIABLES);
    }

    /**
     * Un fallo del proveedor no puede escapar del hilo de fondo: alli no hay
     * nadie escuchando, y una excepcion perdida es justo lo que hace que un
     * correo no llegue sin que nadie se entere.
     */
    @Test
    void unFalloDelProveedorNoSeEscapaDelHiloDeFondo() {
        EnviadorCorreoService enviador = mock(EnviadorCorreoService.class);
        doThrow(new IllegalStateException("el proveedor dijo que no"))
                .when(enviador)
                .enviar(anyString(), anyString(), anyString(), any());
        EjecutorQueAnota ejecutor = new EjecutorQueAnota();

        new EntregaEnSegundoPlano(ejecutor, enviador)
                .entregar("ana@nexus.test", "Asunto", "email/bienvenida", SIN_VARIABLES);

        assertThatNoException(ejecutor);
    }

    private static void assertThatNoException(EjecutorQueAnota ejecutor) {
        org.assertj.core.api.Assertions.assertThatCode(ejecutor::correrTodo).doesNotThrowAnyException();
    }

    @Test
    void conElEjecutorDirectoLaEntregaEsInmediata() {
        // Es el modo que usan las pruebas de integracion: mismo hilo, sin
        // esperas, para poder mirar la bandeja justo despues.
        EnviadorCorreoService enviador = mock(EnviadorCorreoService.class);

        new EntregaEnSegundoPlano((Runnable r) -> r.run(), enviador)
                .entregar("ana@nexus.test", "Asunto", "email/bienvenida", SIN_VARIABLES);

        verify(enviador).enviar("ana@nexus.test", "Asunto", "email/bienvenida", SIN_VARIABLES);
    }

    @Test
    void laListaDeTareasNoCreceSiTodoSeEjecuta() {
        EnviadorCorreoService enviador = mock(EnviadorCorreoService.class);
        EjecutorQueAnota ejecutor = new EjecutorQueAnota();
        EntregaEnSegundoPlano entrega = new EntregaEnSegundoPlano(ejecutor, enviador);

        entrega.entregar("a@nexus.test", "A", "email/x", SIN_VARIABLES);
        entrega.entregar("b@nexus.test", "B", "email/x", SIN_VARIABLES);
        ejecutor.correrTodo();

        assertThat(ejecutor.pendientes).isEmpty();
        verify(enviador, org.mockito.Mockito.times(2))
                .enviar(anyString(), anyString(), anyString(), any());
    }
}