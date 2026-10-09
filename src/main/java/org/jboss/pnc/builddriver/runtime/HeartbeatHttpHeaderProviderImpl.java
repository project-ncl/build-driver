package org.jboss.pnc.builddriver.runtime;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.buildagent.common.http.HeartbeatHttpHeaderProvider;
import org.jboss.pnc.builddriver.pncclientauth.PNCClientAuth;

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
