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

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.importer.source.SourceNode;

/**
 *
 * Default implementation for DocumentModel factory
 * The default empty constructor create Folder for folderish file and
 * File for other. But you can specify them using the other constructor.
 *
 * @author Thierry Delprat
 * @author Daniel Tellez
 *
 */
public class DefaultDocumentModelFactory extends AbstractDocumentModelFactory {

    protected String folderishType;

    protected String leafType;

    /**
     * Instantiate a DefaultDocumentModelFactory that creates Folder and File
     */
    public DefaultDocumentModelFactory() {
        this("Folder", "File");
    }

    /**
     * Instantiate a DefaultDocumentModelFactory that creates specified types doc
     * @param folderishType the folderish type
     * @param leafType the other type
     */
    public DefaultDocumentModelFactory(String folderishType, String leafType) {
        this.folderishType = folderishType;
        this.leafType = leafType;
    }

    /*
     * (non-Javadoc)
     *
     * @seeorg.nuxeo.ecm.platform.importer.base.ImporterDocumentModelFactory#
     * createFolderishNode(org.nuxeo.ecm.core.api.CoreSession,
     * org.nuxeo.ecm.core.api.DocumentModel,
     * org.nuxeo.ecm.platform.importer.base.SourceNode)
     */
    public DocumentModel createFolderishNode(CoreSession session,
            DocumentModel parent, SourceNode node) throws Exception {
        String name = getValidNameFromFileName(node.getName());

        Map<String, Object> options = new HashMap<String, Object>();
        DocumentModel doc = session.createDocumentModel(folderishType, options);
        doc.setPathInfo(parent.getPathAsString(), name);
        doc.setProperty("dublincore", "title", node.getName());
        doc = session.createDocument(doc);
        return doc;
    }

    /*
     * (non-Javadoc)
     *
     * @seeorg.nuxeo.ecm.platform.importer.base.ImporterDocumentModelFactory#
     * createLeafNode(org.nuxeo.ecm.core.api.CoreSession,
     * org.nuxeo.ecm.core.api.DocumentModel,
     * org.nuxeo.ecm.platform.importer.base.SourceNode)
     */
    public DocumentModel createLeafNode(CoreSession session,
            DocumentModel parent, SourceNode node) throws Exception {
        return defaultCreateLeafNode(session, parent, node);
    }

    protected DocumentModel defaultCreateLeafNode(CoreSession session,
            DocumentModel parent, SourceNode node)
            throws Exception {

        BlobHolder bh = node.getBlobHolder();

        String mimeType = bh.getBlob().getMimeType();
        if (mimeType == null) {
            mimeType = getMimeType(node.getName());
        }

        String name = getValidNameFromFileName(node.getName());
        String fileName = node.getName();

        Map<String, Object> options = new HashMap<String, Object>();
        DocumentModel doc = session.createDocumentModel(leafType, options);
        doc.setPathInfo(parent.getPathAsString(), name);
        doc.setProperty("dublincore", "title", node.getName());
        doc.setProperty("file", "filename", fileName);
        doc.setProperty("file", "content", bh.getBlob());

        doc = session.createDocument(doc);
        doc = setDocumentProperties(session, bh.getProperties(), doc);
        return doc;
    }

    /** Modify this to get right mime types depending on the file input */
    protected String getMimeType(String name) {
        // Dummy MimeType detection : plug nuxeo Real MimeType service to
        // have better results

        if (name == null) {
            return "application/octet-stream";
            /* OpenOffice.org 2.x document types */
        } else if (name.endsWith(".odp")) {
            return "application/vnd.oasis.opendocument.presentation";
        } else if (name.endsWith(".otp")) {
            return "application/vnd.oasis.opendocument.presentation-template";
        } else if (name.endsWith(".otg")) {
            return "application/vnd.oasis.opendocument.graphics-template";
        } else if (name.endsWith(".odg")) {
            return "application/vnd.oasis.opendocument.graphics";
        } else if (name.endsWith(".odt")) {
            return "application/vnd.oasis.opendocument.text";
        } else if (name.endsWith(".ott")) {
            return "application/vnd.oasis.opendocument.text-template";
        } else if (name.endsWith(".ods")) {
            return "application/vnd.oasis.opendocument.spreadsheet";
        } else if (name.endsWith(".ots")) {
            return "application/vnd.oasis.opendocument.spreadsheet-template";
            /* Microsoft Office document */
        } else if (name.endsWith(".doc")) {
            return "application/msword";
        } else if (name.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (name.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
            /* Ms Office 2007 */
        } else if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (name.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.template";
            /* Other */
        } else if (name.endsWith(".tar")) {
            return "application/x-gtar";
        } else if (name.endsWith(".gz")) {
            return "application/x-gtar";
        } else if (name.endsWith(".csv")) {
            return "text/csv";
        } else if (name.endsWith(".pdf")) {
            return "application/pdf";
        } else if (name.endsWith(".txt")) {
            return "text/plain";
        } else if (name.endsWith(".html")) {
            return "text/html";
        } else if (name.endsWith(".xml")) {
            return "text/xml";
        } else if (name.endsWith(".png")) {
            return "image/png";
        } else if (name.endsWith(".jpg")) {
            return "image/jpg";
        } else if (name.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (name.endsWith(".gif")) {
            return "image/gif";
        } else if (name.endsWith(".zip")) {
            return "application/zip";
        } else {
            return "application/octet-stream";
        }
    }

}
