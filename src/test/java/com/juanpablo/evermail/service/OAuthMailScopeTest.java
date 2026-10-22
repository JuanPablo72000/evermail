package com.juanpablo.evermail.service;

import com.google.gson.JsonParser;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.OAuthProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OAuthMailScopeTest {
    @Test void microsoftRequiresBothExchangeMailScopesNotGraphMailScopes() {
        for (String scope : new String[]{"Mail.Read Mail.Send", "IMAP.AccessAsUser.All", "SMTP.Send",
                "https://graph.microsoft.com/Mail.Read https://graph.microsoft.com/Mail.Send"}) {
            var error = assertThrows(OAuthAuthenticationException.class,
                    () -> OAuthClient.validateMailScope(OAuthProvider.MICROSOFT,
                            JsonParser.parseString("{\"scope\":\"" + scope + "\"}").getAsJsonObject()));
            assertEquals(ErrorCode.OAUTH_MAIL_PERMISSION_MISSING, error.getErrorCode());
        }
        for (String prefix : new String[]{"", "https://outlook.office.com/", "https://outlook.office365.com/"}) {
            var response = JsonParser.parseString("{\"scope\":\"" + prefix + "IMAP.AccessAsUser.All "
                    + prefix + "SMTP.Send\"}").getAsJsonObject();
            assertDoesNotThrow(() -> OAuthClient.validateMailScope(OAuthProvider.MICROSOFT, response));
        }
    }

    @Test void rejectsIdentityOnlyAndReadOnlyApiPermissions() {
        for (String scope : new String[]{"openid email profile", "https://www.googleapis.com/auth/gmail.readonly", "", "https://mail.google.com/invalid"}) {
            var response = JsonParser.parseString("{\"scope\":\"" + scope + "\",\"access_token\":\"PRIVATE\"}").getAsJsonObject();
            var error = assertThrows(OAuthAuthenticationException.class,
                    () -> OAuthClient.validateMailScope(OAuthProvider.GOOGLE, response));
            assertEquals(ErrorCode.OAUTH_MAIL_PERMISSION_MISSING, error.getErrorCode());
            assertFalse(error.toString().contains("PRIVATE"));
        }
    }

    @Test void acceptsMailPermissionAndOmittedUnchangedScopes() {
        for (String json : new String[]{"{\"scope\":\"openid https://mail.google.com/ email\"}", "{}"})
            assertDoesNotThrow(() -> OAuthClient.validateMailScope(OAuthProvider.GOOGLE,
                    JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test void malformedScopeIsInvalidRatherThanAMissingPermission() {
        for (String value : new String[]{"null", "42", "[]", "{}"}) {
            var error = assertThrows(OAuthAuthenticationException.class,
                    () -> OAuthClient.validateMailScope(OAuthProvider.GOOGLE,
                            JsonParser.parseString("{\"scope\":" + value + "}").getAsJsonObject()));
            assertEquals(ErrorCode.OAUTH_TOKEN_INVALID, error.getErrorCode());
        }
    }
}
