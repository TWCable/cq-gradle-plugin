package testpkg2;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

@SlingServlet(
    methods = {"GET", "POST"},
    paths = {"/bin/buyflow/epc/addoffer"}
)
@Properties({
    @Property(name = "service.vendor", value = "Time Warner Cable", propertyPrivate = false),
    @Property(name = "service.description", value = "Call to add offers to present to user's shopping cart session", propertyPrivate = false)
})
public class SimpleServlet implements Servlet {
    @Reference
    private SimpleService simpleService;


    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public ServletConfig getServletConfig() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public String getServletInfo() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public void destroy() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}
