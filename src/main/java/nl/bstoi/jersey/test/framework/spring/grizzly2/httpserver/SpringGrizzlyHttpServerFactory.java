package nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver;

import nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver.SpringGrizzlyHttpContainer;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.internal.LocalizationMessages;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.net.URI;

/**
 * Hylke Stapersma (codecentric nl)
 * hylke.stapersma@codecentric.nl
 */
public final class SpringGrizzlyHttpServerFactory {

    private static final int DEFAULT_HTTP_PORT = 80;

    /**
     * Create new {@link org.glassfish.grizzly.http.server.HttpServer} instance.
     *
     * @param uri uri on which the {@link org.glassfish.jersey.server.ApplicationHandler} will be deployed. Only first path segment will be used as
     *            context path, the rest will be ignored.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri) {
        return createHttpServer(uri, (SpringGrizzlyHttpContainer) null, false, null, true);
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri   uri on which the {@link org.glassfish.jersey.server.ApplicationHandler} will be deployed. Only first path segment will be used
     *              as context path, the rest will be ignored.
     * @param start if set to false, server will not get started, which allows to configure the underlying transport
     *              layer, see above for details.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri, final boolean start) {
        return createHttpServer(uri, (SpringGrizzlyHttpContainer) null, false, null, start);
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri           URI on which the Jersey web application will be deployed. Only first path segment will be
     *                      used as context path, the rest will be ignored.
     * @param configuration web application configuration.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri, final ResourceConfig configuration) {
        return createHttpServer(
                uri,
                new SpringGrizzlyHttpContainer(configuration),
                false,
                null,
                true
        );
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri           URI on which the Jersey web application will be deployed. Only first path segment will be
     *                      used as context path, the rest will be ignored.
     * @param configuration web application configuration.
     * @param start         if set to false, server will not get started, which allows to configure the underlying
     *                      transport layer, see above for details.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri, final ResourceConfig configuration, final boolean start) {
        return createHttpServer(
                uri,
                new SpringGrizzlyHttpContainer(configuration),
                false,
                null,
                start);
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri                   URI on which the Jersey web application will be deployed. Only first path segment
     *                              will be used as context path, the rest will be ignored.
     * @param configuration         web application configuration.
     * @param secure                used for call {@link org.glassfish.grizzly.http.server.NetworkListener#setSecure(boolean)}.
     * @param sslEngineConfigurator Ssl settings to be passed to {@link org.glassfish.grizzly.http.server.NetworkListener#setSSLEngineConfig}.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri,
                                              final ResourceConfig configuration,
                                              final boolean secure,
                                              final SSLEngineConfigurator sslEngineConfigurator) {
        return createHttpServer(
                uri,
                new SpringGrizzlyHttpContainer(configuration),
                secure,
                sslEngineConfigurator,
                true);
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri                   URI on which the Jersey web application will be deployed. Only first path segment
     *                              will be used as context path, the rest will be ignored.
     * @param configuration         web application configuration.
     * @param secure                used for call {@link org.glassfish.grizzly.http.server.NetworkListener#setSecure(boolean)}.
     * @param sslEngineConfigurator Ssl settings to be passed to {@link org.glassfish.grizzly.http.server.NetworkListener#setSSLEngineConfig}.
     * @param start                 if set to false, server will not get started, which allows to configure the
     *                              underlying transport, see above for details.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     */
    public static HttpServer createHttpServer(final URI uri,
                                              final ResourceConfig configuration,
                                              final boolean secure,
                                              final SSLEngineConfigurator sslEngineConfigurator,
                                              final boolean start) {
        return createHttpServer(
                uri,
                new SpringGrizzlyHttpContainer(configuration),
                secure,
                sslEngineConfigurator,
                start);
    }

    /**
     * Create new {@link HttpServer} instance.
     *
     * @param uri                   uri on which the {@link org.glassfish.jersey.server.ApplicationHandler} will be deployed. Only first path
     *                              segment will be used as context path, the rest will be ignored.
     * @param handler               {@link org.glassfish.grizzly.http.server.HttpHandler} instance.
     * @param secure                used for call {@link org.glassfish.grizzly.http.server.NetworkListener#setSecure(boolean)}.
     * @param sslEngineConfigurator Ssl settings to be passed to {@link org.glassfish.grizzly.http.server.NetworkListener#setSSLEngineConfig}.
     * @param start                 if set to false, server will not get started, this allows end users to set
     *                              additional properties on the underlying listener.
     * @return newly created {@code HttpServer}.
     *
     * @throws javax.ws.rs.ProcessingException in case of any failure when creating a new {@code HttpServer} instance.
     * @see GrizzlyHttpContainer
     */
    public static HttpServer createHttpServer(final URI uri,
                                              final SpringGrizzlyHttpContainer handler,
                                              final boolean secure,
                                              final SSLEngineConfigurator sslEngineConfigurator,
                                              final boolean start) {

        final String host = (uri.getHost() == null) ? NetworkListener.DEFAULT_NETWORK_HOST : uri.getHost();
        final int port = (uri.getPort() == -1) ? DEFAULT_HTTP_PORT : uri.getPort();

        final NetworkListener listener = new NetworkListener("grizzly", host, port);
        listener.setSecure(secure);
        if (sslEngineConfigurator != null) {
            listener.setSSLEngineConfig(sslEngineConfigurator);
        }

        final HttpServer server = new HttpServer();
        server.addListener(listener);

        // Map the path to the processor.
        final ServerConfiguration config = server.getServerConfiguration();
        if (handler != null) {
            config.addHttpHandler(handler, uri.getPath());
        }

        config.setPassTraceRequest(true);

        if (start) {
            try {
                // Start the server.
                server.start();
            } catch (IOException ex) {
                server.shutdownNow();
                throw new ProcessingException(LocalizationMessages.FAILED_TO_START_SERVER(ex.getMessage()), ex);
            }
        }

        return server;
    }

    /**
     * Prevents instantiation.
     */
    private SpringGrizzlyHttpServerFactory() {
    }
}
