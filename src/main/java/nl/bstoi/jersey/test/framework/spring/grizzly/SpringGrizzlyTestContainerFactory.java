package nl.bstoi.jersey.test.framework.spring.grizzly;

import nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver.SpringGrizzlyHttpContainer;
import nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver.SpringGrizzlyHttpServerFactory;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestHelper;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hylke Stapersma (codecentric nl)
 * hylke.stapersma@codecentric.nl
 */
public class SpringGrizzlyTestContainerFactory implements SpringTestContainerFactory {

    private static class SpringGrizzlyTestContainer implements SpringTestContainer {

        private static final Logger LOGGER = Logger.getLogger(SpringGrizzlyTestContainer.class.getName());

        private URI baseUri;

        private final HttpServer server;

        private SpringGrizzlyTestContainer(final URI baseUri, final DeploymentContext context) {
            this.baseUri = UriBuilder.fromUri(baseUri).path(context.getContextPath()).build();

            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Creating GrizzlyTestContainer configured at the base URI "
                        + TestHelper.zeroPortToAvailablePort(baseUri));
            }

            this.server = SpringGrizzlyHttpServerFactory.createHttpServer(this.baseUri, context.getResourceConfig(), false);
        }

        @Override
        public ClientConfig getClientConfig() {
            return null;
        }

        @Override
        public URI getBaseUri() {
            return baseUri;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            for (org.glassfish.grizzly.http.server.HttpHandler httpHandler : this.server.getServerConfiguration().getHttpHandlers().keySet()) {
                if (httpHandler instanceof SpringGrizzlyHttpContainer) {
                    return ((SpringGrizzlyHttpContainer) httpHandler).getSpringApplicationContext();
                }
            }
            throw new IllegalStateException("No http handler found that exposes a spring application context");
        }

        @Override
        public void start() {
            if (server.isStarted()) {
                LOGGER.log(Level.WARNING, "Ignoring start request - GrizzlyTestContainer is already started.");

            } else {
                LOGGER.log(Level.FINE, "Starting GrizzlyTestContainer...");
                try {
                    server.start();

                    if (baseUri.getPort() == 0) {
                        baseUri = UriBuilder.fromUri(baseUri)
                                .port(server.getListener("grizzly").getPort())
                                .build();
                        LOGGER.log(Level.INFO, "Started GrizzlyTestContainer at the base URI " + baseUri);
                    }
                } catch (final IOException ioe) {
                    throw new TestContainerException(ioe);
                }
            }
        }

        @Override
        public void stop() {
            if (server.isStarted()) {
                LOGGER.log(Level.FINE, "Stopping GrizzlyTestContainer...");
                this.server.shutdownNow();
            } else {
                LOGGER.log(Level.WARNING, "Ignoring stop request - GrizzlyTestContainer is already stopped.");
            }
        }
    }

    @Override
    public SpringTestContainer create(final URI baseUri, final DeploymentContext context) {
        return new SpringGrizzlyTestContainer(baseUri, context);
    }
}