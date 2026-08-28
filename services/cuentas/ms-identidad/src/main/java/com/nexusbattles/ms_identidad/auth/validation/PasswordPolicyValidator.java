package com.nexusbattles.ms_identidad.auth.validation;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    public void validar(String password) {
        if (password == null || password.length() <= 8) {
            throw new IllegalArgumentException("La contraseña debe tener una longitud superior a 8 caracteres.");
        }
        boolean tieneMayuscula = password.chars().anyMatch(Character::isUpperCase);
        boolean tieneMinuscula = password.chars().anyMatch(Character::isLowerCase);
        boolean tieneNumero = password.chars().anyMatch(Character::isDigit);
        boolean tieneSimbolo = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        if (!tieneMayuscula || !tieneMinuscula || !tieneNumero || !tieneSimbolo) {
            throw new IllegalArgumentException(
                    "La contraseña debe contener al menos una mayúscula, una minúscula, un número y un símbolo.");
        }
    }
}