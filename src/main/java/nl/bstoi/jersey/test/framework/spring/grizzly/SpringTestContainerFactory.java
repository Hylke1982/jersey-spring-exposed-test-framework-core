package nl.bstoi.jersey.test.framework.spring.grizzly;

import org.glassfish.jersey.test.DeploymentContext;

import java.net.URI;

public interface SpringTestContainerFactory {

    /**
     * Create a spring test container instance.
     *
     * @param baseUri           base URI for the test container to run at.
     * @param deploymentContext deployment context of the tested JAX-RS / Jersey application .
     * @return new test container configured to run the tested application.
     * @throws IllegalArgumentException if {@code deploymentContext} is not supported
     *                                  by this test container factory.
     */
    SpringTestContainer create(URI baseUri, DeploymentContext deploymentContext);
}
