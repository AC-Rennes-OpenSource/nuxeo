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
package org.nuxeo.ecm.platform.routing.test;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.content.template.service.ContentTemplateService;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.core.api.DocumentRoutingEngineService;
import org.nuxeo.ecm.platform.routing.core.api.DocumentRoutingPersistenceService;
import org.nuxeo.runtime.api.Framework;

/**
 * @author arussel
 *
 */
public class DocumentRoutingTestCase extends SQLRepositoryTestCase {
    protected DocumentRoutingPersistenceService persistenceService;

    protected DocumentRoutingEngineService engineService;

    protected DocumentRoutingService service;

    static final String ROUTE1 = "route1";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // deploy and test content template
        deployBundle("org.nuxeo.ecm.platform.content.template");
        deployBundle(TestConstants.CORE_BUNDLE);
        openSession();
        DocumentModel root = session.getRootDocument();
        ContentTemplateService ctService = Framework.getService(ContentTemplateService.class);
        ctService.executeFactoryForType(root);
        assertEquals(
                3,
                session.getChildren(
                        session.getChildren(root.getRef()).get(0).getRef()).size());
        // test our services
        persistenceService = Framework.getService(DocumentRoutingPersistenceService.class);
        engineService = Framework.getService(DocumentRoutingEngineService.class);
        service = Framework.getService(DocumentRoutingService.class);
    }

    public void testServices() throws Exception {
        assertNotNull(persistenceService);
        assertNotNull(engineService);
        assertNotNull(service);
    }

    public DocumentModel createDocumentRouteModel(CoreSession session,
            String name) throws ClientException {
        return createDocumentModel(session, name,
                DocumentRoutingConstants.DOCUMENT_ROUTE_DOCUMENT_TYPE);
    }

    public DocumentModel createDocumentModel(CoreSession session, String name,
            String type) throws ClientException {
        DocumentModel route1 = session.createDocumentModel("/", name, type);
        route1.setPropertyValue(DocumentRoutingConstants.TITLE_PROPERTY_NAME,
                ROUTE1);
        return session.createDocument(route1);

    }

    public DocumentRoute createDocumentRoute(CoreSession session, String name)
            throws ClientException {
        DocumentModel model = createDocumentRouteModel(session, name);
        return model.getAdapter(DocumentRoute.class);
    }

    protected DocumentModel createTestDocument(String name, CoreSession session)
            throws ClientException {
        return createDocumentModel(session, name, "Note");
    }
}
