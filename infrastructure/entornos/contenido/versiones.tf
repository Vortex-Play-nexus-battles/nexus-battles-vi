# Gobierno del estado del host de contenido — R9.1
#
# ## Que estaba mal
#
# `main.tf` declaraba un bloque `terraform {}` con los proveedores SIN version
# fijada y SIN backend. Consecuencias, todas reales:
#
#   1. El estado vivia en el disco de quien aplico (`terraform.tfstate` local).
#      No esta en el repositorio —bien— pero tampoco en ningun sitio
#      compartido: si esa persona pierde el portatil o se desvincula del
#      equipo, el host de contenido queda sin forma gobernable de cambiarse.
#      Es exactamente el riesgo #5 del acta ("un integrante se ausenta"),
#      aplicado a infraestructura en vez de a codigo.
#   2. Sin bloqueo: dos personas aplicando a la vez corrompen el estado.
#   3. Sin versionado: un `destroy` por error no tiene de donde recuperarse.
#   4. Proveedores sin fijar: `terraform init` hoy y dentro de un mes pueden
#      traer versiones distintas del proveedor de AWS sobre la MISMA
#      infraestructura.
#
# Plataforma no tiene ninguno de los cuatro problemas (ver
# ../plataforma/versiones.tf). Esto lo alinea.
#
# ## Por que esto SOLO no basta, y cual es la compuerta
#
# Mover el backend a S3 deja el estado remoto VACIO. Con el estado vacio,
# `tofu plan` no dice "sin cambios": dice que hay que CREAR la instancia, la
# IP, el grupo de seguridad y la llave. Aplicar eso levantaria un tercer EC2
# —prohibido por politica y por coste— mientras el actual sigue corriendo.
#
# Por eso el orden es: backend primero, IMPORTAR despues (ver
# `importaciones.tf`), y solo cuando `tofu plan` diga
#
#     No changes. Your infrastructure matches the configuration.
#
# se habilita `apply` para esta carpeta. Hasta entonces el workflow lo bloquea
# explicitamente; no depende de que nadie se acuerde.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Mismo bucket que plataforma, clave distinta: un estado por entorno, nunca
  # compartido. Bucket y region NO se escriben aqui —los pasa el workflow con
  # -backend-config desde TFSTATE_BUCKET y AWS_REGION— para que este archivo
  # no dependa del numero de cuenta.
  backend "s3" {
    key          = "entornos/contenido-dev.tfstate"
    use_lockfile = true
  }
}
