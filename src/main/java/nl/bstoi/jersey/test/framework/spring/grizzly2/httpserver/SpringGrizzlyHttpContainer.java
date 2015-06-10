package nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.internal.LocalizationMessages;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.ExtendedLogger;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.*;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;
import org.springframework.context.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hylke Stapersma (codecentric nl)
 * hylke.stapersma@codecentric.nl
 */
public class SpringGrizzlyHttpContainer extends HttpHandler implements Container {

    private static final ExtendedLogger logger =
            new ExtendedLogger(Logger.getLogger(SpringGrizzlyHttpContainer.class.getName()), Level.FINEST);

    private static final Type RequestTYPE = (new TypeLiteral<Ref<Request>>() {
    }).getType();
    private static final Type ResponseTYPE = (new TypeLiteral<Ref<Response>>() {
    }).getType();
    /**
     * Cached value of configuration property
     * {@link org.glassfish.jersey.server.ServerProperties#RESPONSE_SET_STATUS_OVER_SEND_ERROR}.
     * If {@code true} method {@link org.glassfish.grizzly.http.server.Response#setStatus} is used over
     * {@link org.glassfish.grizzly.http.server.Response#sendError}.
     */
    private boolean configSetStatusOverSendError;

    /**
     * Referencing factory for Grizzly request.
     */
    private static class GrizzlyRequestReferencingFactory extends ReferencingFactory<Request> {
        @Inject
        public GrizzlyRequestReferencingFactory(Provider<Ref<Request>> referenceFactory) {
            super(referenceFactory);
        }
    }

    /**
     * Referencing factory for Grizzly response.
     */
    private static class GrizzlyResponseReferencingFactory extends ReferencingFactory<Response> {
        @Inject
        public GrizzlyResponseReferencingFactory(Provider<Ref<Response>> referenceFactory) {
            super(referenceFactory);
        }
    }

    /**
     * An internal binder to enable Grizzly HTTP container specific types injection.
     * <p>
     * This binder allows to inject underlying Grizzly HTTP request and response instances.
     * Note that since Grizzly {@code Request} class is not proxiable as it does not expose an empty constructor,
     * the injection of Grizzly request instance into singleton JAX-RS and Jersey providers is only supported via
     * {@link javax.inject.Provider injection provider}.
     */
    static class GrizzlyBinder extends AbstractBinder {

        @Override
        protected void configure() {
            bindFactory(GrizzlyRequestReferencingFactory.class).to(Request.class)
                    .proxy(false).in(RequestScoped.class);
            bindFactory(ReferencingFactory.<Request>referenceFactory()).to(new TypeLiteral<Ref<Request>>() {
            })
                    .in(RequestScoped.class);

            bindFactory(GrizzlyResponseReferencingFactory.class).to(Response.class)
                    .proxy(true).proxyForSameScope(false).in(RequestScoped.class);
            bindFactory(ReferencingFactory.<Response>referenceFactory()).to(new TypeLiteral<Ref<Response>>() {
            })
                    .in(RequestScoped.class);
        }
    }

    private static final CompletionHandler<Response> EMPTY_COMPLETION_HANDLER = new CompletionHandler<Response>() {

        @Override
        public void cancelled() {
            // no-op
        }

        @Override
        public void failed(Throwable throwable) {
            // no-op
        }

        @Override
        public void completed(Response result) {
            // no-op
        }

        @Override
        public void updated(Response result) {
            // no-op
        }
    };

    private final static class ResponseWriter implements ContainerResponseWriter {

        private final String name;
        private final Response grizzlyResponse;
        private final boolean configSetStatusOverSendError;

