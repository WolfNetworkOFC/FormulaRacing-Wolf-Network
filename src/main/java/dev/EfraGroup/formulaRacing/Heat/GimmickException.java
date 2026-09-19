package dev.EfraGroup.formulaRacing.Heat;

/**
 * Failure that already carries a message ready to be shown to the admin,
 * so commands can simply do {@code player.sendMessage("§c✗ " + e.getMessage())}.
 */
public class GimmickException extends RuntimeException {

    public GimmickException(String message) {
        super(message);
    }

    public GimmickException(String message, Throwable cause) {
        super(message, cause);
    }
}
