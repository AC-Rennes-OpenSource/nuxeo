/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Gagnavarslan ehf
 *     Vitalii Siryi
 */
package org.nuxeo.ecm.webdav.backend;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;

public class SearchRootBackend extends SearchVirtualBackend {

    private static final String QUERY = "select * from Workspace where ecm:mixinType != 'HiddenInNavigation' "
            + "AND  ecm:currentLifeCycleState != 'deleted' AND ecm:isProxy = 0 order by ecm:path";

    public SearchRootBackend(CoreSession session) {
        super("", "", QUERY, session, new SimpleRealBackendFactory());
    }

    @Override
    public Backend getBackend(String uri) {
        if (StringUtils.isEmpty(uri) || "/".equals(uri)) {
            return this;
        } else {
            return super.getBackend(uri);
        }

    }

    @Override
    public boolean isRoot() {
        return true;
    }
}
