package com.nexusbattles.ms_cumplimiento.auditoria.security;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSuperAdmin2FA {
}