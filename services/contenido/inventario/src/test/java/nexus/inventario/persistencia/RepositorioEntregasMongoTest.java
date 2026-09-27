package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import nexus.inventario.dominio.ClaveDeEntregaOcupadaException;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.EstadoEntrega;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** B4 — la coleccion {@code entregas}: traduccion de errores y forma de las escrituras. */
class RepositorioEntregasMongoTest {

    private static final Instant CREADA = Instant.parse("2026-09-25T15:00:00Z");

    private MongoOperations mongo;
    private RepositorioEntregasMongo repositorio;

    @BeforeEach
    void preparar() {
        mongo = mock(MongoOperations.class);
        repositorio = new RepositorioEntregasMongo(mongo);
    }

    private static Entrega entrega() {
        return Entrega.pendiente("entrega-1", "clave-1", "huella", "uid-1", OrigenDeEntrega.COFRE, "cofre-7",
                List.of(new LineaDeEntrega("peto", 1)),
                List.of(ElementoInventario.entregado("e-1", "peto", TipoElementoInventario.ARMADURA, "Peto",
                        ParteArmadura.PECHO, OrigenDeEntrega.COFRE, "cofre-7")),
                "ms-finanzas", CREADA);
    }

    @Test
    @DisplayName("el documento conserva todo lo que hace falta para retomar la entrega")
    void idaYVuelta() {
        Entrega original = entrega();

        assertEquals(original, EntregaDocumento.de(original).aDominio());
    }

    @Test
    @DisplayName("una clave ya registrada (indice unico) es ClaveDeEntregaOcupada, no un fallo")
    void claveDuplicada() {
        when(mongo.insert(any(EntregaDocumento.class))).thenThrow(new DuplicateKeyException("clave"));

        assertThrows(ClaveDeEntregaOcupadaException.class, () -> repositorio.registrar(entrega()));
    }

    @Test
    @DisplayName("una base caida se traduce a FalloPersistenciaInventario (503) en las tres operaciones")
    void baseCaida() {
        DataAccessResourceFailureException caida = new DataAccessResourceFailureException("Mongo no disponible");
        when(mongo.insert(any(EntregaDocumento.class))).thenThrow(caida);
        when(mongo.findOne(any(Query.class), eq(EntregaDocumento.class))).thenThrow(caida);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(EntregaDocumento.class))).thenThrow(caida);

        assertThrows(FalloPersistenciaInventarioException.class, () -> repositorio.registrar(entrega()));
        assertThrows(FalloPersistenciaInventarioException.class, () -> repositorio.buscarPorClave("clave-1"));
        assertThrows(FalloPersistenciaInventarioException.class,
                () -> repositorio.completar("entrega-1", CREADA));
    }

    @Test
    @DisplayName("buscar por clave devuelve el dominio, o nada")
    void buscarPorClave() {
        when(mongo.findOne(any(Query.class), eq(EntregaDocumento.class)))
                .thenReturn(EntregaDocumento.de(entrega()))
                .thenReturn(null);

        assertEquals("entrega-1", repositorio.buscarPorClave("clave-1").orElseThrow().id());
        assertTrue(repositorio.buscarPorClave("otra").isEmpty());
    }

    @Test
    @DisplayName("completar solo escribe sobre una entrega PENDIENTE: repetirlo no cambia su momento")
    void completarSoloSiPendiente() {
        repositorio.completar("entrega-1", CREADA);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(consulta.capture(), cambio.capture(), eq(EntregaDocumento.class));
        Document filtro = consulta.getValue().getQueryObject();
        assertEquals("entrega-1", filtro.get("_id"));
        assertEquals(EstadoEntrega.PENDIENTE, filtro.get("estado"));
        Document fijar = cambio.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals(EstadoEntrega.COMPLETADA, fijar.get("estado"));
        assertEquals(CREADA, fijar.get("entregadaEn"));
    }

    @Test
    @DisplayName("un documento sin listas (escrito a mano o antiguo) se lee con listas vacias")
    void documentoSinListas() {
        EntregaDocumento sinListas = new EntregaDocumento("entrega-2", "clave-2", "huella", "uid-1",
                OrigenDeEntrega.MISION, "mision-1", null, null, EstadoEntrega.COMPLETADA, "misiones", CREADA, CREADA);

        Entrega leida = sinListas.aDominio();

        assertEquals(List.of(), leida.productos());
        assertEquals(List.of(), leida.elementos());
    }
}
