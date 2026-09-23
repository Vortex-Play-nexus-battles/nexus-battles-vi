# Adopcion del host de contenido que YA existe — R9.1
#
# ## Por que este archivo existe
#
# `nexus-contenido-dev` lleva corriendo desde antes de que esta carpeta
# tuviera estado remoto. Mover el backend a S3 (ver `versiones.tf`) deja ese
# estado remoto VACIO, y un estado vacio no significa "todo bien": significa
# que Terraform cree que no existe nada y propone CREARLO. Aplicar eso
# levantaria una instancia nueva, una IP elastica nueva y un grupo de
# seguridad nuevo, mientras los actuales siguen en pie. Un tercer EC2 y coste
# duplicado, por un `apply` que parecia rutinario.
#
# La forma correcta de meter infraestructura existente bajo control es
# importarla, no recrearla. Estos bloques `import` son declarativos (OpenTofu
# 1.5+): se escriben en el codigo, `tofu plan` muestra que va a adoptar, y el
# `apply` que los ejecuta no crea nada.
#
# ## Como se rellenan
#
# Los identificadores hay que leerlos de la cuenta; no se pueden adivinar ni
# estan en el repositorio. El workflow `infra-dev.yml` tiene para eso una
# accion `inventario` que los imprime en el resumen de la corrida, en modo
# SOLO LECTURA (describe-*), sin tocar nada y sin imprimir ningun secreto:
#
#     Actions > Infra dev (AWS Free Plan) > Run workflow
#       host   = contenido
#       accion = inventario
#
# Con esa salida se descomentan los bloques de abajo y se rellenan los `id`.
#
# ## La compuerta, que no depende de que nadie se acuerde
#
# Despues de importar, `tofu plan` tiene que decir exactamente:
#
#     No changes. Your infrastructure matches the configuration.
#
# Si en vez de eso aparece UN SOLO `destroy` o `replace` sobre la instancia,
# la IP elastica, el volumen o el grupo de seguridad: NO se aplica. Se abre
# issue tecnico con el recurso, el motivo, el riesgo y la alternativa, y se
# sigue con el resto del bloque. El workflow ademas bloquea `apply` sobre esta
# carpeta mientras `IMPORTACION_CONTENIDO_COMPLETADA` no este en "true".
#
# ## Detalle util al rellenar
#
# `tls_private_key.contenido_dev` NO se puede importar: la clave privada solo
# existio en la memoria del proceso que la genero. Al adoptar el key pair sin
# ella, Terraform propondra reemplazar el key pair para regenerarla, y eso SI
# es un `replace`. La salida limpia es sacar la generacion de la llave de
# Terraform (la llave real ya esta en los secrets del entorno dev) y declarar
# el key pair con `lifecycle { ignore_changes = [public_key] }`. Esta anotado
# como lo primero a resolver cuando se ejecute la importacion; hasta entonces
# no se aplica nada.

# import {
#   to = aws_instance.contenido_dev
#   id = "i-XXXXXXXXXXXXXXXXX"
# }

# import {
#   to = aws_eip.contenido_dev
#   id = "eipalloc-XXXXXXXXXXXXXXXXX"
# }

# import {
#   to = aws_security_group.contenido_sg
#   id = "sg-XXXXXXXXXXXXXXXXX"
# }

# import {
#   to = aws_key_pair.contenido_dev
#   id = "contenido-dev-key"
# }
