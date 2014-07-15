package nl.bstoi.jersey.test.framework.spring;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import nl.bstoi.jersey.test.framework.spring.grizzly.SpringGrizzlyTestContainerFactory;
import nl.bstoi.jersey.test.framework.spring.grizzly.SpringTestContainer;
import nl.bstoi.jersey.test.framework.spring.grizzly.SpringTestContainerFactory;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.internal.ServiceFinderBinder;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.spi.TestContainer;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.After;
import org.junit.Before;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.security.AccessController;
import java.util.*;
import java.util.logging.*;

/**
 * Hylke Stapersma (codecentric nl)
 *
 * Copy of JerseyTest @see org.glassfish.jersey.test.JerseyTest where the spring ApplicationContext is exposed
 * hylke.stapersma@codecentric.nl
 */
public class SpringContextJerseyTest {

    private static final Logger LOGGER = Logger.getLogger(SpringContextJerseyTest.class.getName());

    /**
     * Holds the default test container factory class to be used for running the
     * tests.
     */
    private static Class<? extends SpringTestContainerFactory> testContainerFactoryClass;
    /**
     * The test container factory which creates an instance of the test container
     * on which the tests would be run.
     */
    private TestContainerFactory testContainerFactory;
    /**
     * The test container on which the tests would be run.
     */
    private final SpringTestContainer tc;
    private Client client;

    private final ResourceConfig resourceConfig;

    private final DeploymentContext deploymentContext;
    /**
     * JerseyTest property bag that can be used to configure the test behavior.
     * These properties can be overridden with a system property.
     */
    private final Map<String, String> propertyMap = Maps.newHashMap();
    /**
     * JerseyTest forced property bag that can be used to configure the test behavior.
     * These property cannot be overridden with a system property.
     */
    private final Map<String, String> forcedPropertyMap = Maps.newHashMap();

    private Handler logHandler;
    private List<LogRecord> loggedStartupRecords = Lists.newArrayList();
    private List<LogRecord> loggedRuntimeRecords = Lists.newArrayList();
    private Map<Logger, Level> logLevelMap = Maps.newIdentityHashMap();

    /**
     * An extending class must implement the {@link #configure()} method to
     * provide an applicationHandler descriptor.
     *
     * @throws org.glassfish.jersey.test.spi.TestContainerException if the default test container factory
     *                                                              cannot be obtained, or the applicationHandler descriptor is not
     *                                                              supported by the test container factory.
     */
    public SpringContextJerseyTest() throws TestContainerException {
        final Application application = configure();
        deploymentContext = DeploymentContext.builder(application).build();
        resourceConfig = deploymentContext.getResourceConfig();
        resourceConfig.register(new ServiceFinderBinder<TestContainerFactory>(TestContainerFactory.class, null, RuntimeType.SERVER));

        if (isLogRecordingEnabled()) {
            registerLogHandler();
        }
        this.tc = getContainer(getTestContainerFactory());
        if (isLogRecordingEnabled()) {
            loggedStartupRecords.addAll(loggedRuntimeRecords);
            loggedRuntimeRecords.clear();
            unregisterLogHandler();
        }
    }

    /**
     * Programmatically enable a feature with a given name.
     * Enabling of the feature may be overridden via a system property.
     *
     * @param featureName name of the enabled feature.
     */
    protected final void enable(String featureName) {
        // TODO: perhaps we could reuse the resource config for the test properties?
        propertyMap.put(featureName, Boolean.TRUE.toString());
    }

    /**
     * Programmatically disable a feature with a given name.
     * Disabling of the feature may be overridden via a system property.
     *
     * @param featureName name of the disabled feature.
     */
    protected final void disable(String featureName) {
        propertyMap.put(featureName, Boolean.FALSE.toString());
    }

    /**
     * Programmatically force-enable a feature with a given name.
     * Force-enabling of the feature cannot be overridden via a system property.
     * Use with care!
     *
     * @param featureName name of the force-enabled feature.
     */
    protected final void forceEnable(String featureName) {
        forcedPropertyMap.put(featureName, Boolean.TRUE.toString());
    }

    /**
     * Programmatically force-disable a feature with a given name.
     * Force-disabling of the feature cannot be overridden via a system property.
     * Use with care!
     *
     * @param featureName name of the force-disabled feature.
     */
    protected final void forceDisable(String featureName) {
        forcedPropertyMap.put(featureName, Boolean.FALSE.toString());
    }

    /**
     * Programmatically set a value of a property with a given name.
     * The property value may be overridden via a system property.
     *
     * @param propertyName name of the property.
     * @param value        property value.
     */
    protected final void set(String propertyName, Object value) {
        set(propertyName, value.toString());
    }

    /**
     * Programmatically set a value of a property with a given name.
     * The property value may be overridden via a system property.
     *
     * @param propertyName name of the property.
     * @param value        property value.
     */
    protected final void set(String propertyName, String value) {
        propertyMap.put(propertyName, value);
    }

