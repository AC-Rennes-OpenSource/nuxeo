/*
 * (C) Copyright 2010-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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

package org.nuxeo.ecm.platform.routing.dm.operation;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteStep;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.dm.adapter.TaskStep;
import org.nuxeo.ecm.platform.routing.dm.api.RoutingTaskConstants;
import org.nuxeo.ecm.platform.routing.dm.task.RoutingTaskService;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskEventNames;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * Creates a routing task
 * 
 * @author ldoguin
 * @since 5.6
 */
@Operation(id = CreateRoutingTask.ID, category = Constants.CAT_SERVICES, label = "Create task", since = "5.6", description = "Enable to create a routingTask bound to a route and its document. "
        + "In <b>accept operation chain</b> and <b>reject operation chain</b> fields, "
        + "you can put the operation chain ID of your choice among the one you contributed. "
        + "Those operations will be executed when the user validates the task, "
        + "depending on  whether he accepts or rejects the task. "
        + "Extra (String) properties can be set on the taskVariables from the input document or from the step.")
public class CreateRoutingTask {

    public static final String ID = "Workflow.CreateRoutingTask";

    private static final Log log = LogFactory.getLog(CreateRoutingTask.class);

    public enum OperationTaskVariableName {
        acceptOperationChain, rejectOperationChain, createdFromCreateTaskOperation, taskDocuments
    }

    public static final String STEP_PREFIX = "StepTask:";

    public static final String DOCUMENT_PREFIX = "Document:";

    @Context
    protected OperationContext ctx;

    @Context
    protected CoreSession coreSession;

    @Context
    protected RoutingTaskService routingTaskService;

    @Param(name = "accept operation chain", required = false, order = 4)
    protected String acceptOperationChain;

    @Param(name = "reject operation chain", required = false, order = 5)
    protected String rejectOperationChain;

    @Param(name = "mappingTaskVariables", required = false)
    protected Properties mappingTaskVariables;

    @Param(name = "mappingProperties", required = false)
    protected Properties mappingProperties;

    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel createTask(DocumentModel document) throws Exception {
        Principal pal = coreSession.getPrincipal();
        if (!(pal instanceof NuxeoPrincipal)) {
            throw new OperationException(
                    "Principal is not an instance of NuxeoPrincipal");
        }

        DocumentRouteStep step = (DocumentRouteStep) ctx.get(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY);
        DocumentModel stepDocument = step.getDocument();
        TaskStep taskStep = stepDocument.getAdapter(TaskStep.class);
        List<String> actors = taskStep.getActors();

        if (actors.isEmpty()) {
            // no actors: do nothing
            log.debug("No actors could be resolved => do not create any task");
            return document;
        }

        // create the task, passing operation chains in task variables
        Map<String, String> taskVariables = new HashMap<String, String>();
        taskVariables.put(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY,
                step.getDocument().getId());
        taskVariables.put(
                OperationTaskVariableName.createdFromCreateTaskOperation.name(),
                "true");
        if (!StringUtils.isEmpty(acceptOperationChain)) {
            taskVariables.put(
                    OperationTaskVariableName.acceptOperationChain.name(),
                    acceptOperationChain);
        }
        if (!StringUtils.isEmpty(rejectOperationChain)) {
            taskVariables.put(
                    OperationTaskVariableName.rejectOperationChain.name(),
                    rejectOperationChain);
        }

        // disable notification service
        taskVariables.put(TaskEventNames.DISABLE_NOTIFICATION_SERVICE, "true");

        if (routingTaskService == null) {
            throw new OperationException("Service routingTaskService not found");
        }
        if (mappingTaskVariables != null) {
            mapPropertiesToTaskVariables(taskVariables, stepDocument, document,
                    mappingTaskVariables);
        }
        // TODO: call method with number of comments after NXP-8068 is merged
        List<Task> tasks = routingTaskService.createRoutingTask(coreSession,
                (NuxeoPrincipal) pal, document, taskStep.getName(), actors,
                false, taskStep.getDirective(), null, taskStep.getDueDate(),
                taskVariables, null);
        DocumentModelList docList = new DocumentModelListImpl(tasks.size());
        for (Task task : tasks) {
            docList.add(((mappingProperties == null) ? (task.getDocument())
                    : mapPropertiesToTaskDocument(coreSession, stepDocument,
                            task.getDocument(), document, mappingProperties)));
        }

        // all the actors should be able to validate the step creating the task
        for (String actor : actors) {
            step.setCanReadStep(coreSession, actor);
            step.setCanValidateStep(coreSession, actor);
            step.setCanUpdateStep(coreSession, actor);
        }
        ctx.put(OperationTaskVariableName.taskDocuments.name(), docList);
        ctx.put(RoutingTaskConstants.ROUTING_TASK_ACTORS_KEY, new StringList(
                actors));
        return document;
    }

    protected void mapPropertiesToTaskVariables(
            Map<String, String> taskVariables, DocumentModel stepDoc,
            DocumentModel inputDoc, Properties mappingProperties)
            throws ClientException {
        for (Map.Entry<String, String> prop : mappingProperties.entrySet()) {
            String getter = prop.getKey();
            String setter = prop.getValue();
            DocumentModel setterDoc = null;
            if (setter.startsWith(DOCUMENT_PREFIX)) {
                setterDoc = inputDoc;
                setter = setter.substring(DOCUMENT_PREFIX.length());
            } else if (setter.startsWith(STEP_PREFIX)) {
                setterDoc = stepDoc;
                setter = setter.substring(STEP_PREFIX.length());
            }
            try {
                taskVariables.put(getter,
                        (String) setterDoc.getPropertyValue(setter));
            } catch (PropertyException e) {
                log.error(
                        "Could not map property on the task document in the taskVariables ",
                        e);
            }
        }
    }

    DocumentModel mapPropertiesToTaskDocument(CoreSession session,
            DocumentModel stepDoc, DocumentModel taskDoc,
            DocumentModel inputDoc, Properties mappingProperties)
            throws ClientException {
        for (Map.Entry<String, String> prop : mappingProperties.entrySet()) {
            String getter = prop.getKey();
            String setter = prop.getValue();
            DocumentModel setterDoc = null;
            if (setter.startsWith(DOCUMENT_PREFIX)) {
                setterDoc = inputDoc;
                setter = setter.substring(DOCUMENT_PREFIX.length());
            } else if (setter.startsWith(STEP_PREFIX)) {
                setterDoc = stepDoc;
                setter = setter.substring(STEP_PREFIX.length());
            }
            try {
                taskDoc.setPropertyValue(getter,
                        (String) setterDoc.getPropertyValue(setter));
            } catch (PropertyException e) {
                log.error(
                        "Could not map property on the task document in the taskVariables ",
                        e);
            }
        }
        return session.saveDocument(taskDoc);
    }
}