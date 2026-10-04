package nexus.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nexus.api.SolicitudBanner;
import nexus.dominio.Banner;
import nexus.dominio.BannerNoEncontradoException;
import nexus.persistencia.BannerRepository;
import org.springframework.stereotype.Service;

@Service
public class GestionarBannersServicio {

        private final BannerRepository repositorio;

        public GestionarBannersServicio(BannerRepository repositorio) {
                this.repositorio = repositorio;
        }

        public Banner crear(SolicitudBanner solicitud) {
                Instant ahora = Instant.now();
                Banner banner = new Banner(
                        UUID.randomUUID().toString(),
                        solicitud.contenido().strip(),
                        solicitud.publicarDesde(),
                        solicitud.vigenteHasta(),
                        false,
                        ahora,
                        ahora);
                return repositorio.save(banner);
        }

        public Banner editar(String id, SolicitudBanner solicitud) {
                Banner actual = buscar(id);
                Banner editado = new Banner(
                        actual.id(),
                        solicitud.contenido().strip(),
                        solicitud.publicarDesde(),
                        solicitud.vigenteHasta(),
                        actual.retirado(),
                        actual.creadoEn(),
                        Instant.now());
                return repositorio.save(editado);
        }

        public void retirar(String id) {
                Banner actual = buscar(id);
                if (!actual.retirado()) {
                        repositorio.save(new Banner(
                                actual.id(),
                                actual.contenido(),
                                actual.publicarDesde(),
                                actual.vigenteHasta(),
                                true,
                                actual.creadoEn(),
                                Instant.now()));
                }
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

        private Banner buscar(String id) {
                return repositorio.findById(id)
                        .orElseThrow(() -> new BannerNoEncontradoException(id));
        }
}
