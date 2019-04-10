/*
 * (C) Copyright 2006-2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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

import org.nuxeo.apidoc.api.BundleGroup;
import org.nuxeo.apidoc.api.BundleGroupFlatTree;
import org.nuxeo.apidoc.api.BundleGroupTreeHelper;
import org.nuxeo.apidoc.api.NuxeoArtifact;
import org.nuxeo.ecm.webengine.model.WebObject;

@WebObject(type = "bundleGroup")
public class BundleGroupWO extends NuxeoArtifactWebObject {

    @Override
    @GET
    @Produces("text/html")
    @Path(value = "introspection")
    public Object doGet() throws Exception {
        BundleGroup group = getTargetBundleGroup();
        BundleGroupTreeHelper bgth = new BundleGroupTreeHelper(
                getSnapshotManager().getSnapshot(getDistributionId(),
                        ctx.getCoreSession()));
        List<BundleGroupFlatTree> tree = bgth.getBundleGroupSubTree(nxArtifactId);
        return getView("view").arg("group", group).arg("groupId", nxArtifactId).arg(
                "tree", tree);
    }

    public BundleGroup getTargetBundleGroup() {
        return getSnapshotManager().getSnapshot(getDistributionId(),
                ctx.getCoreSession()).getBundleGroup(nxArtifactId);
    }

    @Override
    public NuxeoArtifact getNxArtifact() {
        return getTargetBundleGroup();
    }

    /*
     * @GET
     *
     * @Produces("text/html") public Object doViewAggregated() throws Exception
     * { NuxeoArtifact nxItem = getNxArtifact(); AssociatedDocuments docs =
     * nxItem.getAssociatedDocuments(ctx.getCoreSession()); BundleGroup group =
     * getTargetBundleGroup(); return getView("aggregated").arg("nxItem",
     * nxItem).arg("docs",
     * docs).arg("selectedTab","aggView").arg("group",group); }
     */

    public List<BundleWO> getBundles() {
        List<BundleWO> result = new ArrayList<BundleWO>();

        BundleGroup group = getTargetBundleGroup();
        for (String bid : group.getBundleIds()) {
            result.add((BundleWO) ctx.newObject("bundle", bid));
        }
        return result;
    }

    public List<BundleGroupWO> getSubGroups() {
        List<BundleGroupWO> result = new ArrayList<BundleGroupWO>();

        BundleGroup group = getTargetBundleGroup();
        for (BundleGroup bg : group.getSubGroups()) {
            result.add((BundleGroupWO) ctx.newObject("bundleGroup", bg.getId()));
        }
        return result;
    }

}
