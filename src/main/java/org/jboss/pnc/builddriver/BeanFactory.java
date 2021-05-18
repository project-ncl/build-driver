package org.jboss.pnc.builddriver;

import org.jboss.pnc.buildagent.common.http.HttpClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class BeanFactory {

    private HttpClient httpClient;

    @PostConstruct
    void init() throws IOException {
        httpClient = new HttpClient();
    }

    @Produces
    @ApplicationScoped
    public HttpClient getHttpClient() {
        return httpClient;
    }
}
