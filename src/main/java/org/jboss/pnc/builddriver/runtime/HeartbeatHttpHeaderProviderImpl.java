package org.jboss.pnc.builddriver.runtime;

import io.quarkus.oidc.client.OidcClient;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.buildagent.common.http.HeartbeatHttpHeaderProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class HeartbeatHttpHeaderProviderImpl implements HeartbeatHttpHeaderProvider {

    @Inject
    OidcClient oidcClient;

    @Override
    public List<Request.Header> getHeaders() {
        return Collections.singletonList(new Request.Header("Authorization", "Bearer " + getFreshAccessToken()));
    }

    /**
     * Get an access token for the service account
     *
     * @return fresh access token
     */
    private String getFreshAccessToken() {
        return oidcClient.getTokens().await().indefinitely().getAccessToken();
    }
}
