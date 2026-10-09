package org.jboss.pnc.builddriver;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.pnc.buildagent.common.http.HeartbeatHttpHeaderProvider;
import org.jboss.pnc.buildagent.common.http.HeartbeatSender;
import org.jboss.pnc.buildagent.common.http.HttpClient;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class BeanFactory {

    private HttpClient httpClient;
    private HeartbeatSender heartbeatSender;

    @Inject
    HeartbeatHttpHeaderProvider heartbeatHttpProvider;

    @PostConstruct
    void init() throws IOException {
        httpClient = new HttpClient();
        heartbeatSender = new HeartbeatSender(httpClient, heartbeatHttpProvider);
    }

    @Produces
    @ApplicationScoped
    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Produces
    @ApplicationScoped
    public HeartbeatSender getHeartbeatSender() {
        return heartbeatSender;
    }
}
