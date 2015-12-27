/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     ldoguin
 */
package org.nuxeo.ecm.platform.routing.dm.adapter;

import org.nuxeo.ecm.automation.task.CreateTask.OperationTaskVariableName;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteStep;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.task.TaskImpl;

/**
 * @deprecated since 5.9.2 - Use only routes of type 'graph' The facet 'RoutingTask' is still used to mark tasks created
 *             by the workflow, but it this class is marked as deprecated as it extends the deprecated ActionableObject
 */
@Deprecated
public class RoutingTaskImpl extends TaskImpl implements RoutingTask {

    public RoutingTaskImpl(DocumentModel doc) {
        super(doc);
    }

    private static final long serialVersionUID = 1L;

    /**
     * @deprecated {@link Task#getTargetDocumentsIds() } should be used instead
     */
    @Deprecated
    @Override
    public DocumentModelList getAttachedDocuments(CoreSession coreSession) {
        DocumentRef stepIdRef = new IdRef(getTargetDocumentId());
        DocumentModel targetDocument = coreSession.getDocument(stepIdRef);
        DocumentModelList docList = new DocumentModelListImpl();
        docList.add(targetDocument);
        return docList;
    }

    @Override
    public DocumentRouteStep getDocumentRouteStep(CoreSession coreSession) {
        String docStepId = getVariable(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY);
        DocumentRef stepIdRef = new IdRef(docStepId);
        DocumentModel docStep = coreSession.getDocument(stepIdRef);
        return docStep.getAdapter(DocumentRouteStep.class);
    }

    @Override
    public String getRefuseOperationChainId() {
        return getVariable(OperationTaskVariableName.rejectOperationChain.name());
    }

    @Override
    public String getValidateOperationChainId() {
        return getVariable(OperationTaskVariableName.acceptOperationChain.name());
    }

}
