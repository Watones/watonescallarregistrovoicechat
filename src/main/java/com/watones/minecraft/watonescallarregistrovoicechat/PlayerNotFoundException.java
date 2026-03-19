package com.watones.minecraft.watonescallarregistrovoicechat;

final class PlayerNotFoundException extends RuntimeException {

    PlayerNotFoundException(String targetName) {
        super("Jugador no encontrado: " + targetName);
    }
}
