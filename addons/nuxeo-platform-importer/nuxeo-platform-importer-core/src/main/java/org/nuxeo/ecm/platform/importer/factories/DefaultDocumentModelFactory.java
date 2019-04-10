/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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

package org.nuxeo.ecm.platform.importer.factories;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.importer.source.SourceNode;

/**
 *
 * Default implementation for DocumentModel factory
 *
 * @author Thierry Delprat
 *
 */
public class DefaultDocumentModelFactory implements ImporterDocumentModelFactory {

    /* (non-Javadoc)
     * @see org.nuxeo.ecm.platform.importer.base.ImporterDocumentModelFactory#isTargetDocumentModelFolderish(org.nuxeo.ecm.platform.importer.base.SourceNode)
     */
    public boolean isTargetDocumentModelFolderish(SourceNode node) {
        return node.isFolderish();
    }

    /* (non-Javadoc)
     * @see org.nuxeo.ecm.platform.importer.base.ImporterDocumentModelFactory#createFolderishNode(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.platform.importer.base.SourceNode)
     */
    public DocumentModel createFolderishNode(CoreSession session,
            DocumentModel parent, SourceNode node) throws Exception {
        String docType = "Folder";
        String name = getValidNameFromFileName(node.getName());

        Map<String, Object> options = new HashMap<String, Object>();
        DocumentModel doc = session.createDocumentModel(docType, options);
        doc.setPathInfo(parent.getPathAsString(), name);
        doc.setProperty("dublincore", "title", node.getName());
        doc = session.createDocument(doc);
        return doc;
    }

    /* (non-Javadoc)
     * @see org.nuxeo.ecm.platform.importer.base.ImporterDocumentModelFactory#createLeafNode(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.platform.importer.base.SourceNode)
     */
    public DocumentModel createLeafNode(CoreSession session,
            DocumentModel parent, SourceNode node) throws Exception {
        String docType = "File";
        return defaultCreateLeafNode(session, parent, node, docType);
    }

    protected DocumentModel defaultCreateLeafNode(CoreSession session,
            DocumentModel parent, SourceNode node, String docType) throws Exception {

        BlobHolder bh = node.getBlobHolder();

        String mimeType = bh.getBlob().getMimeType();
        if (mimeType == null) {
            mimeType = getMimeType(node.getName());
        }

        String name = getValidNameFromFileName(node.getName());
        String fileName = node.getName();

        Map<String, Object> options = new HashMap<String, Object>();
        DocumentModel doc = session.createDocumentModel(docType, options);
        doc.setPathInfo(parent.getPathAsString(), name);
        doc.setProperty("dublincore", "title", node.getName());
        doc.setProperty("file", "filename", fileName);
        doc.setProperty("file", "content", bh.getBlob());

        Map<String, Serializable> props = bh.getProperties();
        if (props!=null) {
            for (String pName : props.keySet()) {
                doc.setPropertyValue(pName, props.get(pName));
            }
        }
        doc = session.createDocument(doc);
        return doc;
    }

    protected String getValidNameFromFileName(String fileName) {
        String name = IdUtils.generateId(fileName, "-", true, 100);
        name = name.replace("'", "");
        name = name.replace("(", "");
        name = name.replace(")", "");
        name = name.replace("+", "");
        return name;
    }

    /** Modify this to get right mime types depending on the file input */
    protected String getMimeType(String name) {
        // Dummy MimeType detection : plug nuxeo Real MimeType service to
        // have better results

        if (name == null) {
            return "application/octet-stream";
        } else if (name.endsWith(".doc")) {
            return "application/msword";
        } else if (name.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (name.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (name.endsWith(".txt")) {
            return "text/plain";
        } else if (name.endsWith(".html")) {
            return "text/html";
        } else if (name.endsWith(".xml")) {
            return "text/xml";
        } else if (name.endsWith(".jpg")) {
            return "image/jpeg";
        } else if (name.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (name.endsWith(".gif")) {
            return "image/gif";
        } else if (name.endsWith(".odt")) {
            return "application/vnd.oasis.opendocument.text";
        } else if (name.endsWith(".zip")) {
            return "application/zip";
        } else {
            return "application/octet-stream";
        }
    }

}
