# Host de DESARROLLO del dominio de contenido (services/contenido/*: heroes,
# inventario, productos, motor-combate + su MongoDB), separado del host de
# plataforma. Sigue la regla de trabajo de plataforma "desplegar por perfil de
# dominio, no los 20 servicios a la vez" y el mismo patron de
# infrastructure/entornos/main.tf (Ubuntu 22.04, Docker por user_data, llave
# generada por Terraform). Estado propio: se aplica desde ESTA carpeta, no
# toca el estado de plataforma.
#
# Cuenta: la prueba gratuita / creditos de AWS que el cliente autorizo en la
# clase del 2026-09-03. Plan gratuito de AWS (cuentas desde julio de 2025):
# hasta 200 USD de credito por 6 meses y solo tipos de instancia elegibles
# (t3.micro, t3.small, t4g.micro, t4g.small, c7i-flex.large, m7i-flex.large).
# t3.small ~17 USD/mes + IP elastica ~3,6 USD/mes, descontados del credito:
# el semestre (hasta el 6 de noviembre) cabe con holgura. Apagar la
# instancia cuando no se use.
#
# Uso:
#   cd infrastructure/entornos/contenido
#   terraform init && terraform apply
#   terraform output -raw ip_publica
#   terraform output -raw llave_privada > contenido-dev.pem   # NUNCA al repo
# Los tres valores van a los secrets del entorno "dev" del repositorio
# (los carga un administrador): DEPLOY_HOST_CONTENIDO_DEV (la IP),
# SSH_USER_CONTENIDO_DEV (ubuntu) y SSH_KEY_CONTENIDO_DEV (el .pem completo).

terraform {
  required_providers {
    aws = { source = "hashicorp/aws" }
    tls = { source = "hashicorp/tls" }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  description = "Region de AWS. Misma que plataforma para no confundir consolas."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "t3.small (2 GB) es un tipo elegible del nivel gratuito para cuentas nuevas y alcanza para los 4 servicios + Mongo con los limites del compose (~1,7 GB). t3.medium no es elegible en el plan gratuito."
  type        = string
  default     = "t3.small"
}

variable "cidr_ssh" {
  description = "Desde donde se acepta SSH (puerto 22). 0.0.0.0/0 solo con autenticacion por llave, que es lo unico que la AMI de Ubuntu permite; GitHub Actions no tiene IP fija."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

resource "tls_private_key" "contenido_dev" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "contenido_dev" {
  key_name   = "contenido-dev-key"
  public_key = tls_private_key.contenido_dev.public_key_openssh
}

# Puertos del bloque de contenido (docker-compose.contenido.yml y puerto_de()
# en cd.yml): 8101 heroes, 8102 inventario, 8103 productos, 8104 motor.
resource "aws_security_group" "contenido_sg" {
  name        = "contenido-dev-sg"
  description = "SSH para el flujo de despliegue y los puertos de los servicios de contenido"

  ingress {
    description = "SSH: por aqui entra cd.yml (scp + ssh) a desplegar"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.cidr_ssh
  }
  ingress {
    description = "Servicios de contenido (heroes, inventario, productos, motor)"
    from_port   = 8101
    to_port     = 8104
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "contenido_dev" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.contenido_dev.key_name
  vpc_security_group_ids = [aws_security_group.contenido_sg.id]
  # Lo que desplegar.sh da por hecho en el servidor: Docker, Compose v2, el
  # usuario ubuntu en el grupo docker y /opt/nexus escribible por el.
  #
  # R8.3 — mas un swap de 2 GB, como el de plataforma
  # (infrastructure/entornos/plataforma/main.tf). Este host nacio sin el: con
  # 1,9 GiB de RAM contra 1.760 MB de `mem_limit`, el margen declarado es de
  # -114 MB, y aqui viven los cuatro servicios de los que depende el combate.
  # Sin swap un pico de arranque no degrada: el OOM killer mata un contenedor.
  # Es disco gp3 ya pagado; no cuesta un centavo mas.
  #
  # OJO — esto NO arregla el host que ya esta corriendo: `user_data` solo se
  # ejecuta en el primer arranque. Al host vivo lo arregla
  # `scripts/cd/asegurar-swap.sh`, que corre desde `desplegar.sh` en cada
  # despliegue y es idempotente. Este bloque es para cualquier reconstruccion
  # futura, para que no vuelva a nacer sin swap.
  user_data = <<-EOT
    #!/bin/bash
    apt-get update -y
    apt-get install -y docker.io docker-compose-v2
    usermod -aG docker ubuntu
    mkdir -p /opt/nexus && chown ubuntu:ubuntu /opt/nexus
    if [ ! -f /swapfile ]; then
      fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile
      echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
    swapon -a
  EOT

  # **Esta linea es la que impide un desastre.** Por omision, cambiar
  # `user_data` marca la instancia para REEMPLAZO: Terraform la destruiria y
  # crearia otra, perdiendo /opt/nexus y obligando a reasociar la IP elastica.
  # Con `false`, el cambio se registra en el estado y no toca el host.
  # Plataforma la lleva desde su PR original por la misma razon.
  user_data_replace_on_change = false

  root_block_device {
    volume_size = 16
  }
  tags = { Name = "nexus-contenido-dev" }

  lifecycle {
    # La AMI "mas reciente" cambia cada pocas semanas. Sin esto, un `apply`
    # rutinario recrearia el host. Mismo motivo y misma linea que en
    # plataforma.
    ignore_changes = [ami]
  }
}

# IP fija: la instancia se puede apagar y prender sin que cambie el secreto.
resource "aws_eip" "contenido_dev" {
  instance = aws_instance.contenido_dev.id
  tags     = { Name = "nexus-contenido-dev" }
}

output "ip_publica" {
  description = "Valor de DEPLOY_HOST_CONTENIDO_DEV"
  value       = aws_eip.contenido_dev.public_ip
}

output "usuario_ssh" {
  description = "Valor de SSH_USER_CONTENIDO_DEV"
  value       = "ubuntu"
}

output "llave_privada" {
  description = "Valor de SSH_KEY_CONTENIDO_DEV (terraform output -raw llave_privada)"
  value       = tls_private_key.contenido_dev.private_key_pem
  sensitive   = true
}
