/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 */

package org.nuxeo.targetplatforms.jaxrs;

import java.io.IOException;

import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.DetectThreadDeadlocksFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features({ DetectThreadDeadlocksFeature.class, WebEngineFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Jetty(port = 18090)
@Deploy({ "org.nuxeo.targetplatforms.core", "org.nuxeo.targetplatforms.core.test", "org.nuxeo.targetplatforms.jaxrs" })
@LocalDeploy({ "org.nuxeo.targetplatforms.core:OSGI-INF/test-datasource-contrib.xml",
        "org.nuxeo.targetplatforms.core:OSGI-INF/test-targetplatforms-contrib.xml" })
public class TargetPlatformServiceTest {

    private static final int TIMEOUT = 2000;

    @Ignore("NXP-17108")
    @Test
    public void ping() throws IOException {
        WebResource resource = getServiceFor("Administrator", "Administrator");
        ClientResponse response = resource.path("/platforms").accept(APPLICATION_JSON).get(ClientResponse.class);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        String result = IOUtils.toString(response.getEntityInputStream());
        assertTrue(result.contains("nuxeo-dm-5.8"));
    }

    protected WebResource getServiceFor(String user, String password) {
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        client.setConnectTimeout(TIMEOUT);
        client.setReadTimeout(TIMEOUT);
        client.addFilter(new HTTPBasicAuthFilter(user, password));
        return client.resource("http://localhost:18090/target-platforms");
    }

}