    /**
     * Programmatically force-set a value of a property with a given name.
     * The force-set property value cannot be overridden via a system property.
     *
     * @param propertyName name of the property.
     * @param value        property value.
     */
    protected final void forceSet(String propertyName, String value) {
        forcedPropertyMap.put(propertyName, value);
    }

    /**
     * Check if the Jersey test boolean property (flag) has been set to {@code true}.
     *
     * @param propertyName name of the Jersey test boolean property.
     * @return {@code true} if the test property has been enabled, {@code false} otherwise.
     */
    protected final boolean isEnabled(String propertyName) {
        return Boolean.valueOf(getProperty(propertyName));
    }

    private String getProperty(String propertyName) {
        if (forcedPropertyMap.containsKey(propertyName)) {
            return forcedPropertyMap.get(propertyName);
        }

        final Properties systemProperties = AccessController.doPrivileged(PropertiesHelper.getSystemProperties());
        if (systemProperties.containsKey(propertyName)) {
            return systemProperties.getProperty(propertyName);
        }

        if (propertyMap.containsKey(propertyName)) {
            return propertyMap.get(propertyName);
        }

        return null;
    }

    /**
     * Return an JAX-RS applicationHandler that defines how the applicationHandler in the
     * test container is configured.
     * If a constructor is utilized that does not supply an applicationHandler
     * descriptor then this method must be overridden to return an applicationHandler
     * descriptor, otherwise an {@link UnsupportedOperationException} exception
     * will be thrown.
     * If a constructor is utilized that does supply an applicationHandler descriptor
     * then this method does not require to be overridden and will not be
     * invoked.
     *
     * @return the applicationHandler descriptor.
     */
    protected Application configure() {
        throw new UnsupportedOperationException(
                "The configure method must be implemented by the extending class");
    }

