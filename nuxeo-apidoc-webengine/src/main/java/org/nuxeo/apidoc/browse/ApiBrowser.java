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
package org.nuxeo.apidoc.browse;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.apidoc.api.BundleGroup;
import org.nuxeo.apidoc.api.BundleGroupFlatTree;
import org.nuxeo.apidoc.api.BundleGroupTreeHelper;
import org.nuxeo.apidoc.api.BundleInfo;
import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.apidoc.api.DocumentationItem;
import org.nuxeo.apidoc.api.ExtensionInfo;
import org.nuxeo.apidoc.api.ExtensionPointInfo;
import org.nuxeo.apidoc.api.NuxeoArtifact;
import org.nuxeo.apidoc.api.SeamComponentInfo;
import org.nuxeo.apidoc.api.ServiceInfo;
import org.nuxeo.apidoc.documentation.DocumentationService;
import org.nuxeo.apidoc.search.ArtifactSearcher;
import org.nuxeo.apidoc.snapshot.DistributionSnapshot;
import org.nuxeo.apidoc.snapshot.SnapshotManager;
import org.nuxeo.apidoc.tree.TreeHelper;
import org.nuxeo.ecm.platform.rendering.wiki.WikiSerializer;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:td@nuxeo.com">Thierry Delprat</a>
 *
 */
@WebObject(type = "apibrowser")
public class ApiBrowser extends DefaultObject {

    String distributionId;
    boolean embeddedMode=false;

    protected SnapshotManager getSnapshotManager() {
        return Framework.getLocalService(SnapshotManager.class);
    }

    protected ArtifactSearcher getSearcher() {
        return Framework.getLocalService(ArtifactSearcher.class);
    }

    @Override
    protected void initialize(Object... args) {
        distributionId = (String) args[0];
        if (args.length>1) {
            embeddedMode = (Boolean) args[1];
        }
    }

    @GET
    @Produces("text/plain")
    @Path(value = "tree")
    public Object tree(@QueryParam("root") String source) {
        return TreeHelper.updateTree(getContext(), source);
    }

    @GET
    @Produces("text/html")
    @Path(value = "treeView")
    public Object treeView() {
        return getView("tree").arg("distId", ctx.getProperty("distId"));
    }

