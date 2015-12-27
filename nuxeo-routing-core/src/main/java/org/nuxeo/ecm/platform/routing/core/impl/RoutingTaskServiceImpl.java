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
package org.nuxeo.ecm.platform.routing.core.impl;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.api.RoutingTaskService;
import org.nuxeo.ecm.platform.routing.api.exception.DocumentRouteException;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @deprecated since 5.6, use DocumentRoutingService instead
 */
@Deprecated
public class RoutingTaskServiceImpl extends DefaultComponent implements RoutingTaskService {

    @Override
    public void makeRoutingTasks(CoreSession session, List<Task> tasks) {
        DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        routing.makeRoutingTasks(session, tasks);
    }

    @Override
    public void endTask(CoreSession session, Task task, Map<String, Object> data, String status)
            throws DocumentRouteException {
        DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        routing.endTask(session, task, data, status);
    }

    @Override
    public List<DocumentModel> getWorkflowInputDocuments(CoreSession session, Task task) throws DocumentRouteException {
        DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        return routing.getWorkflowInputDocuments(session, task);
    }

}