        ResponseWriter(final Response response, final boolean configSetStatusOverSendError) {
            this.grizzlyResponse = response;
            this.configSetStatusOverSendError = configSetStatusOverSendError;

            if (logger.isDebugLoggable()) {
                this.name = "ResponseWriter {" + "id=" + UUID.randomUUID().toString() + ", grizzlyResponse=" + grizzlyResponse.hashCode() + '}';
                logger.debugLog("{0} - init", name);
            } else {
                this.name = "ResponseWriter";
            }
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void commit() {
            try {
                if (grizzlyResponse.isSuspended()) {
                    grizzlyResponse.resume();
                }
            } finally {
                logger.debugLog("{0} - commit() called", name);
            }
        }

        @Override
        public boolean suspend(final long timeOut, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
            try {
                grizzlyResponse.suspend(timeOut, timeUnit, EMPTY_COMPLETION_HANDLER,
                        new org.glassfish.grizzly.http.server.TimeoutHandler() {

                            @Override
                            public boolean onTimeout(Response response) {
                                if (timeoutHandler != null) {
                                    timeoutHandler.onTimeout(ResponseWriter.this);
                                }

                                // TODO should we return true in some cases instead?
                                // Returning false relies on the fact that the timeoutHandler will resume the response.
                                return false;
                            }
                        }
                );
                return true;
            } catch (IllegalStateException ex) {
                return false;
            } finally {
                logger.debugLog("{0} - suspend(...) called", name);
            }
        }

        @Override
        public void setSuspendTimeout(long timeOut, TimeUnit timeUnit) throws IllegalStateException {
            try {
                grizzlyResponse.getSuspendContext().setTimeout(timeOut, timeUnit);
            } finally {
                logger.debugLog("{0} - setTimeout(...) called", name);
            }
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(final long contentLength,
                                                          final ContainerResponse context)
                throws ContainerException {
            try {
                final javax.ws.rs.core.Response.StatusType statusInfo = context.getStatusInfo();
                if (statusInfo.getReasonPhrase() == null) {
                    grizzlyResponse.setStatus(statusInfo.getStatusCode());
                } else {
                    grizzlyResponse.setStatus(statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
                }

                grizzlyResponse.setContentLengthLong(contentLength);

                for (final Map.Entry<String, List<String>> e : context.getStringHeaders().entrySet()) {
                    for (final String value : e.getValue()) {
                        grizzlyResponse.addHeader(e.getKey(), value);
                    }
                }

                return grizzlyResponse.getOutputStream();
            } finally {
                logger.debugLog("{0} - writeResponseStatusAndHeaders() called", name);
            }
        }

        @Override
        @SuppressWarnings("MagicNumber")
        public void failure(Throwable error) {
            try {
                if (!grizzlyResponse.isCommitted()) {
                    try {
                        if (configSetStatusOverSendError) {
                            grizzlyResponse.reset();
                            grizzlyResponse.setStatus(500, "Request failed.");
                        } else {
                            grizzlyResponse.sendError(500, "Request failed.");
                        }
                    } catch (IllegalStateException ex) {
                        // a race condition externally committing the response can still occur...
                        logger.log(Level.FINER, "Unable to reset failed response.", ex);
                    } catch (IOException ex) {
                        throw new ContainerException(
                                LocalizationMessages.EXCEPTION_SENDING_ERROR_RESPONSE(500, "Request failed."),
                                ex);
                    }
                }
            } finally {
                logger.debugLog("{0} - failure(...) called", name);
                rethrow(error);
            }
        }

        @Override
        public boolean enableResponseBuffering() {
            return true;
        }

        /**
         * Rethrow the original exception as required by JAX-RS, 3.3.4
         *
         * @param error throwable to be re-thrown
         */
        private void rethrow(Throwable error) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            } else {
                throw new ContainerException(error);
            }
        }
    }

    private volatile ApplicationHandler appHandler;
    private volatile ContainerLifecycleListener containerListener;

    /**
     * Create a new Grizzly HTTP container.
     *
     * @param application JAX-RS / Jersey application to be deployed on Grizzly HTTP container.
     */
    SpringGrizzlyHttpContainer(final Application application) {
        this.appHandler = new ApplicationHandler(application, new GrizzlyBinder());
        cacheConfigSetStatusOverSendError();
    }

