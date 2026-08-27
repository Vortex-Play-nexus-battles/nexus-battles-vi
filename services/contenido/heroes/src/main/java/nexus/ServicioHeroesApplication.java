package nexus;

import nexus.dominio.Catalogo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ServicioHeroesApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicioHeroesApplication.class, args);
	}

	/**
	 * Catalogo en memoria con los ocho prototipos iniciales. Cuando la plataforma
	 * entregue MongoDB, este bean se sustituye por el repositorio documental sin
	 * tocar el dominio ni el controlador.
	 */
	@Bean
	public Catalogo catalogo() {
		return Catalogo.conPrototiposIniciales();
	}
}
