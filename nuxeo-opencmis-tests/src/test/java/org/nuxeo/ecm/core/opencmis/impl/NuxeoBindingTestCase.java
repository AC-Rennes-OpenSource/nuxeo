/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.opencmis.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.spi.CmisBinding;
import org.apache.chemistry.opencmis.server.impl.CallContextImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.opencmis.bindings.NuxeoCmisServiceFactory;
import org.nuxeo.ecm.core.opencmis.impl.client.NuxeoBinding;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoCmisService;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepositories;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepository;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;

public class NuxeoBindingTestCase {

    public static final String USERNAME = "test";

    public static final String PASSWORD = "test";

    public String repositoryId;

    public String rootFolderId;

    public CmisBinding binding;

    public static class NuxeoTestCase extends SQLRepositoryTestCase {
        public String getRepositoryId() {
            return REPOSITORY_NAME;
        }

        public CoreSession getSession() {
            return session;
        }
    }

    public NuxeoTestCase nuxeotc;

    public void setUp() throws Exception {
        nuxeotc = new NuxeoTestCase();
        nuxeotc.setUp();
        // QueryMaker registration
        nuxeotc.deployBundle("org.nuxeo.ecm.core.opencmis.impl");
        // MyDocType
        nuxeotc.deployBundle("org.nuxeo.ecm.core.opencmis.tests");
        nuxeotc.openSession();

        Map<String, String> params = new HashMap<String, String>();
        params.put(SessionParameter.BINDING_SPI_CLASS,
                SessionParameter.LOCAL_FACTORY);
        params.put(SessionParameter.LOCAL_FACTORY,
                NuxeoCmisServiceFactory.class.getName());

        // use manual local bindings to keep the session open

        repositoryId = nuxeotc.getRepositoryId();
        rootFolderId = nuxeotc.getSession().getRootDocument().getId();

        boolean objectInfoRequired = true; // for tests
        CallContextImpl context = new CallContextImpl(
                CallContext.BINDING_LOCAL, repositoryId, objectInfoRequired);
        context.put(CallContext.USERNAME, USERNAME);
        context.put(CallContext.PASSWORD, PASSWORD);
        NuxeoRepository repository = new NuxeoRepository(repositoryId,
                rootFolderId);
        NuxeoCmisService service = new NuxeoCmisService(repository, context,
                nuxeotc.getSession());
        binding = new NuxeoBinding(service);
    }

    public void tearDown() throws Exception {
        if (nuxeotc != null) {
            nuxeotc.closeSession();
            nuxeotc.tearDown();
        }
        NuxeoRepositories.clear();
    }

}
