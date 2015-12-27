/*
 * (C) Copyright 2006-2010 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.apidoc.browse;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.apidoc.api.ExtensionInfo;
import org.nuxeo.apidoc.api.ExtensionPointInfo;
import org.nuxeo.apidoc.api.NuxeoArtifact;
import org.nuxeo.apidoc.api.ServiceInfo;
import org.nuxeo.ecm.webengine.model.WebObject;

@WebObject(type = "component")
public class ComponentWO extends NuxeoArtifactWebObject {

    @Override
    @GET
    @Produces("text/html")
    @Path("introspection")
    public Object doGet() {
        ComponentInfo ci = getTargetComponentInfo();
        String bundleId = ci.getBundle().getBundleId();
        return getView("view").arg("bundleId", bundleId).arg("component", ci);
    }

    public ComponentInfo getTargetComponentInfo() {
        return getSnapshotManager().getSnapshot(getDistributionId(), ctx.getCoreSession()).getComponent(nxArtifactId);
    }

    @Override
    public NuxeoArtifact getNxArtifact() {
        return getTargetComponentInfo();
    }

    public List<ServiceWO> getServices() {
        List<ServiceWO> result = new ArrayList<ServiceWO>();
        ComponentInfo ci = getTargetComponentInfo();
        for (ServiceInfo si : ci.getServices()) {
            result.add((ServiceWO) ctx.newObject("service", si.getId()));
        }
        return result;
    }

    public List<ExtensionPointWO> getExtensionPoints() {
        List<ExtensionPointWO> result = new ArrayList<ExtensionPointWO>();
        ComponentInfo ci = getTargetComponentInfo();
        for (ExtensionPointInfo ei : ci.getExtensionPoints()) {
            result.add((ExtensionPointWO) ctx.newObject("extensionPoint", ei.getId()));
        }
        return result;
    }

    public List<ContributionWO> getContributions() {
        List<ContributionWO> result = new ArrayList<ContributionWO>();
        ComponentInfo ci = getTargetComponentInfo();
        for (ExtensionInfo ei : ci.getExtensions()) {
            result.add((ContributionWO) ctx.newObject("contribution", ei.getId()));
        }
        return result;
    }

}