    protected SpringTestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new SpringGrizzlyTestContainerFactory();
    }

    /**
     * Create a web resource whose URI refers to the base URI the Web
     * applicationHandler is deployed at.
     *
     * @return the created web resource
     */
    public WebTarget target() {
        return client().target(tc.getBaseUri());
    }

    /**
     * Create a web resource whose URI refers to the base URI the Web
     * applicationHandler is deployed at plus the path specified in the argument.
     *
     * This method is an equivalent of calling {@code target().path(path)}.
     *
     * @param path Relative path (from base URI) this target should point to.
     * @return the created web resource
     */
    public WebTarget target(String path) {
        return target().path(path);
    }

    /**
     * Get the client that is configured for this test.
     *
     * @return the configured client.
     */
    public Client client() {
        if (client == null) {
            client = getClient(tc);
        }
        return client;
    }

    /**
     * Set up the test by invoking {@link TestContainer#start() } on
     * the test container obtained from the test container factory.
     *
     * @throws Exception if an exception is thrown during setting up the test environment.
     */
    @Before
    public void setUp() throws Exception {
        if (isLogRecordingEnabled()) {
            loggedRuntimeRecords.clear();
            registerLogHandler();
        }

        tc.start();
    }

    /**
     * Tear down the test by invoking {@link TestContainer#stop() } on
     * the test container obtained from the test container factory.
     *
     * @throws Exception if an exception is thrown during tearing down the test environment.
     */
    @After
    public void tearDown() throws Exception {
        if (isLogRecordingEnabled()) {
            loggedRuntimeRecords.clear();
            unregisterLogHandler();
        }

        tc.stop();
    }

    private SpringTestContainer getContainer(SpringTestContainerFactory tcf) {
        return tcf.create(getBaseUri(), deploymentContext);
    }

    /**
     * Creates an instance of {@link Client}.
     *
     * Checks whether TestContainer provides ClientConfig instance and
     * if not, empty new {@link org.glassfish.jersey.client.ClientConfig} instance
     * will be used to create new client instance.
     *
     * This method is called exactly once when JerseyTest is created.
     *
     * @param tc instance of {@link TestContainer}
     * @return A Client instance.
     */
    protected Client getClient(SpringTestContainer tc) {
        ClientConfig cc = tc.getClientConfig();

        if (cc == null) {
            cc = new ClientConfig();
        }

        //check if logging is required
        if (isEnabled(TestProperties.LOG_TRAFFIC)) {
            cc.register(new LoggingFilter(LOGGER, isEnabled(TestProperties.DUMP_ENTITY)));
        }

        configureClient(cc);

        return ClientBuilder.newClient(cc);
    }

    /**
     * Can be overridden by subclasses to conveniently configure the client instance
     * used by the test.
     *
     * Default implementation of the method is "no-op".
     *
     * @param config Jersey test client configuration that can be modified before the client is created.
     */
    protected void configureClient(ClientConfig config) {
        // nothing
    }

    /**
     * Returns the base URI of the applicationHandler.
     *
     * @return The base URI of the applicationHandler
     */
    protected URI getBaseUri() {
        return UriBuilder.fromUri("http://localhost/").port(getPort()).build();
    }

    /**
     * Get the port to be used for test applicationHandler deployments.
     *
     * @return The HTTP port of the URI
     */
    protected final int getPort() {
        final String value = AccessController.doPrivileged(PropertiesHelper.getSystemProperty(TestProperties.CONTAINER_PORT));
        if (value != null) {

            try {
                final int i = Integer.parseInt(value);
                if (i <= 0) {
                    throw new NumberFormatException("Value not positive.");
                }
                return i;
            } catch (NumberFormatException e) {
                LOGGER.log(Level.CONFIG,
                        "Value of " + TestProperties.CONTAINER_PORT
                                + " property is not a valid positive integer [" + value + "]."
                                + " Reverting to default [" + TestProperties.DEFAULT_CONTAINER_PORT + "].",
                        e
                );
            }
        }
        return TestProperties.DEFAULT_CONTAINER_PORT;
    }

    /**
     * Get stored {@link LogRecord log records} if enabled by setting {@link TestProperties#RECORD_LOG_LEVEL} or an empty list.
     *
     * @return list of log records or an empty list.
     */
    protected List<LogRecord> getLoggedRecords() {
        final List<LogRecord> logRecords = Lists.newArrayList();
        logRecords.addAll(loggedStartupRecords);
        logRecords.addAll(loggedRuntimeRecords);
        return logRecords;
    }

    /**
     * Get last stored {@link LogRecord log record} if enabled by setting {@link TestProperties#RECORD_LOG_LEVEL} or {@code null}.
     *
     * @return last stored {@link LogRecord log record} or {@code null}.
     */
    protected LogRecord getLastLoggedRecord() {
        final List<LogRecord> loggedRecords = getLoggedRecords();
        return loggedRecords.isEmpty() ? null : loggedRecords.get(loggedRecords.size() - 1);
    }

    /**
     * Retrieves a list of root loggers.
     *
     * @return list of root loggers.
     */
    private Set<Logger> getRootLoggers() {
        final LogManager logManager = LogManager.getLogManager();
        final Enumeration<String> loggerNames = logManager.getLoggerNames();

        final Set<Logger> rootLoggers = Sets.newHashSet();

        while (loggerNames.hasMoreElements()) {
            Logger logger = logManager.getLogger(loggerNames.nextElement());
            if (logger != null) {
                while (logger.getParent() != null) {
                    logger = logger.getParent();
                }
                rootLoggers.add(logger);
            }
        }

        return rootLoggers;
    }

    /**
     * Register {@link Handler log handler} to the list of root loggers.
     */
    private void registerLogHandler() {
        final String recordLogLevel = getProperty(TestProperties.RECORD_LOG_LEVEL);
        final int recordLogLevelInt = Integer.valueOf(recordLogLevel);
        final Level level = Level.parse(recordLogLevel);

        logLevelMap.clear();

        for (final Logger root : getRootLoggers()) {
            logLevelMap.put(root, root.getLevel());

            if (root.getLevel().intValue() > recordLogLevelInt) {
                root.setLevel(level);
            }

            root.addHandler(getLogHandler());
        }
    }

    /**
     * Un-register {@link Handler log handler} from the list of root loggers.
     */
    private void unregisterLogHandler() {
        for (final Logger root : getRootLoggers()) {
            root.setLevel(logLevelMap.get(root));
            root.removeHandler(getLogHandler());
        }
        logHandler = null;
    }

    /**
     * Return {@code true} if log recoding is enabled.
     *
     * @return {@code true} if log recoding is enabled, {@code false} otherwise.
     */
    private boolean isLogRecordingEnabled() {
        return getProperty(TestProperties.RECORD_LOG_LEVEL) != null;
    }

    /**
     * Retrieves {@link Handler log handler} capable of storing {@link LogRecord logged records}.
     *
     * @return log handler.
     */
    private Handler getLogHandler() {
        if (logHandler == null) {
            final Integer logLevel = Integer.valueOf(getProperty(TestProperties.RECORD_LOG_LEVEL));
            logHandler = new Handler() {

                @Override
                public void publish(LogRecord record) {
                    final String loggerName = record.getLoggerName();

                    if (record.getLevel().intValue() >= logLevel
                            && loggerName.startsWith("org.glassfish.jersey")
                            && !loggerName.startsWith("org.glassfish.jersey.test")) {
                        loggedRuntimeRecords.add(record);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() throws SecurityException {
                }
            };
        }
        return logHandler;
    }

    protected ApplicationContext getSpringApplicationContext() {
        return tc.getApplicationContext();
    }
}

