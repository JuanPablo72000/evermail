package com.juanpablo.evermail.application;

import com.juanpablo.evermail.exception.*;

/** Safe user-facing information. Never exposes exception messages, credentials or mail bodies. */
public record OperationError(ErrorCode code, String message) {
    public static OperationError from(Exception failure) {
        ErrorCode code = failure instanceof EvermailException domain ? domain.getErrorCode()
                : failure instanceof InterruptedException ? ErrorCode.CANCELLED : null;
        if (code == null) return new OperationError(null, "No se pudo completar la operación.");
        if (code == ErrorCode.IMAP_CONNECTION_FAILED || code == ErrorCode.IMAP_FETCH_FAILED) {
            return new OperationError(code, inboxMessage(failure, code));
        }
        String message = switch (code) {
            case OAUTH_MAIL_PERMISSION_MISSING -> "El proveedor no concedió los permisos de correo necesarios. Vuelve a iniciar sesión y autoriza el acceso al buzón y el envío.";
            case SMTP_CONNECTION_FAILED -> "No se pudo conectar o autorizar el envío. Revisa que la cuenta permita SMTP con OAuth y que el puerto 587 esté disponible.";
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

    private static String inboxMessage(Exception failure, ErrorCode code) {
        // Classify causes only; server messages may contain account data or credentials.
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof jakarta.mail.AuthenticationFailedException)
                return "El servidor rechazó el acceso al buzón. Revisa los permisos de correo y que tu cuenta permita IMAP.";
            if (cause instanceof jakarta.mail.NoSuchProviderException)
                return "Falta el componente IMAP en esta instalación de Evermail. Es necesario revisar cómo se inicia la aplicación.";
            if (cause instanceof java.net.UnknownHostException)
                return "No se pudo localizar el servidor de correo. Revisa tu conexión a Internet y la configuración DNS.";
            if (cause instanceof javax.net.ssl.SSLException)
                return "No se pudo establecer una conexión segura con el servidor de correo. Revisa el certificado, la fecha del equipo y la conexión.";
            if (cause instanceof java.net.SocketTimeoutException)
                return "El servidor de correo superó el tiempo de espera. Pulsa Actualizar para reintentar.";
            if (cause instanceof java.net.ConnectException)
                return "No se pudo conectar al servidor IMAP. Revisa la red y si el puerto 993 está bloqueado.";
        }
        return code == ErrorCode.IMAP_CONNECTION_FAILED
                ? "No se pudo abrir el buzón (IMAP_CONNECTION_FAILED). Revisa la conexión y el acceso IMAP de tu cuenta."
                : "No se pudieron descargar los correos (IMAP_FETCH_FAILED). Pulsa Actualizar para reintentar.";
    }
}
