package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.repository.BrechaConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrechaConocimientoServiceTest {

    @Mock
    private BrechaConocimientoRepository brechaConocimientoRepository;

    private BrechaConocimientoService brechaConocimientoService;

    @BeforeEach
    void configurar() {
        brechaConocimientoService = new BrechaConocimientoService(brechaConocimientoRepository);
    }

    @Test
    void registrarNoUtil_preguntaNueva_creaLaBrechaConElContadorCorrectoEnUno() {
        when(brechaConocimientoRepository.findByTextoNormalizado("como subo de nivel")).thenReturn(Optional.empty());

        brechaConocimientoService.registrarNoUtil("¿Cómo subo de nivel?");

        BrechaConocimiento guardada = capturarBrechaGuardada();
        assertEquals("como subo de nivel", guardada.getTextoNormalizado());
        assertEquals("¿Cómo subo de nivel?", guardada.getEjemploPregunta());
        assertEquals(1, guardada.getContadorNoUtil());
        assertEquals(0, guardada.getContadorEscalamiento());
    }

    @Test
    void registrarEscalamiento_brechaExistente_incrementaSuContadorSinCrearOtra() {
        BrechaConocimiento existente = new BrechaConocimiento("como subo de nivel", "¿Cómo subo de nivel?");
        existente.registrarEscalamiento();
        when(brechaConocimientoRepository.findByTextoNormalizado("como subo de nivel")).thenReturn(Optional.of(existente));

        brechaConocimientoService.registrarEscalamiento("como subo de nivel");

        BrechaConocimiento guardada = capturarBrechaGuardada();
        assertSame(existente, guardada);
        assertEquals(2, guardada.getContadorEscalamiento());
        assertEquals(0, guardada.getContadorNoUtil());
    }

    // El corazon de la "agrupacion": variantes que solo difieren en tildes,
    // mayusculas y signos buscan la MISMA brecha.
    @Test
    void variantesDeLaMismaPregunta_seAgrupanEnElMismoTextoNormalizado() {
        when(brechaConocimientoRepository.findByTextoNormalizado(anyString())).thenReturn(Optional.empty());

        brechaConocimientoService.registrarEscalamiento("¿¿CÓMO subo de NIVEL??");

        verify(brechaConocimientoRepository).findByTextoNormalizado("como subo de nivel");
    }

    // Una pregunta que queda vacia al normalizar ("???", solo emojis) no es
    // una brecha: no se consulta ni se guarda nada.
    @Test
    void preguntaSinTextoUtil_noRegistraNada() {
        brechaConocimientoService.registrarNoUtil("???");

        verifyNoInteractions(brechaConocimientoRepository);
    }

    // texto_normalizado es VARCHAR(500) en V3: una pregunta mas larga se
    // recorta en vez de hacer fallar el INSERT.
    @Test
    void preguntaMuyLarga_seRecortaAlLimiteDeLaColumna() {
        String preguntaLarga = "palabra ".repeat(100);
        when(brechaConocimientoRepository.findByTextoNormalizado(anyString())).thenReturn(Optional.empty());

        brechaConocimientoService.registrarEscalamiento(preguntaLarga);

        BrechaConocimiento guardada = capturarBrechaGuardada();
        assertTrue(guardada.getTextoNormalizado().length() <= BrechaConocimientoService.LONGITUD_MAXIMA_TEXTO_NORMALIZADO);
    }

    private BrechaConocimiento capturarBrechaGuardada() {
        ArgumentCaptor<BrechaConocimiento> captor = ArgumentCaptor.forClass(BrechaConocimiento.class);
        verify(brechaConocimientoRepository).save(captor.capture());
        return captor.getValue();
    }
}
