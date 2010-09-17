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
package org.nuxeo.ecm.core.opencmis.bindings;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.exceptions.CmisConnectionException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.impl.server.AbstractServiceFactory;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.server.CmisService;
import org.apache.chemistry.opencmis.server.support.CmisServiceWrapper;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoCmisService;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepositories;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepository;

/**
 * Factory for a wrapped {@link NuxeoCmisService}.
 * <p>
 * Called for each method dispatch by
 * {@link org.apache.chemistry.opencmis.server.impl.atompub.CmisAtomPubServlet}
 * or
 * {@link org.apache.chemistry.opencmis.server.impl.webservices.AbstractService}.
 */
public class NuxeoCmisServiceFactory extends AbstractServiceFactory {

    public static final BigInteger DEFAULT_TYPES_MAX_ITEMS = BigInteger.valueOf(100);

    public static final BigInteger DEFAULT_TYPES_DEPTH = BigInteger.valueOf(-1);

    public static final BigInteger DEFAULT_MAX_ITEMS = BigInteger.valueOf(100);

    public static final BigInteger DEFAULT_DEPTH = BigInteger.valueOf(2);

    protected Map<String, NuxeoRepository> repositories;

    @Override
    public void init(Map<String, String> parameters) {
        repositories = Collections.synchronizedMap(new HashMap<String, NuxeoRepository>());
    }

    @Override
    public void destroy() {
        repositories = null;
    }

    @Override
    public CmisService getService(CallContext context) {
        String repositoryId = context.getRepositoryId();
        NuxeoRepository repository = NuxeoRepositories.getRepository(repositoryId);
        NuxeoCmisService service = new NuxeoCmisService(repository, context);

        // wrap the service to provide default parameter checks
        return new CmisServiceWrapper<NuxeoCmisService>(service,
                DEFAULT_TYPES_MAX_ITEMS, DEFAULT_TYPES_DEPTH,
                DEFAULT_MAX_ITEMS, DEFAULT_DEPTH);
    }

}
