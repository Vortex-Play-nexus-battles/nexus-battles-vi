variable "region" {
  description = "Region de AWS. La misma de infrastructure/entornos/main.tf y contenido/ para no repartir recursos por consolas distintas."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = <<-EOT
    Tipo de instancia del host de plataforma. Solo se admiten los tipos que el
    Free Plan marca como elegibles para cuentas creadas desde el 2025-07-15
    (docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html):
    t3.micro, t3.small, t4g.micro, t4g.small, c7i-flex.large, m7i-flex.large.
    Cualquier otro tipo lo rechaza el propio plan al lanzar.
    Precio on-demand oficial en us-east-1 (Linux, 2026-09-15):
      t3.small  2 GiB  x86   USD 0,0208/h  ->  15,0 USD/mes 24x7,  6,2 con apagado nocturno
      t4g.small 2 GiB  arm64 USD 0,0168/h  ->  12,1 USD/mes 24x7,  5,0 con apagado nocturno
      c7i-flex.large 4 GiB   USD 0,0848/h  ->  61,1 USD/mes 24x7, 25,4 con apagado nocturno
      m7i-flex.large 8 GiB   USD 0,0958/h  ->  69,0 USD/mes 24x7, 28,7 con apagado nocturno
    t3.small por defecto: x86 como las imagenes que ya publica cd.yml (sin
    buildx multi-arquitectura) y 2 GiB, que con los limites de memoria de
    docker-compose.deploy.yml alcanzan para el perfil de la demo del Sprint 2.
  EOT
  type        = string
  default     = "t3.small"

  validation {
    condition     = contains(["t3.micro", "t3.small", "t4g.micro", "t4g.small", "c7i-flex.large", "m7i-flex.large"], var.instance_type)
    error_message = "Solo tipos elegibles del Free Plan: t3.micro, t3.small, t4g.micro, t4g.small, c7i-flex.large, m7i-flex.large."
  }
}

variable "tamano_disco_gb" {
  description = "Disco raiz gp3 en GB. USD 0,08/GB-mes oficial: 20 GB = 1,60 USD/mes, y se cobra aunque la instancia este apagada."
  type        = number
  default     = 20
}

variable "cidr_ssh" {
  description = "Origenes admitidos en el puerto 22. cd.yml despliega por scp+ssh desde runners de GitHub sin IP fija, por eso 0.0.0.0/0 con autenticacion SOLO por llave (la AMI de Ubuntu no acepta contrasena). Vacio = puerto cerrado (cuando el despliegue pase a SSM)."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "cidr_servicios" {
  description = "Origenes admitidos en los puertos de los servicios de plataforma (8081-8088, docker-compose.yml y puerto_de() en cd.yml)."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "correo_alertas" {
  description = "Correo que recibe las alertas de presupuesto y los recordatorios de salida. Debe ser un buzon del equipo, no el del root. Llega desde la variable AWS_CORREO_ALERTAS del repositorio (TF_VAR_correo_alertas en infra-dev.yml)."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.correo_alertas))
    error_message = "correo_alertas debe ser una direccion de correo valida."
  }
}

variable "credito_total_usd" {
  description = "Credito del Free Plan sobre el que se calculan los umbrales 10/25/50/75/90 %. USD 100 al registro; subir a 200 solo cuando los USD 100 de actividades esten acreditados en Billing > Credits."
  type        = number
  default     = 100
}

variable "tope_mensual_usd" {
  description = "Tope de gasto bruto mensual (antes de credito). Con un t3.small, su IP y su disco 24x7 el consumo real es ~20 USD/mes; el tope avisa antes de que un recurso olvidado se coma el credito."
  type        = number
  default     = 30
}

variable "recordatorios" {
  description = "Recordatorios de salida (fecha UTC -> mensaje). El plan free cierra la cuenta el 2027-03-15; el proyecto se entrega el 2026-11-06."
  type        = map(string)
  default = {
    "2026-10-30T12:00:00" = "Nexus Battles VI: quedan 7 dias para el cierre del proyecto (6/nov). Respalda datos de dev y programa el DESTROY."
    "2027-01-14T12:00:00" = "Nexus Battles VI: quedan 60 dias para que el Free Plan cierre la cuenta (15/mar/2027). Ejecuta el plan de salida: respaldo, DESTROY y decision sobre la cuenta."
    "2027-02-12T12:00:00" = "Nexus Battles VI: quedan 30 dias para el cierre automatico de la cuenta AWS (15/mar/2027). Verifica que no quede ningun recurso."
  }
}
