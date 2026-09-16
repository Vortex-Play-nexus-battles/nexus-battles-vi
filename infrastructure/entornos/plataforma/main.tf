# Un solo host para el dominio de plataforma. Sigue el patron de
# infrastructure/entornos/contenido/main.tf (Ubuntu 22.04, Docker por
# user_data, llave generada por Terraform, IP fija) porque cd.yml y
# scripts/cd/desplegar.sh dan por hecho ese servidor: scp + ssh como `ubuntu`,
# Docker Compose v2 y /opt/nexus escribible.
#
# Lo que cambia respecto a infrastructure/entornos/main.tf (3 hosts t3.micro
# dev/test/prod sin estado remoto): un unico entorno dev, tipo elegible del
# Free Plan validado, disco gp3 explicito, IMDSv2 obligatorio, perfil de
# instancia para SSM (Session Manager, gratuito: acceso sin abrir el 22 cuando
# el despliegue deje el ssh) y la llave privada guardada como parametro
# SecureString en vez de imprimirse en una terminal.

locals {
  nombre        = "nexus-plataforma-dev"
  arquitectura  = startswith(var.instance_type, "t4g") ? "arm64" : "amd64"
  puerto_inicio = 8081
  puerto_fin    = 8088
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-${local.arquitectura}-server-*"]
  }
}

# ---------------------------------------------------------------------------
# Llave del flujo de despliegue (cd.yml -> secreto SSH_KEY_DEV)
# ---------------------------------------------------------------------------
resource "tls_private_key" "despliegue" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "despliegue" {
  key_name   = "${local.nombre}-despliegue"
  public_key = tls_private_key.despliegue.public_key_openssh
}

# La llave privada no sale por `output` a una terminal ni a los logs de
# Actions: queda cifrada en Parameter Store (KMS gestionado por AWS, dentro
# de las 20.000 peticiones gratuitas al mes). Un administrador la lee desde
# la consola y la pega en el secreto SSH_KEY_DEV del entorno `dev`.
resource "aws_ssm_parameter" "llave_privada" {
  name        = "/nexus/dev/plataforma/llave-ssh-despliegue"
  description = "Llave privada RSA del par ${aws_key_pair.despliegue.key_name}. Valor del secreto SSH_KEY_DEV."
  type        = "SecureString"
  value       = tls_private_key.despliegue.private_key_pem
  tier        = "Standard"
}

# ---------------------------------------------------------------------------
# Red
# ---------------------------------------------------------------------------
resource "aws_security_group" "plataforma" {
  name        = "${local.nombre}-sg"
  description = "SSH para cd.yml y puertos publicados de los servicios de plataforma"

  dynamic "ingress" {
    for_each = length(var.cidr_ssh) > 0 ? [1] : []
    content {
      description = "SSH: por aqui entra cd.yml (scp + ssh) a desplegar"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.cidr_ssh
    }
  }

  ingress {
    description = "Borde nginx (infrastructure/red-balanceo/borde-dev.conf): frontend + /api/v1/* + /ws en un solo origen"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Servicios de plataforma: comentarios 8081 ... admin-parametros 8088"
    from_port   = local.puerto_inicio
    to_port     = local.puerto_fin
    protocol    = "tcp"
    cidr_blocks = var.cidr_servicios
  }

  ingress {
    description = "ms-identidad (Cuentas) en el host de plataforma, como lo espera cd.yml"
    from_port   = 8089
    to_port     = 8089
    protocol    = "tcp"
    cidr_blocks = var.cidr_servicios
  }

  egress {
    description = "Salida libre: GHCR, apt, Brevo"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.nombre}-sg" }
}

# ---------------------------------------------------------------------------
# Identidad de la instancia: solo Session Manager. Sin claves de AWS dentro
# del host; el host no necesita llamar a ningun servicio de AWS.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "confianza_ec2" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "host" {
  name               = "${local.nombre}-host"
  assume_role_policy = data.aws_iam_policy_document.confianza_ec2.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "host" {
  name = "${local.nombre}-host"
  role = aws_iam_role.host.name
}

# ---------------------------------------------------------------------------
# Instancia
# ---------------------------------------------------------------------------
resource "aws_instance" "plataforma" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.despliegue.key_name
  vpc_security_group_ids = [aws_security_group.plataforma.id]
  iam_instance_profile   = aws_iam_instance_profile.host.name

  # Credito de CPU "standard": una t3/t4g en modo unlimited puede facturar
  # USD 0,05 por vCPU-hora extra fuera del precio base. Aqui no. Los tipos
  # *-flex no tienen este bloque.
  dynamic "credit_specification" {
    for_each = startswith(var.instance_type, "t") ? [1] : []
    content {
      cpu_credits = "standard"
    }
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv2
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.tamano_disco_gb
    delete_on_termination = true
    encrypted             = true
  }

  # Lo que desplegar.sh da por hecho en el servidor, mas un swap de 2 GB:
  # con 2 GiB de RAM y varias JVM, el swap evita que el kernel mate un
  # servicio en un pico de arranque. Es disco gp3 ya pagado, no cuesta mas.
  user_data = <<-EOT
    #!/bin/bash
    set -eu
    apt-get update -y
    apt-get install -y docker.io docker-compose-v2
    usermod -aG docker ubuntu
    mkdir -p /opt/nexus && chown ubuntu:ubuntu /opt/nexus
    if [ ! -f /swapfile ]; then
      fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile
      echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
    swapon -a
    snap start amazon-ssm-agent 2>/dev/null || true
  EOT

  user_data_replace_on_change = false

  tags = { Name = local.nombre }

  lifecycle {
    # La AMI "mas reciente" cambia cada pocas semanas; no queremos que un
    # `apply` rutinario recree el host y borre /opt/nexus.
    ignore_changes = [ami]
  }
}

# IP fija: cd.yml la guarda en el secreto DEPLOY_HOST_DEV y no puede cambiar
# cada vez que la instancia se apaga y se enciende. Coste oficial: USD 0,005/h
# (3,60 USD/mes) tanto en uso como ociosa -- es el unico recurso que sigue
# facturando con la instancia apagada, junto con el disco. DESTROY la libera.
resource "aws_eip" "plataforma" {
  domain   = "vpc"
  instance = aws_instance.plataforma.id
  tags     = { Name = local.nombre }
}
