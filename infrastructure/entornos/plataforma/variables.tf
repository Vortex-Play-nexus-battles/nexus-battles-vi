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
    Hasta el 28-sep: t3.small (x86 como las imagenes que publica cd.yml, sin
    buildx multi-arquitectura; 2 GiB).
    Desde la opcion E de infrastructure/despliegue/CAPACIDAD.md: c7i-flex.large.
    Medido el 28-sep, 15 min despues de un reinicio limpio: 3305 MiB de
    demanda (12 JVM + 5 Postgres) sobre 1910 MiB de RAM, swap de 2 GB lleno y
    cinco servicios sin salud en 5 s; el smoke de dev fallaba por eso. 4 GiB,
    tambien x86 (misma AMI, mismas imagenes), del Free Plan; el cambio de tipo
    es en caliente (parar, cambiar, encender) con la misma IP elastica y el
    mismo disco. Se paga con creditos del Free Plan (autorizado por el
    responsable del bloque el 29-sep, con USD 113,75 de saldo; el saldo lo
    muestra diagnostico-dev.yml).
    Volver a t3.small: revertir el PR que puso este valor. Es el mismo cambio
    en caliente al reves, y la compuerta de infra-dev.yml comprueba antes que
    el tipo se ofrece en la zona del host.
  EOT
  type        = string
  default     = "c7i-flex.large"

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
  description = <<-EOT
    Origenes admitidos en los puertos directos de los servicios de plataforma
    (8081-8089, docker-compose.yml y puerto_de() en cd.yml). B12: solo el host
    de contenido (IP elastica 34.193.90.11, cuenta del grupo 2), que es el
    unico que llama a un puerto directo: sus servicios validan tokens contra
    el JWKS de ms-identidad en :8089 (IDENTIDAD_JWKS_URL en
    docker-compose.contenido.yml). El publico entra SOLO por el borde (:80);
    las llamadas entre servicios de este host van por la red de Docker y no
    pasan por aqui. Hasta B12 era 0.0.0.0/0: cualquiera llegaba a /actuator y
    a las rutas internas de cada servicio sin pasar por el borde.
    Para depurar desde un portatil se anade la IP propia a proposito y
    temporalmente, en un PR, nunca a mano en la consola.
  EOT
  type        = list(string)
  default     = ["34.193.90.11/32"]
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
  description = <<-EOT
    Tope de gasto bruto mensual (antes de credito). Su trabajo es avisar de lo
    que NO esta previsto (una segunda instancia, un volumen huerfano) antes de
    que se coma el credito, asi que tiene que quedar por encima del gasto
    previsto y no mucho mas: si el gasto normal lo supera, sus avisos pasan a
    ser ruido y nadie los lee.
    Con t3.small, IP y disco 24x7 el gasto era ~20 USD/mes (tope 30). Con
    c7i-flex.large (opcion E, 29-sep) y el apagado programado de lunes a
    viernes (~83 h encendido por semana), ~36 USD/mes (instancia ~30,5 + IP
    3,65 + disco 1,60); desde el 10-oct se enciende tambien el fin de semana
    (~116 h por semana), ~48 USD/mes (instancia ~42,7); 24x7 serian ~67.
    Tope 50: el pronostico avisa si el host se queda encendido de noche.
  EOT
  type        = number
  default     = 50
}

variable "horario_activo" {
  description = "Apagado nocturno y encendido programados del host (horario.tf). false los desactiva sin borrarlos: por ejemplo, la semana de la demo si hace falta el entorno 24 h. Con c7i-flex.large cada noche encendida cuesta ~0,65 USD de credito."
  type        = bool
  default     = true
}

variable "horario_apagar" {
  description = "Cuando se apaga el host, en hora de Colombia (America/Bogota), con la sintaxis cron de EventBridge Scheduler: minutos horas dia-del-mes mes dia-de-la-semana ano. Por omision, todos los dias a las 23:23."
  type        = string
  default     = "cron(23 23 * * ? *)"
}

variable "horario_encender" {
  description = "Cuando se enciende el host, en hora de Colombia (America/Bogota), sintaxis cron de EventBridge Scheduler. Por omision, todos los dias a las 06:47 (hasta el 10-oct era de lunes a viernes y el fin de semana el juego no estaba disponible): las JVM arrancan por turnos (unos 5 minutos) y el entorno queda listo antes de la jornada. El apagado nocturno no cambia."
  type        = string
  default     = "cron(47 6 * * ? *)"
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
