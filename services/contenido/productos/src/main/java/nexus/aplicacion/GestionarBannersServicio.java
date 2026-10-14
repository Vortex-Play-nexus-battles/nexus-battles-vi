package nexus.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nexus.api.SolicitudBanner;
import nexus.auditoria.ObjetoAuditado;
import nexus.auditoria.OperacionAdministrativa;
import nexus.auditoria.RegistroDeOperaciones;
import nexus.auditoria.TipoOperacion;
import nexus.dominio.Banner;
import nexus.dominio.BannerNoEncontradoException;
import nexus.persistencia.BannerRepository;
import nexus.recursos.MotivoInvalidoException;
import org.springframework.stereotype.Service;

/**
 * Los banners de la página de inicio — HU-PRD-013, §7.2.4.
 *
 * <p>SA-18 (§7.2.5, §7.3.5): cada publicación, edición y retiro queda con
 * quién lo hizo ({@code creadoPor}, {@code modificadoPor}) y en la bitácora de
 * operaciones del catálogo, con sus valores antes y después; el retiro admite
 * un motivo. Retirar es lógico: el banner deja de mostrarse y su registro se
 * conserva.
 */
@Service
public class GestionarBannersServicio {

        /** Lo que cabe en el motivo de un retiro, como el de la bitácora central. */
        public static final int MOTIVO_MAXIMO = 500;

        private final BannerRepository repositorio;
        private final RegistroDeOperaciones operaciones;

        public GestionarBannersServicio(BannerRepository repositorio, RegistroDeOperaciones operaciones) {
                this.repositorio = repositorio;
                this.operaciones = operaciones;
        }

        /** @param autor el {@code uid} de quien lo publica */
        public Banner crear(SolicitudBanner solicitud, String autor) {
                Instant ahora = Instant.now();
                Banner banner = new Banner(
                        UUID.randomUUID().toString(),
                        solicitud.contenido().strip(),
                        solicitud.publicarDesde(),
                        solicitud.vigenteHasta(),
                        false,
                        ahora,
                        ahora,
                        autor,
                        autor);
                Banner guardado = repositorio.save(banner);
                operaciones.deBanner(TipoOperacion.ALTA_BANNER, null, guardado, autor, null);
                return guardado;
        }

        /** @param autor el {@code uid} de quien lo edita */
        public Banner editar(String id, SolicitudBanner solicitud, String autor) {
                Banner actual = buscar(id);
                Banner editado = new Banner(
                        actual.id(),
                        solicitud.contenido().strip(),
                        solicitud.publicarDesde(),
                        solicitud.vigenteHasta(),
                        actual.retirado(),
                        actual.creadoEn(),
                        Instant.now(),
                        actual.creadoPor(),
                        autor);
                Banner guardado = repositorio.save(editado);
                operaciones.deBanner(TipoOperacion.MODIFICACION_BANNER, actual, guardado, autor, null);
                return guardado;
        }

        /**
         * Retira el banner. Retirar uno ya retirado no cambia nada (ni se
         * registra otra vez).
         *
         * @param motivo por qué se retira; opcional, hasta {@value #MOTIVO_MAXIMO} caracteres
         * @throws MotivoInvalidoException si el motivo es más largo
         */
        public void retirar(String id, String autor, String motivo) {
                String limpio = motivo == null || motivo.isBlank() ? null : motivo.strip();
                if (limpio != null && limpio.length() > MOTIVO_MAXIMO) {
                        throw new MotivoInvalidoException(
                                        "El motivo admite como máximo " + MOTIVO_MAXIMO + " caracteres.");
                }
                Banner actual = buscar(id);
                if (actual.retirado()) {
                        return;
                }
                Banner retirado = repositorio.save(new Banner(
                        actual.id(),
                        actual.contenido(),
                        actual.publicarDesde(),
                        actual.vigenteHasta(),
                        true,
                        actual.creadoEn(),
                        Instant.now(),
                        actual.creadoPor(),
                        autor));
                operaciones.deBanner(TipoOperacion.RETIRO_BANNER, actual, retirado, autor, limpio);
        }

        public List<Banner> consultarVigentes() {
                Instant ahora = Instant.now();
                return repositorio
                        .findByRetiradoFalseAndPublicarDesdeLessThanEqualAndVigenteHastaGreaterThanOrderByPublicarDesdeDesc(
                                ahora,
                                ahora);
        }

        public List<Banner> consultarTodos() {
                return repositorio.findAllByOrderByPublicarDesdeDesc();
        }

        /** SA-18: lo que se hizo con un banner, de lo más reciente a lo más antiguo. */
        public List<OperacionAdministrativa> historial(String id) {
                buscar(id);
                return operaciones.historialDe(ObjetoAuditado.BANNER, id);
        }

        private Banner buscar(String id) {
                return repositorio.findById(id)
                        .orElseThrow(() -> new BannerNoEncontradoException(id));
        }
}
