/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.platform.routing.api.helper;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.routing.api.ActionableObject;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * An actionable validator allows to run an {@link ActionableObject}.
 *
 * @deprecated since 5.9.2 - Use only routes of type 'graph'
 * @author <a href="mailto:arussel@nuxeo.com">Alexandre Russel</a>
 */
@Deprecated
public class ActionableValidator {

    protected ActionableObject actionnable;

    protected CoreSession session;

    protected Map<String, Serializable> additionalProperties = new HashMap<String, Serializable>();

    public ActionableValidator(ActionableObject actionnable, CoreSession session) {
        this.actionnable = actionnable;
        this.session = session;
    }

    public ActionableValidator(ActionableObject actionnable, CoreSession session,
            Map<String, Serializable> additionalProperties) {
        this.actionnable = actionnable;
        this.session = session;
        this.additionalProperties = additionalProperties;
    }

    public void validate() {
        String chainId = actionnable.getValidateOperationChainId();
        runChain(chainId);
    }

    public void refuse() {
        String chainId = actionnable.getRefuseOperationChainId();
        runChain(chainId);
    }

    protected void runChain(String chainId) {
        AutomationService automationService = getAutomationService();
        OperationContext context = new OperationContext(session);
        context.put(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY, actionnable.getDocumentRouteStep(session));
        context.setInput(actionnable.getAttachedDocuments(session));
        context.putAll(additionalProperties);
        try {
            automationService.run(context, chainId);
        } catch (OperationException e) {
            throw new NuxeoException(e);
        }
    }

    protected AutomationService getAutomationService() {
        return Framework.getService(AutomationService.class);
    }

}
