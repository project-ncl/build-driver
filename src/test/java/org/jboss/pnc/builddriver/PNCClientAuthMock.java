package org.jboss.pnc.builddriver;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.pnc.builddriver.pncclientauth.PNCClientAuth;

import io.quarkus.test.Mock;

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