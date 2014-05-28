package nl.bstoi.jersey.test.framework.spring.grizzly2.httpserver;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.internal.PropertiesDelegate;

import java.util.Collection;

/**
 * Hylke Stapersma (codecentric nl)
 * hylke.stapersma@codecentric.nl
 */
public class SpringGrizzlyRequestPropertiesDelegate implements PropertiesDelegate {
    private final Request request;

    /**
     * Create new Grizzly container properties delegate instance.
     *
     * @param request grizzly HTTP request.
     */
    SpringGrizzlyRequestPropertiesDelegate(Request request) {
        this.request = request;
    }

    @Override
    public Object getProperty(String name) {
        return request.getAttribute(name);
    }

    @Override
    public Collection<String> getPropertyNames() {
        return request.getAttributeNames();
    }

    @Override
    public void setProperty(String name, Object value) {
        request.setAttribute(name, value);
    }

    @Override
    public void removeProperty(String name) {
        request.removeAttribute(name);
    }
}
