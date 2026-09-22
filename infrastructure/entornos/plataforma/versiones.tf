# Host de DESARROLLO del dominio de plataforma (services/plataforma/*) en la
# cuenta AWS de la empresa creada el 2026-09-15 con el Free Plan (USD 100 de
# credito al registro, hasta USD 100 mas por actividades, vence el
# 2027-03-15 o al agotar el credito). Politica de la cuenta: gasto de bolsillo
# USD 0, nunca Paid Plan, ningun recurso que facture fuera del credito.
#
# El estado vive en S3 (bucket creado a mano una sola vez desde CloudShell,
# ver README.md). Bucket y region NO se escriben aqui: los pasa
# .github/workflows/infra-dev.yml con -backend-config desde las variables del
# repositorio TFSTATE_BUCKET y AWS_REGION, para que este archivo no dependa
# del numero de cuenta.

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

  backend "s3" {
    key          = "entornos/plataforma-dev.tfstate"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  # Etiquetas en TODO recurso: asi Cost Explorer puede separar el gasto por
  # equipo y entorno, y la salida (DESTROY) encuentra todo lo que se creo aqui.
  default_tags {
    tags = {
      Proyecto = "nexus-battles-vi"
      Equipo   = "grupo-6"
      Entorno  = "dev"
      Gestion  = "terraform"
    }
  }
}
