package org.jboss.pnc.builddriver;

import org.jboss.pnc.buildagent.common.http.HeartbeatHttpHeaderProvider;
import org.jboss.pnc.buildagent.common.http.HeartbeatSender;
import org.jboss.pnc.buildagent.common.http.HttpClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.IOException;

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
