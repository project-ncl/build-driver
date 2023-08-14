package org.jboss.pnc.builddriver;

import io.quarkus.oidc.client.Tokens;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

@ApplicationScoped
public class BeanMockFactory {

    @Produces
    public Tokens getTokens() {
        return new Tokens("abcd", 0L, null, "abcd", 0L, null);
    }
}
