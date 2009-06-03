/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.dam.platform.context;

import java.io.Serializable;
import java.security.Principal;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.jboss.seam.ScopeType.SESSION;
import org.jboss.seam.annotations.Begin;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.contexts.Context;
import org.jboss.seam.contexts.Contexts;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.platform.util.RepositoryLocation;
import org.nuxeo.ecm.webapp.delegate.DocumentManagerBusinessDelegate;
import org.nuxeo.ecm.webapp.context.ServerContextBean;

@Name("startupHelper")
@Scope(SESSION)
// TODO @Install(precedence=FRAMEWORK)
public class StartupHelper implements Serializable {

    private static final long serialVersionUID = 3248972387619873245L;

    protected static final Log log = LogFactory.getLog(StartupHelper.class);

    @In(create = true)
    protected transient RepositoryManager repositoryManager;

    @In
    protected transient Context sessionContext;

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In
    protected transient Context conversationContext;

    @In
    protected ServerContextBean serverLocator;

    /**
     * Initializes the context with the principal id, and tries to connect to
     * the default server if any then: - if the server has several domains,
     * redirect to the list of domains - if the server has only one domain,
     * select it and redirect to viewId - if the server is empty, create a new
     * domain with title 'domainTitle' and redirect to it on viewId.
     * <p>
     * If several servers are available, let the user choose.
     * 
     * @return the view id of the contextually computed startup page
     * @throws ClientException
     */
    @Begin(id = "#{conversationIdGenerator.nextMainConversationId}", join = true)
    public String initDomainAndFindStartupPage() throws ClientException {

        setupCurrentUser();

        RepositoryLocation repLoc = new RepositoryLocation(
                repositoryManager.getRepositories().iterator().next().getName());
        serverLocator.setRepositoryLocation(repLoc);

        if (documentManager == null) {
            documentManager = getOrCreateDocumentManager();
        }

        // TODO : REMOVE
        return "view_documents";
    }

    public void setupCurrentUser() {
        Principal currentUser = FacesContext.getCurrentInstance().getExternalContext().getUserPrincipal();
        sessionContext.set("currentUser", currentUser);
    }

    /**
     * Returns the current documentManager if any or create a new session to the
     * current location.
     */
    public CoreSession getOrCreateDocumentManager() throws ClientException {

        if (documentManager != null) {
            return documentManager;
        }

        DocumentManagerBusinessDelegate documentManagerBD = (DocumentManagerBusinessDelegate) Contexts.lookupInStatefulContexts("documentManager");

        if (documentManagerBD == null) {
            // this is the first time we select the location, create a
            // DocumentManagerBusinessDelegate instance
            documentManagerBD = new DocumentManagerBusinessDelegate();
            conversationContext.set("documentManager", documentManagerBD);
        }
        RepositoryLocation repLoc = new RepositoryLocation(
                repositoryManager.getRepositories().iterator().next().getName());

        documentManager = documentManagerBD.getDocumentManager(repLoc);
        return documentManager;
    }

}