    @GET
    @Produces("text/html")
    public Object doGet() {
        if (embeddedMode) {
            DistributionSnapshot snap = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession());
            Map<String, Integer> stats = new HashMap<String, Integer>();
            stats.put("bundles", snap.getBundleIds().size());
            stats.put("jComponents", snap.getJavaComponentIds().size());
            stats.put("xComponents", snap.getXmlComponentIds().size());
            stats.put("services",snap.getServiceIds().size());
            stats.put("xps",snap.getExtensionPointIds().size());
            stats.put("contribs", snap.getComponentIds().size());
            return getView("indexSimple").arg("distId", ctx.getProperty("distId")).arg("stats", stats);
        } else {
            return getView("index").arg("distId", ctx.getProperty("distId"));
        }
    }

    @GET
    @Produces("text/html")
    @Path(value = "listBundleGroups")
    public Object getMavenGroups() {
        BundleGroupTreeHelper bgth = new BundleGroupTreeHelper(getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()));
        List<BundleGroupFlatTree> tree = bgth.getBundleGroupTree();
        return getView("listBundleGroups")
                .arg("tree", tree).arg("distId", ctx.getProperty("distId"));
    }
    public Map<String, DocumentationItem> getDescriptions(String targetType) throws Exception {
        DocumentationService ds = Framework.getLocalService(DocumentationService.class);
        return ds.getAvailableDescriptions(getContext().getCoreSession(), targetType);
    }

    @GET
    @Produces("text/html")
    @Path(value = "listBundles")
    public Object getBundles() {
        List<String> bundleIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getBundleIds();
        return getView("listBundles")
                .arg("bundleIds", bundleIds).arg("distId", ctx.getProperty("distId"));
    }

    @GET
    @Produces("text/html")
    @Path(value = "filterBundles")
    public Object filterBundles() throws Exception {
        String fulltext = getContext().getForm().getFormProperty("fulltext");
        List<NuxeoArtifact> artifacts = getSearcher().filterArtifact(getContext().getCoreSession(), distributionId, BundleInfo.TYPE_NAME, fulltext);
        List<String> bundleIds = new ArrayList<String>();
        for (NuxeoArtifact item : artifacts) {
            bundleIds.add(item.getId());
        }
        return getView("listBundles")
                .arg("bundleIds", bundleIds).arg("distId", ctx.getProperty("distId")).arg("searchFilter", fulltext);
    }

    @GET
    @Produces("text/html")
    @Path(value = "listComponents")
    public Object getComponents() {
        List<String> javaComponentIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getJavaComponentIds();
        List<ArtifactLabel> javaLabels = new ArrayList<ArtifactLabel>();
        for (String id : javaComponentIds) {
            javaLabels.add(ArtifactLabel.createLabelFromComponent(id));
        }

        List<String> xmlComponentIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getXmlComponentIds();
        List<ArtifactLabel> xmlLabels = new ArrayList<ArtifactLabel>();
        for (String id : xmlComponentIds) {
            xmlLabels.add(ArtifactLabel.createLabelFromComponent(id));
        }

        Collections.sort(javaLabels);
        Collections.sort(xmlLabels);

        return getView("listComponents")
                .arg("javaComponents", javaLabels).arg("xmlComponents", xmlLabels).arg("distId", ctx.getProperty("distId"));
    }

    @GET
    @Produces("text/html")
    @Path(value = "filterComponents")
    public Object filterComponents() throws Exception {
        String fulltext = getContext().getForm().getFormProperty("fulltext");
        List<NuxeoArtifact> artifacts = getSearcher().filterArtifact(getContext().getCoreSession(), distributionId, ComponentInfo.TYPE_NAME, fulltext);

        List<ArtifactLabel> xmlLabels = new ArrayList<ArtifactLabel>();
        List<ArtifactLabel> javaLabels = new ArrayList<ArtifactLabel>();

        for (NuxeoArtifact item : artifacts) {
            ComponentInfo ci = (ComponentInfo) item;
            if (ci.isXmlPureComponent()) {
                xmlLabels.add(ArtifactLabel.createLabelFromComponent(ci.getId()));
            } else {
                javaLabels.add(ArtifactLabel.createLabelFromComponent(ci.getId()));
            }
        }
        return getView("listComponents")
                .arg("javaComponents", javaLabels).arg("xmlComponents", xmlLabels).arg("distId", ctx.getProperty("distId")).arg("searchFilter", fulltext);
    }


    @GET
    @Produces("text/html")
    @Path(value = "listServices")
    public Object getServices() {
        List<String> serviceIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getServiceIds();

        List<ArtifactLabel> serviceLabels = new ArrayList<ArtifactLabel>();

        for (String id : serviceIds) {
            serviceLabels.add(ArtifactLabel.createLabelFromService(id));
        }
        Collections.sort(serviceLabels);

        return getView("listServices")
                .arg("services", serviceLabels).arg("distId", ctx.getProperty("distId"));
    }

    protected Map<String, String> getRenderedDescriptions(String type) throws Exception {

         Map<String, DocumentationItem> descs = getDescriptions(type);
         Map<String, String> result = new HashMap<String, String>();

         for (String key : descs.keySet()) {
             DocumentationItem docItem = descs.get(key);
             String content = docItem.getContent();
             if ("wiki".equals(docItem.getRenderingType())) {
                 Reader reader = new StringReader(content);
                 WikiSerializer engine = new WikiSerializer();
                 StringWriter writer = new StringWriter();
                 engine.serialize(reader, writer);
                 content = writer.getBuffer().toString();
             } else {
                 content="<div class='doc'>" + content + "</div>";
             }
             result.put(key, content);
         }
        return result;
    }


    @GET
    @Produces("text/plain")
    @Path(value = "feedServices")
    public String feedServices() throws Exception {
        List<String> serviceIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getServiceIds();

        Map<String, String> descs = getRenderedDescriptions("NXService");

        List<ArtifactLabel> serviceLabels = new ArrayList<ArtifactLabel>();

        for (String id : serviceIds) {
            serviceLabels.add(ArtifactLabel.createLabelFromService(id));
        }
        Collections.sort(serviceLabels);

        JSONArray array = new JSONArray();

        for (ArtifactLabel label : serviceLabels) {
            JSONObject object = new JSONObject();
            object.put("id", label.getId());
            object.put("label", label.getLabel());
            object.put("desc", descs.get(label.id));
            object.put("url", "http://explorer.nuxeo.org/nuxeo/site/distribution/current/service2Bundle/" + label.id);
            array.put(object);
        }

        return array.toString();
    }

    @GET
    @Produces("text/plain")
    @Path(value = "feedExtensionPoints")
    public String feedExtensionPoints() throws Exception {
        List<String> epIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getExtensionPointIds();

        Map<String, String> descs = getRenderedDescriptions("NXExtensionPoint");

        List<ArtifactLabel> labels = new ArrayList<ArtifactLabel>();

        for (String id : epIds) {
            labels.add(ArtifactLabel.createLabelFromExtensionPoint(id));
        }
        Collections.sort(labels);

        JSONArray array = new JSONArray();

        for (ArtifactLabel label : labels) {
            JSONObject object = new JSONObject();
            object.put("id", label.getId());
            object.put("label", label.getLabel());
            object.put("desc", descs.get(label.id));
            object.put("url", "http://explorer.nuxeo.org/nuxeo/site/distribution/current/extensionPoint2Component/" + label.id);
            array.put(object);
        }

        return array.toString();
    }


    @GET
    @Produces("text/html")
    @Path(value = "filterServices")
    public Object filterServices() throws Exception {
        String fulltext = getContext().getForm().getFormProperty("fulltext");
        List<NuxeoArtifact> artifacts = getSearcher().filterArtifact(getContext().getCoreSession(), distributionId, ServiceInfo.TYPE_NAME, fulltext);
        List<String> serviceIds = new ArrayList<String>();
        for (NuxeoArtifact item : artifacts) {
            serviceIds.add(item.getId());
        }
        List<ArtifactLabel> serviceLabels = new ArrayList<ArtifactLabel>();

        for (String id : serviceIds) {
            serviceLabels.add(ArtifactLabel.createLabelFromService(id));
        }
        return getView("listServices")
                .arg("services", serviceLabels).arg("distId", ctx.getProperty("distId")).arg("searchFilter", fulltext);
    }

    @GET
    @Produces("text/html")
    @Path(value = "listExtensionPointsSimple")
    public Object getExtensionPointsSimple() {
        List<String> epIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getExtensionPointIds();

        Map<String, Integer> epSimpleIds = new HashMap<String, Integer>();

        List<ArtifactLabel> labels = new ArrayList<ArtifactLabel>();
        for (String id : epIds) {
            ArtifactLabel label =ArtifactLabel.createLabelFromExtensionPoint(id);
            labels.add(label);
            Integer count = epSimpleIds.get(label.simpleId);
            if (count==null) {
                count=1;
            } else {
                count=count+1;
            }
            epSimpleIds.put(label.simpleId, count);
        }

        for (ArtifactLabel label : labels) {
            if (epSimpleIds.get(label.simpleId)==1) {
                label.label=label.simpleId;
            }
        }

        Collections.sort(labels);
        return getView("listExtensionPointsSimple")
                .arg("eps", labels).arg("distId", ctx.getProperty("distId")).arg("hideNav", true);
    }

    @GET
    @Produces("text/html")
    @Path(value = "listExtensionPoints")
    public Object getExtensionPoints() {
        List<String> epIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getExtensionPointIds();

        List<ArtifactLabel> labels = new ArrayList<ArtifactLabel>();
        for (String id : epIds) {
            labels.add(ArtifactLabel.createLabelFromExtensionPoint(id));
        }

        Collections.sort(labels);
        return getView("listExtensionPoints")
                .arg("eps", labels).arg("distId", ctx.getProperty("distId"));
    }

    @GET
    @Produces("text/html")
    @Path(value = "filterExtensionPoints")
    public Object filterExtensionPoints() throws Exception {
        String fulltext = getContext().getForm().getFormProperty("fulltext");
        List<NuxeoArtifact> artifacts = getSearcher().filterArtifact(getContext().getCoreSession(), distributionId, ExtensionPointInfo.TYPE_NAME, fulltext);
        List<String> eps = new ArrayList<String>();
        for (NuxeoArtifact item : artifacts) {
            eps.add(item.getId());
        }
        List<ArtifactLabel> labels = new ArrayList<ArtifactLabel>();
        for (String id : eps) {
            labels.add(ArtifactLabel.createLabelFromExtensionPoint(id));
        }
        return getView("listExtensionPoints")
                .arg("eps",labels).arg("distId", ctx.getProperty("distId")).arg("searchFilter", fulltext);
    }


    @GET
    @Produces("text/html")
    @Path(value = "listContributions")
    public Object getContributions() {
        List<String> cIds = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getContributionIds();
        return getView("listContributions")
                .arg("cIds", cIds).arg("distId", ctx.getProperty("distId"));
    }

    @GET
    @Produces("text/html")
    @Path(value = "filterContributions")
    public Object filterContributions() throws Exception {
        String fulltext = getContext().getForm().getFormProperty("fulltext");
        List<NuxeoArtifact> artifacts = getSearcher().filterArtifact(getContext().getCoreSession(), distributionId, ExtensionPointInfo.TYPE_NAME, fulltext);
        List<String> cIds = new ArrayList<String>();
        for (NuxeoArtifact item : artifacts) {
            cIds.add(item.getId());
        }
        return getView("listContributions")
                .arg("cIds", cIds).arg("distId", ctx.getProperty("distId")).arg("searchFilter", fulltext);
    }


    @Path(value = "doc")
    public Resource viewDoc() {
        try {
            return ctx.newObject("documentation");
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Produces("text/html")
    @Path(value = "service2Bundle/{serviceId}")
    public Object service2Bundle(@PathParam("serviceId") String serviceId) throws Exception {

        ServiceInfo si = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getService(serviceId);
        if (si==null) {
            return null;
        }
        String cid = si.getComponentId();

        ComponentInfo ci = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getComponent(cid);
        String bid = ci.getBundle().getId();

        org.nuxeo.common.utils.Path target = new org.nuxeo.common.utils.Path(getContext().getRoot().getName());
        target = target.append(distributionId);
        target = target.append("viewBundle");
        target = target.append(bid + "#Service." + serviceId);
        return Response.seeOther(new URI(target.toString())).build();
    }

    @GET
    @Produces("text/html")
    @Path(value = "extensionPoint2Component/{epId}")
    public Object extensionPoint2Component(@PathParam("epId") String epId) throws Exception {

        ExtensionPointInfo epi = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession()).getExtensionPoint(epId);
        if (epi==null) {
            return null;
        }
        String cid = epi.getComponent().getId();

        org.nuxeo.common.utils.Path target = new org.nuxeo.common.utils.Path(getContext().getRoot().getName());
        target = target.append(distributionId);
        target = target.append("viewComponent");
        target = target.append(cid + "#extensionPoint." + epId);
        return Response.seeOther(new URI(target.toString())).build();
    }


    @Path(value = "viewBundle/{bundleId}")
    public Resource viewBundle(@PathParam("bundleId") String bundleId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("bundle", bundleId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewComponent/{componentId}")
    public Resource viewComponent(@PathParam("componentId") String componentId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("component", componentId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewSeamComponent/{componentId}")
    public Resource viewSeamComponent(@PathParam("componentId") String componentId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("seamComponent", componentId);
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewService/{serviceId}")
    public Resource viewService(@PathParam("serviceId") String serviceId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("service", serviceId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewExtensionPoint/{epId}")
    public Resource viewExtensionPoint(@PathParam("epId") String epId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("extensionPoint", epId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewContribution/{cId}")
    public Resource viewContribution(@PathParam("cId") String cId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("contribution", cId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewBundleGroup/{gId}")
    public Resource viewBundleGroup(@PathParam("gId") String gId) {
        try {
            NuxeoArtifactWebObject wo = (NuxeoArtifactWebObject) ctx.newObject("bundleGroup", gId);
            TreeHelper.updateTree(getContext(), wo.getNxArtifact().getHierarchyPath());
            return wo;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Path(value = "viewArtifact/{id}")
    public Object viewArtifact(@PathParam("id") String id) {
        try {

            DistributionSnapshot snap = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession());

            BundleGroup bg = snap.getBundleGroup(id);
            if (bg!=null) {
                return viewBundleGroup(id);
            }

            BundleInfo bi = snap.getBundle(id);
            if (bi!=null) {
                return viewBundle(id);
            }

            ComponentInfo ci = snap.getComponent(id);
            if (ci!=null) {
                return viewComponent(id);
            }

            ServiceInfo si = snap.getService(id);
            if (si!=null) {
                return viewService(id);
            }

            ExtensionPointInfo epi = snap.getExtensionPoint(id);
            if (epi!=null) {
                return viewExtensionPoint(id);
            }

            ExtensionInfo ei = snap.getContribution(id);
            if (ei!=null) {
                return viewContribution(id);
            }

            return Response.status(404).build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    public String getLabel(String id) {
        return null;
    }

    @GET
    @Produces("text/html")
    @Path(value = "listSeamComponents")
    public Object listSeamComponents() throws Exception {
        return dolistSeamComponents("listSeamComponents", false);
    }

    @GET
    @Produces("text/html")
    @Path(value = "listSeamComponentsSimple")
    public Object listSeamComponentsSimple() throws Exception {
        return dolistSeamComponents("listSeamComponentsSimple", true);
    }

    protected Object dolistSeamComponents(String view, boolean hideNav) throws Exception {

        getSnapshotManager().initSeamContext(getContext().getRequest());

        DistributionSnapshot snap = getSnapshotManager().getSnapshot(distributionId,ctx.getCoreSession());
        List<SeamComponentInfo> seamComponents = snap.getSeamComponents();
        return getView(view)
        .arg("seamComponents", seamComponents).arg("distId", ctx.getProperty("distId")).arg("hideNav", hideNav);
    }

}
