package org.nuxeo.dam.webapp;

import java.io.IOException;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants;
import org.nuxeo.ecm.platform.ui.web.util.BaseURL;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 */
@Name("logoutActions")
@Scope(ScopeType.STATELESS)
public class LogoutActions {

    /**
     * Logs the user out. Invalidates the HTTP session so that it cannot be used
     * anymore.
     *
     * @return the next page that is going to be displayed
     * @throws java.io.IOException
     */
    public static String logout() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext eContext = context.getExternalContext();
        Object req = eContext.getRequest();
        Object resp = eContext.getResponse();
        HttpServletRequest request = null;
        if (req instanceof HttpServletRequest) {
            request = (HttpServletRequest) req;
        }
        HttpServletResponse response = null;
        if (resp instanceof HttpServletResponse) {
            response = (HttpServletResponse) resp;
        }

        if (response != null && request != null
                && !context.getResponseComplete()) {
            String baseURL = BaseURL.getBaseURL(request);
            request.setAttribute(NXAuthConstants.DISABLE_REDIRECT_REQUEST_KEY,
                    true);
            response.sendRedirect(baseURL + NXAuthConstants.LOGOUT_PAGE);
            context.responseComplete();
        }
        return null;
    }

}
