/*
 * (C) Copyright 2010-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Alexandre Russel
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.core.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteElement;

public abstract class AbstractRunner implements ElementRunner {

    protected List<DocumentRouteElement> getChildrenElement(CoreSession session, DocumentRouteElement element) {
        DocumentModelList children = session.getChildren(element.getDocument().getRef());
        List<DocumentRouteElement> elements = new ArrayList<DocumentRouteElement>();
        for (DocumentModel model : children) {
            elements.add(model.getAdapter(DocumentRouteElement.class));
        }
        return elements;
    }

    @Override
    public void run(CoreSession session, DocumentRouteElement element, Map<String, Serializable> map) {
        run(session, element);
    }

    @Override
    public void resume(CoreSession session, DocumentRouteElement element, String nodeId, String taskId,
            Map<String, Object> data, String status) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public void undo(CoreSession session, DocumentRouteElement element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel(CoreSession session, DocumentRouteElement element) {
        List<DocumentRouteElement> children = getChildrenElement(session, element);
        for (DocumentRouteElement child : children) {
            child.cancel(session);
        }
        element.setCanceled(session);
    }
}
