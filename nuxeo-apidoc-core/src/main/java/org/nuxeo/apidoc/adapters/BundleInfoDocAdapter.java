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
package org.nuxeo.apidoc.adapters;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.nuxeo.apidoc.api.BundleInfo;
import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;

public class BundleInfoDocAdapter extends BaseNuxeoArtifactDocAdapter implements
        BundleInfo {

    public static BundleInfoDocAdapter create(BundleInfo bundleInfo,
            CoreSession session, String containerPath) throws ClientException {

        DocumentModel doc = session.createDocumentModel(TYPE_NAME);
        String name = computeDocumentName("bundle-" + bundleInfo.getId());
        String targetPath = new Path(containerPath).append(name).toString();
        boolean exist = false;
        if (session.exists(new PathRef(targetPath))) {
            exist = true;
            doc = session.getDocument(new PathRef(targetPath));
        }
        doc.setPathInfo(containerPath, name);
        doc.setPropertyValue("dc:title", bundleInfo.getBundleId());
        doc.setPropertyValue("nxbundle:artifactGroupId",
                bundleInfo.getArtifactGroupId());
        doc.setPropertyValue("nxbundle:artifactId", bundleInfo.getArtifactId());
        doc.setPropertyValue("nxbundle:artifactVersion",
                bundleInfo.getArtifactVersion());
        doc.setPropertyValue("nxbundle:bundleId", bundleInfo.getId());
        doc.setPropertyValue("nxbundle:jarName", bundleInfo.getFileName());
        Blob manifestBlob = new StringBlob(bundleInfo.getManifest());
        manifestBlob.setFilename("MANIFEST.MF");
        manifestBlob.setMimeType("text/plain");
        doc.setPropertyValue("file:content", (Serializable) manifestBlob);

        if (exist) {
            doc = session.saveDocument(doc);
        } else {
            doc = session.createDocument(doc);
        }

        return new BundleInfoDocAdapter(doc);
    }

    public BundleInfoDocAdapter(DocumentModel doc) {
        super(doc);
    }

    @Override
    public String getArtifactId() {
        return safeGet("nxbundle:artifactId");
    }

    @Override
    public String getBundleId() {
        return safeGet("nxbundle:bundleId");
    }

    @Override
    public Collection<ComponentInfo> getComponents() {
        List<ComponentInfo> components = new ArrayList<ComponentInfo>();

        try {
            List<DocumentModel> children = getCoreSession().getChildren(
                    doc.getRef());

            for (DocumentModel child : children) {
                ComponentInfo comp = child.getAdapter(ComponentInfo.class);
                if (comp != null) {
                    components.add(comp);
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
        return components;
    }

    @Override
    public String getFileName() {
        return safeGet("nxbundle:jarName");
    }

    @Override
    public String getArtifactGroupId() {
        return safeGet("nxbundle:artifactGroupId");
    }

    @Override
    public String getLocation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getManifest() {
        try {
            Blob mf = safeGet(Blob.class, "file:content", new StringBlob(
                    "No MANIFEST"));
            if (mf.getEncoding() == null || "".equals(mf.getEncoding())) {
                mf.setEncoding("utf-8");
            }
            return mf.getString();
        } catch (IOException e) {
            log.error("Error while reading blob", e);
            return "";
        }
    }

    @Override
    public String[] getRequirements() {
        return null;
    }

    @Override
    public String getArtifactVersion() {
        return safeGet("nxbundle:artifactVersion", null);
    }

    @Override
    public String getId() {
        return getBundleId();
    }

    @Override
    public String getVersion() {
        return getArtifactVersion();
    }

    @Override
    public String getArtifactType() {
        return TYPE_NAME;
    }

}
