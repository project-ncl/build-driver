package org.jboss.pnc.builddriver.runtime;

import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.buildagent.common.http.HeartbeatHttpHeaderProvider;
import org.jboss.pnc.quarkus.client.auth.runtime.PNCClientAuth;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class HeartbeatHttpHeaderProviderImpl implements HeartbeatHttpHeaderProvider {

    @Inject
    PNCClientAuth pncClientAuth;

    @Override
    public List<Request.Header> getHeaders() {
        return Collections
                .singletonList(new Request.Header("Authorization", pncClientAuth.getHttpAuthorizationHeaderValue()));
    }
}
