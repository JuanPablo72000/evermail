package com.juanpablo.evermail.application;

import com.juanpablo.evermail.exception.*;

/** Safe user-facing information. Never exposes exception messages, credentials or mail bodies. */
public record OperationError(ErrorCode code, String message) {
    public static OperationError from(Exception failure) {
        ErrorCode code = failure instanceof EvermailException domain ? domain.getErrorCode()
                : failure instanceof InterruptedException ? ErrorCode.CANCELLED : null;
        if (code == null) return new OperationError(null, "No se pudo completar la operación.");
        String message = switch (code) {
            case CANCELLED -> "La operación fue cancelada.";
            case LOCAL_TIMEOUT, NETWORK_TIMEOUT, OAUTH_TIMEOUT -> "La operación superó el tiempo de espera.";
            case REAUTH_REQUIRED, OAUTH_INVALID_CREDENTIALS, OAUTH_TOKEN_EXPIRED -> "Vuelve a autorizar tu cuenta.";
            case CONFIG_INVALID -> "Revisa la configuración del proveedor de correo.";
            case INVALID_RECIPIENT -> "Revisa los destinatarios y los campos del correo.";
            case DELIVERY_UNKNOWN -> "No se pudo confirmar el envío. Comprueba el correo antes de volver a enviarlo.";
            case LOCAL_SAVE_PENDING -> "El guardado local está pendiente. No vuelvas a enviar el correo.";
            case SESSION_UNAVAILABLE -> "La sesión no está disponible para esta operación.";
            case MAIL_NOT_FOUND -> "El correo ya no está disponible.";
            case DB_CONNECTION_FAILED, DB_QUERY_FAILED, DB_MIGRATION_FAILED,
                    CRYPTO_OPERATION_FAILED, KEY_NOT_FOUND -> "No se pudo acceder al almacenamiento local.";
            default -> "No se pudo completar la operación de correo.";
        };
        return new OperationError(code, message);
    }
}
