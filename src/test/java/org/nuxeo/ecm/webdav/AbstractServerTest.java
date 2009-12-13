/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.webdav;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

public abstract class AbstractServerTest extends Assert {

    public static final int PORT = 9999;
    public static final int BT_PORT = 9997;

    public static final int port;
    public static final String TEST_URI;

    static {
        if (System.getenv("BT") == null) {
            port = PORT;
        } else {
            port = BT_PORT;
        }
        TEST_URI = "http://localhost:" + port;
    }

    static final String ROOT_URI = TEST_URI + "/dav/workspaces/";

    @BeforeClass
    public static void startServer() throws Exception {
        Server.startRuntime();
        Server.startServer(port);
    }

    @AfterClass
    public static void stopServer() {
        Server.stopServer();
    }

}
