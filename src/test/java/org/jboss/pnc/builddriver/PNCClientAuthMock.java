package org.jboss.pnc.builddriver;

import io.quarkus.test.Mock;
import org.jboss.pnc.quarkus.client.auth.runtime.PNCClientAuth;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;

@Mock
@ApplicationScoped
public class PNCClientAuthMock implements PNCClientAuth {
    @Override
    public String getAuthToken() {
        return "1234";
    }

    @Override
    public String getHttpAuthorizationHeaderValue() {
        return "Bearer 1234";
    }

    @Override
    public String getHttpAuthorizationHeaderValueWithCachedToken() {
        return getHttpAuthorizationHeaderValue();
    }

    @Override
    public LDAPCredentials getLDAPCredentials() throws IOException {
        return new LDAPCredentials("user", "password");
    }
}