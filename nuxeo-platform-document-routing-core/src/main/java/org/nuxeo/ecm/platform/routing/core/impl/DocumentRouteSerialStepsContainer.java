/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     arussel
 */
package org.nuxeo.ecm.platform.routing.core.impl;

import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteElement;

/**
 * @author arussel
 *
 */
public class DocumentRouteSerialStepsContainer extends
        DocumentRouteStepsContainerImpl {

    public DocumentRouteSerialStepsContainer(DocumentModel doc) {
        super(doc);
    }

    @Override
    public boolean run(CoreSession session) {
        List<DocumentRouteElement> children = getChildrenElement(session);
        if (!isRunning()) {
            setRunning(session);
        }
        if (children.isEmpty()) {
            setDone(session);
            return false;
        }
        // run all the child unless there is a wait state
        for (DocumentRouteElement child : children) {
            if (!child.isDone()) {
                boolean isWaitState = child.run(session);
                if (isWaitState) {
                    return true;
                }
            }
        }
        // all child ran, we're done
        setDone(session);
        return false;
    }
}
