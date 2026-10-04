package nexus.misiones.catalogo;

import static nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemillaTest.MASTER_POR_DIFICULTAD;
import static nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemillaTest.documento;
import static nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemillaTest.documentoConTabla20;
import static nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemillaTest.sinTabla;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.Origen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio 3 de HU-MIS-012 («la probabilidad de aparicion del Master
 * corresponde a la dificultad declarada») y las exigencias de RG-108 a las
 * misiones que disena el equipo: la semilla se revisa al cargar, no en el
 * momento de jugar.
 */
class MasterPorDificultadTest {

    private static final List<EpicaDeTabla20> TABLA20 = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20();
    private static final EpicaDeTabla20 MAGO_HIELO = TABLA20.stream()
            .filter(f -> f.prototipo().equals("Mago Hielo")).findFirst().orElseThrow();
    private static final EpicaDeTabla20 CHAMAN = TABLA20.stream()
            .filter(f -> f.prototipo().equals("Chamán")).findFirst().orElseThrow();

    // ------------------------------------------------------ la tabla de la semilla

    @Test
    @DisplayName("la semilla del documento declara la probabilidad de Master de las cuatro dificultades (decision del PO)")
    void laSemillaDeclaraLaTabla() {
        assertThat(CatalogoDeMisionesDesdeSemilla.leer(CatalogoDeMisionesDesdeSemilla.DEL_DOCUMENTO)
                .probabilidadDeMasterPorDificultad())
                .containsOnlyKeys(Dificultad.values())
                .containsEntry(Dificultad.FACIL, 0.10)
                .containsEntry(Dificultad.NORMAL, 0.15)
                .containsEntry(Dificultad.DIFICIL, 0.20)
                .containsEntry(Dificultad.EXTREMO, 0.25);
    }

    @Test
    @DisplayName("todas las misiones publicadas traen un Master cuya probabilidad es la de su dificultad")
    void lasPublicadasCoinciden() {
        Map<Dificultad, Double> tabla = CatalogoDeMisionesDesdeSemilla.leer(CatalogoDeMisionesDesdeSemilla.DEL_DOCUMENTO)
                .probabilidadDeMasterPorDificultad();
        List<Mision> todas = CatalogoDeMisionesDesdeSemilla.cargar(true).todas();

        assertThat(todas).isNotEmpty();
        for (Mision mision : todas) {
            for (MasterDeMision master : mision.masters()) {
                assertThat(master.probabilidad())
                        .as("Master «%s» de «%s» (%s)", master.nombre(), mision.id(), mision.dificultad())
                        .isEqualTo(tabla.get(mision.dificultad()));
            }
        }
    }

    // ------------------------------------------------ la validacion de carga (criterio 3)

    @Test
    @DisplayName("una mision cuyo Master no tiene la probabilidad de su dificultad no se publica")
    void rechazaLaQueNoCoincide() {
        Mision templo = Misiones.templo();
        assertThat(templo.dificultad()).isEqualTo(Dificultad.NORMAL);
        assertThat(templo.masters().get(0).probabilidad()).isEqualTo(0.15);

        // Normal con 15 %: la del ejemplo del documento, pasa.
        assertThatCode(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(templo), null)).doesNotThrowAnyException();

