import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[hasPermission]',
  standalone: true
})
export class HasPermissionDirective {
  private currentRole = 'JUGADOR'; // Hardcoded for demo/AuthService state

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}

  @Input() set hasPermission(action: string) {
    const isPermitted = this.checkPermission(this.currentRole, action);
    if (isPermitted) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    } else {
      this.viewContainer.clear();
    }
  }

  // Matriz de permisos reactiva para la UI según Tabla 24
  private checkPermission(role: string, action: string): boolean {
    const matrix: Record<string, string[]> = {
      'JUGADOR': ['CREAR_CUENTA_JUGADOR', 'MODIFICAR_PERFIL_PROPIO', 'PUBLICAR_COMENTARIOS', 'ELIMINAR_COMENTARIO_PROPIO'],
      'MODERADOR': ['MODIFICAR_PERFIL_PROPIO', 'PUBLICAR_COMENTARIOS', 'ELIMINAR_COMENTARIO_PROPIO', 'MODERAR_COMENTARIOS', 'EMITIR_ADVERTENCIAS', 'SUSPENDER_USUARIOS'],
      'ADMINISTRADOR': ['MODIFICAR_PERFIL_PROPIO', 'PUBLICAR_COMENTARIOS', 'ELIMINAR_COMENTARIO_PROPIO', 'MODERAR_COMENTARIOS', 'EMITIR_ADVERTENCIAS', 'SUSPENDER_USUARIOS', 'BANEAR_DEFINITIVAMENTE', 'GESTIONAR_PRODUCTOS'],
      'SUPER_ADMINISTRADOR': ['MODIFICAR_PERFIL_PROPIO', 'PUBLICAR_COMENTARIOS', 'ELIMINAR_COMENTARIO_PROPIO', 'MODERAR_COMENTARIOS', 'EMITIR_ADVERTENCIAS', 'SUSPENDER_USUARIOS', 'BANEAR_DEFINITIVAMENTE', 'CREAR_ADMIN_MODERADOR', 'GESTIONAR_PRODUCTOS']
    };
    return matrix[role] ? matrix[role].includes(action) : false;
  }
}