    @Override
    public void start() {
        super.start();
        this.appHandler.onStartup(this);
    }

    @Override
    public void service(final Request request, final Response response) {
        final ResponseWriter responseWriter = new ResponseWriter(response, configSetStatusOverSendError);
        try {
            logger.debugLog("GrizzlyHttpContainer.service(...) started");
            URI baseUri = getBaseUri(request);
            ContainerRequest requestContext = new ContainerRequest(baseUri,
                    getRequestUri(baseUri, request), request.getMethod().getMethodString(),
                    getSecurityContext(request), new SpringGrizzlyRequestPropertiesDelegate(request));
            requestContext.setEntityStream(request.getInputStream());
            for (String headerName : request.getHeaderNames()) {
                requestContext.headers(headerName, request.getHeaders(headerName));
            }
            requestContext.setWriter(responseWriter);

            requestContext.setRequestScopedInitializer(new RequestScopedInitializer() {

                @Override
                public void initialize(ServiceLocator locator) {
                    locator.<Ref<Request>>getService(RequestTYPE).set(request);
                    locator.<Ref<Response>>getService(ResponseTYPE).set(response);
                }
            });
            appHandler.handle(requestContext);
        } finally {
            logger.debugLog("GrizzlyHttpContainer.service(...) finished");
        }
    }

    @Override
    public ResourceConfig getConfiguration() {
        return appHandler.getConfiguration();
    }

    @Override
    public void reload() {
        reload(appHandler.getConfiguration());
    }

    @Override
    public void reload(ResourceConfig configuration) {
        this.appHandler.onShutdown(this);
        appHandler = new ApplicationHandler(configuration, new GrizzlyBinder());
        containerListener.onReload(this);
        containerListener.onStartup(this);
        cacheConfigSetStatusOverSendError();
    }

    @Override
    public ApplicationHandler getApplicationHandler() {
        return appHandler;
    }

    @Override
    public void destroy() {
        super.destroy();
        this.appHandler.onShutdown(this);
        appHandler = null;
    }

    private SecurityContext getSecurityContext(final Request request) {
        return new SecurityContext() {

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return request.isSecure();
            }

            @Override
            public Principal getUserPrincipal() {
                return request.getUserPrincipal();
            }

            @Override
            public String getAuthenticationScheme() {
                return request.getAuthType();
            }
        };
    }

    private URI getBaseUri(final Request request) {
        try {
            return new URI(request.getScheme(), null, request.getServerName(),
                    request.getServerPort(), getBasePath(request), null, null);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private String getBasePath(final Request request) {
        final String contextPath = request.getContextPath();

        if (contextPath == null || contextPath.isEmpty()) {
            return "/";
        } else if (contextPath.charAt(contextPath.length() - 1) != '/') {
            return contextPath + "/";
        } else {
            return contextPath;
        }
    }

    private URI getRequestUri(URI baseUri, Request grizzlyRequest) {
        // TODO: this is terrible, there must be a way to obtain the original request URI!
        String originalUri = UriBuilder.fromPath(
                grizzlyRequest.getRequest().getRequestURIRef().getOriginalRequestURIBC().toString(Charsets.DEFAULT_CHARSET)
        ).build().toString();

        String queryString = grizzlyRequest.getQueryString();
        if (queryString != null) {
            originalUri = originalUri + "?" + queryString;
        }

        return baseUri.resolve(originalUri);
    }

    /**
     * The method reads and caches value of configuration property
     * {@link org.glassfish.jersey.server.ServerProperties#RESPONSE_SET_STATUS_OVER_SEND_ERROR} for future purposes.
     */
    private void cacheConfigSetStatusOverSendError() {
        this.configSetStatusOverSendError = ServerProperties.getValue(getConfiguration().getProperties(),
                ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, false, Boolean.class);
    }

    public ApplicationContext getSpringApplicationContext() {
        return getApplicationHandler().getServiceLocator().getService(ApplicationContext.class);
    }


}