        // La misma mision declarada Dificil (20 %) con el Master al 15 %: no coincide.
        Mision dificilAl15 = copia(templo, templo.id(), Origen.DOCUMENTO, Dificultad.DIFICIL, templo.masters());
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(dificilAl15), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("templo-olvidado")
                .hasMessageContaining("Sombra del Olvido")
                .hasMessageContaining("15 %")
                .hasMessageContaining("20 %")
                .hasMessageContaining("DIFICIL");
    }

    @Test
    @DisplayName("cada dificultad admite solo su cifra: 10, 15, 20 y 25 %")
    void cadaDificultadSuCifra() {
        Mision templo = Misiones.templo();
        for (Dificultad dificultad : Dificultad.values()) {
            for (Map.Entry<Dificultad, Double> cifra : MASTER_POR_DIFICULTAD.entrySet()) {
                Mision mision = copia(templo, templo.id(), Origen.DOCUMENTO, dificultad,
                        List.of(new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", cifra.getValue(),
                                Misiones.VELO_DE_SOMBRAS)));
                if (cifra.getKey() == dificultad) {
                    assertThatCode(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(mision), null))
                            .as("%s con %s", dificultad, cifra.getValue()).doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(mision), null))
                            .as("%s con %s", dificultad, cifra.getValue())
                            .isInstanceOf(IllegalStateException.class);
                }
            }
        }
    }

    @Test
    @DisplayName("la semilla del documento sin la tabla completa, o con una cifra fuera de rango, no arranca")
    void tablaIncompleta() {
        Mision templo = Misiones.templo();
        SemillaDeMisiones sinTabla = new SemillaDeMisiones("1", List.of(), List.of(), Map.of(), List.of(templo));
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(sinTabla, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("probabilidad de Máster")
                .hasMessageContaining("dificultad");

        Map<Dificultad, Double> sinExtremo = new EnumMap<>(MASTER_POR_DIFICULTAD);
        sinExtremo.remove(Dificultad.EXTREMO);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                new SemillaDeMisiones("1", List.of(), List.of(), sinExtremo, List.of(templo)), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXTREMO");

        Map<Dificultad, Double> fueraDeRango = new EnumMap<>(MASTER_POR_DIFICULTAD);
        fueraDeRango.put(Dificultad.FACIL, 1.5);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                new SemillaDeMisiones("1", List.of(), List.of(), fueraDeRango, List.of(templo)), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACIL");
    }

    @Test
    @DisplayName("la tabla es del documento: la semilla de progresion, la del equipo o la provisional no traen la suya")
    void soloUnaTabla() {
        Mision templo = Misiones.templo();
        Mision delEquipo = delEquipo("la-del-equipo", Dificultad.NORMAL, 0.15);
        SemillaDeMisiones conTabla = new SemillaDeMisiones("1", List.of(), List.of(), MASTER_POR_DIFICULTAD,
                List.of(delEquipo));
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20, templo), null, conTabla, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Máster por dificultad");

        SemillaDeMisiones progresionConTabla = new SemillaDeMisiones("1", List.of(), List.of(),
                MASTER_POR_DIFICULTAD, List.of());
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(templo), progresionConTabla, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Máster por dificultad");

        SemillaDeMisiones provisionalConTabla = new SemillaDeMisiones("1", List.of(), List.of(),
                MASTER_POR_DIFICULTAD, List.of());
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento(templo), provisionalConTabla))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Máster por dificultad");
    }

    // ---------------------------------------------- RG-108: las misiones del equipo

    @Test
    @DisplayName("la semilla del equipo publica solo misiones del equipo, y la del documento solo las del documento")
    void cadaSemillaSuOrigen() {
        Mision templo = Misiones.templo();
        Mision delEquipo = delEquipo("la-del-equipo", Dificultad.NORMAL, 0.15);
        SemillaDeMisiones documento = documentoConTabla20(TABLA20, templo);

        assertThatCode(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null, sinTabla(delEquipo), null))
                .doesNotThrowAnyException();
        assertThat(CatalogoDeMisionesDesdeSemilla.desde(documento, null, sinTabla(delEquipo), null).todas())
                .extracting(Mision::id).containsExactly("templo-olvidado", "la-del-equipo");

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null, sinTabla(templo), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("templo-olvidado")
                .hasMessageContaining("equipo");
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20, delEquipo), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("la-del-equipo");
    }

    @Test
    @DisplayName("RF-MIS-58: una mision del equipo sin Master no se publica")
    void sinMasterNo() {
        Mision sinMaster = copia(delEquipo("sin-master", Dificultad.NORMAL, 0.15), "sin-master", Origen.EQUIPO,
                Dificultad.NORMAL, List.of());

        assertThatThrownBy(() -> publicar(sinMaster))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin-master")
                .hasMessageContaining("Máster");
    }

    @Test
    @DisplayName("RF-MIS-57: una mision del equipo necesita enemigos regulares y jefe final")
    void sinJefeOSinRegularesNo() {
        Mision modelo = delEquipo("a-medias", Dificultad.NORMAL, 0.15);
        Mision sinJefe = new Mision(modelo.id(), modelo.origen(), modelo.nombre(), modelo.categoria(),
                modelo.descripcionBreve(), modelo.imagen(), modelo.dificultad(), modelo.duracionHoras(),
                modelo.nivelRecomendado(), modelo.requisitosPrevios(), modelo.narrativa(), modelo.escenario(),
                modelo.objetivos(), modelo.enemigos(), null, modelo.masters(), modelo.recompensas(),
                modelo.destacada(), modelo.disponibleHasta(), modelo.intentos());
        Mision sinRegulares = new Mision(modelo.id(), modelo.origen(), modelo.nombre(), modelo.categoria(),
                modelo.descripcionBreve(), modelo.imagen(), modelo.dificultad(), modelo.duracionHoras(),
                modelo.nivelRecomendado(), modelo.requisitosPrevios(), modelo.narrativa(), modelo.escenario(),
                modelo.objetivos(), List.of(), modelo.jefe(), modelo.masters(), modelo.recompensas(),
                modelo.destacada(), modelo.disponibleHasta(), modelo.intentos());

        assertThatThrownBy(() -> publicar(sinJefe)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jefe");
        assertThatThrownBy(() -> publicar(sinRegulares)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enemigos regulares");
    }

    @Test
    @DisplayName("la epica del Master del equipo lleva efecto general y potenciado, como las de la Tabla 20")
    void epicaConAmbosEfectos() {
        // Mago Hielo tiene efecto general en la Tabla 20: sin el, la epica esta a medias.
        Epica sinGeneral = new Epica(MAGO_HIELO.epica().nombre(), null, MAGO_HIELO.epica().efectoPotenciado(),
                MAGO_HIELO.epica().productoId());
        Epica sinPotenciado = new Epica(MAGO_HIELO.epica().nombre(), MAGO_HIELO.epica().efectoGeneral(), " ",
                MAGO_HIELO.epica().productoId());

        assertThatThrownBy(() -> publicar(conMasterDeHielo(sinGeneral)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("efecto general");
        assertThatThrownBy(() -> publicar(conMasterDeHielo(sinPotenciado)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("efecto potenciado");

        // La unica excepcion es la que la propia Tabla 20 hace: Chaman y Medico no tienen efecto general.
        Epica delChaman = CHAMAN.epica();
        Mision conChaman = copia(delEquipo("con-chaman", Dificultad.NORMAL, 0.15), "con-chaman", Origen.EQUIPO,
                Dificultad.NORMAL, List.of(new MasterDeMision("Voz del Bosque", "Chamán", 0.15, delChaman)));
        assertThatCode(() -> publicar(conChaman)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("si la epica trae productoId, es el de la fila de la Tabla 20 de su tipo")
    void productoDeLaEpica() {
        Epica conOtroProducto = new Epica(MAGO_HIELO.epica().nombre(), MAGO_HIELO.epica().efectoGeneral(),
                MAGO_HIELO.epica().efectoPotenciado(), UUID.randomUUID().toString());

        assertThatThrownBy(() -> publicar(conMasterDeHielo(conOtroProducto)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tabla 20");
        assertThatCode(() -> publicar(conMasterDeHielo(MAGO_HIELO.epica()))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una epica propia de la mision, que no es de la Tabla 20 (Velo de Sombras), se entrega con su producto")
    void epicaPropiaConSuProducto() {
        Epica velo = Misiones.VELO_DE_SOMBRAS;
        assertThat(velo.entregable()).isTrue();
        Mision conVelo = copia(delEquipo("con-velo", Dificultad.NORMAL, 0.15), "con-velo", Origen.EQUIPO,
                Dificultad.NORMAL, List.of(new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.15, velo)));

        assertThatCode(() -> CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20), null,
                sinTabla(conVelo), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el producto de otro tipo de la Tabla 20 no se entrega a un Master que no es de ese tipo, ni con otro nombre")
    void productoDeOtroTipoDeLaTabla() {
        Epica delTanque = TABLA20.stream().filter(f -> f.prototipo().equals("Guerrero Tanque")).findFirst()
                .orElseThrow().epica();
        Epica conOtroNombre = new Epica("Escarcha eterna", "x", "y", delTanque.productoId());

        assertThatThrownBy(() -> publicar(conMasterDeHielo(delTanque)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Tabla 20");
        assertThatThrownBy(() -> publicar(conMasterDeHielo(conOtroNombre)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Tabla 20");
    }

    @Test
    @DisplayName("la mision tecnica del banco E2E (PROVISIONAL_DEV) puede fijar su Master al 100 %: no es contenido del juego")
    void laMisionTecnicaQuedaFueraDeLaTabla() {
        Mision templo = Misiones.templo();
        MasterDeMision seguro = new MasterDeMision("Máster de prueba", "Guerrero Tanque", 1.0,
                TABLA20.stream().filter(f -> f.prototipo().equals("Guerrero Tanque")).findFirst().orElseThrow()
                        .epica());
        Mision tecnica = new Mision("dev-master-seguro", Origen.PROVISIONAL_DEV,
                CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL + " Master seguro", templo.categoria(), "d", null,
                Dificultad.FACIL, 1, null, List.of(), "n", null, templo.objetivos(), templo.enemigos(), templo.jefe(),
                List.of(seguro), templo.recompensas(), false, null, null);

        assertThatCode(() -> CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20, templo), null, null,
                null, sinTabla(tecnica))).doesNotThrowAnyException();
        // La misma mision, si fuera del equipo, no se publica: la regla sigue vigente para el contenido del juego.
        Mision delJuego = copia(tecnica, "del-juego", Origen.EQUIPO, Dificultad.FACIL, List.of(seguro));
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20, templo), null,
                sinTabla(delJuego), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("100 %")
                .hasMessageContaining("10 %");
    }

    // ------------------------------------------------------------------ apoyo

    private static CatalogoDeMisionesDesdeSemilla publicar(Mision delEquipo) {
        return CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(TABLA20, Misiones.templo()),
                null, sinTabla(delEquipo), null);
    }

    private static Mision conMasterDeHielo(Epica epica) {
        return copia(delEquipo("con-hielo", Dificultad.NORMAL, 0.15), "con-hielo", Origen.EQUIPO, Dificultad.NORMAL,
                List.of(new MasterDeMision("Hija de la Escarcha", "Mago Hielo", 0.15, epica)));
    }

    /** Una mision completa del equipo (la de Templo con otro id y origen), con un Master afin a Mago Hielo. */
    private static Mision delEquipo(String id, Dificultad dificultad, double probabilidadDelMaster) {
        MasterDeMision master = new MasterDeMision("Hija de la Escarcha", "Mago Hielo", probabilidadDelMaster,
                MAGO_HIELO.epica());
        return copia(Misiones.templo(), id, Origen.EQUIPO, dificultad, List.of(master));
    }

    private static Mision copia(Mision m, String id, Origen origen, Dificultad dificultad,
                                List<MasterDeMision> masters) {
        return new Mision(id, origen, m.nombre(), m.categoria(), m.descripcionBreve(), m.imagen(), dificultad,
                m.duracionHoras(), m.nivelRecomendado(), List.of(), m.narrativa(), m.escenario(), m.objetivos(),
                m.enemigos(), m.jefe(), masters, m.recompensas(), false, m.disponibleHasta(), m.intentos());
    }
}
