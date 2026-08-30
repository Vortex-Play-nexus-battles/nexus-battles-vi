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
	 * Catalogo en memoria (perfil por defecto): sirve la demo sin infraestructura.
	 */
	@Bean
	@org.springframework.context.annotation.Profile("!mongo")
	public nexus.dominio.CatalogoDeHeroes catalogoEnMemoria() {
		return Catalogo.conPrototiposIniciales();
	}

	/**
	 * Catalogo en MongoDB (perfil "mongo"): la persistencia no relacional que
	 * exige la seccion 8. Se activa con SPRING_PROFILES_ACTIVE=mongo y la
	 * cadena de conexion SPRING_DATA_MONGODB_URI cuando plataforma aprovisione.
	 */
	@Bean
	@org.springframework.context.annotation.Profile("mongo")
	public nexus.dominio.CatalogoDeHeroes catalogoEnMongo(
			org.springframework.data.mongodb.core.MongoTemplate mongo) {
		return new nexus.persistencia.CatalogoEnMongo(mongo);
	}
}
