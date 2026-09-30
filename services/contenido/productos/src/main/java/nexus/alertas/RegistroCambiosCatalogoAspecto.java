package nexus.alertas;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import nexus.dominio.Producto;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RegistroCambiosCatalogoAspecto {

    private static final Logger BITACORA = LoggerFactory.getLogger(RegistroCambiosCatalogoAspecto.class);

    private static final Set<String> CAMPOS_BALANCE = Set.of(
            "costoPoder",
            "multiplicadorNivel",
            "turnosCarga",
            "turnosRecarga",
            "efectoGeneral",
            "efectoPotenciado",
            "defensa",
            "efecto",
            "poderDeAtaque",
            "tasaDeCaida");

    private final AlertasCatalogoServicio alertas;

    public RegistroCambiosCatalogoAspecto(AlertasCatalogoServicio alertas) {
        this.alertas = alertas;
    }

    @AfterReturning(
            pointcut = "execution(nexus.dominio.Producto nexus.aplicacion.CrearProductoServicio.crear(..))",
            returning = "resultado")
    public void despuesDeCrear(Object resultado) {
        if (resultado instanceof Producto producto) {
            registrar(TipoCambioCatalogo.NUEVO_PRODUCTO, producto);
        }
    }

    @AfterReturning(
            pointcut = "execution(nexus.dominio.Producto nexus.aplicacion.ModificarProductoServicio.modificar(..))",
            returning = "resultado")
    public void despuesDeModificar(JoinPoint llamada, Object resultado) {
        if (resultado instanceof Producto producto) {
            TipoCambioCatalogo tipo = contieneCambioDeBalance(llamada.getArgs())
                    ? TipoCambioCatalogo.CAMBIO_BALANCE
                    : TipoCambioCatalogo.PRODUCTO_MODIFICADO;
            registrar(tipo, producto);
        }
    }

    @Around("execution(* nexus.productos.dominio.CatalogoProductos.suspender(..))")
    public Object alSuspender(ProceedingJoinPoint llamada) throws Throwable {
        return registrarCambioDeEstado(
                llamada,
                TipoCambioCatalogo.PRODUCTO_SUSPENDIDO);
    }

    @Around("execution(* nexus.productos.dominio.CatalogoProductos.reactivar(..))")
    public Object alReactivar(ProceedingJoinPoint llamada) throws Throwable {
        return registrarCambioDeEstado(
                llamada,
                TipoCambioCatalogo.PRODUCTO_REACTIVADO);
    }

    private Object registrarCambioDeEstado(
            ProceedingJoinPoint llamada,
            TipoCambioCatalogo tipo) throws Throwable {
        CatalogoProductos catalogo = (CatalogoProductos) llamada.getTarget();
        String productoId = (String) llamada.getArgs()[0];
        DisponibilidadProducto anterior = catalogo.consultar(productoId);
        Object resultado = llamada.proceed();
        if (resultado instanceof DisponibilidadProducto producto
                && producto.estado() != anterior.estado()) {
            try {
                alertas.registrarEstado(tipo, producto.productoId());
            } catch (RuntimeException error) {
                BITACORA.error(
                        "No se pudo registrar la alerta {} del producto {}",
                        tipo,
                        producto.productoId(),
                        error);
            }
        }
        return resultado;
    }

    private void registrar(TipoCambioCatalogo tipo, Producto producto) {
        try {
            alertas.registrar(tipo, producto);
        } catch (RuntimeException error) {
            BITACORA.error(
                    "No se pudo registrar la alerta {} del producto {}",
                    tipo,
                    producto.id(),
                    error);
        }
    }

    private boolean contieneCambioDeBalance(Object[] argumentos) {
        return Arrays.stream(argumentos)
                .filter(argumento -> argumento != null && argumento.getClass().isRecord())
                .flatMap(argumento -> componentesPresentes(argumento).stream())
                .anyMatch(CAMPOS_BALANCE::contains);
    }

    private Set<String> componentesPresentes(Object argumento) {
        return Arrays.stream(argumento.getClass().getRecordComponents())
                .filter(componente -> valorPresente(componente, argumento))
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private boolean valorPresente(RecordComponent componente, Object argumento) {
        try {
            return componente.getAccessor().invoke(argumento) != null;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }
}
