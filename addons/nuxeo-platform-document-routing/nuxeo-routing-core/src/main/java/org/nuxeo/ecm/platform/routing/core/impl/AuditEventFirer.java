/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 */
package org.nuxeo.ecm.platform.routing.core.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteElement;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * Fire events for Route audit logs (used by previous route content view)
 *
 * @since 5.6
 */
public class AuditEventFirer {

    static public void fireEvent(CoreSession coreSession, DocumentRouteElement element,
            Map<String, Serializable> eventProperties, String eventName, DocumentModel doc) {
        if (eventProperties == null) {
            eventProperties = new HashMap<String, Serializable>();
        }
        eventProperties.put(DocumentRoutingConstants.TASK_ROUTE_INSTANCE_DOCUMENT_ID_KEY,
                element.getDocumentRoute(coreSession).getDocument().getId());
        eventProperties.put(DocumentRoutingConstants.DOCUMENT_ELEMENT_EVENT_CONTEXT_KEY, element);
        eventProperties.put(DocumentEventContext.CATEGORY_PROPERTY_KEY, DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
        DocumentEventContext envContext = new DocumentEventContext(coreSession, coreSession.getPrincipal(), doc);
        envContext.setProperties(eventProperties);
        getEventProducer().fireEvent(envContext.newEvent(eventName));
    }

    static protected EventProducer getEventProducer() {
        return Framework.getService(EventProducer.class);
    }

}
