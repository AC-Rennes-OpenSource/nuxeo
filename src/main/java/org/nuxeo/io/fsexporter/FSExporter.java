/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     annejubert
 */

package org.nuxeo.io.fsexporter;

import java.io.IOException;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @author ajubert
 */
public class FSExporter extends DefaultComponent implements FSExporterService {

    protected FSExporterPlugin exporter = new DefaultExporterPlugin();

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {

        ExportLogicDescriptor exportLogicDesc = (ExportLogicDescriptor) contribution;
        if (exportLogicDesc.plugin != null) {
            try {
                exporter = exportLogicDesc.plugin.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void export(CoreSession session, String rootPath, String fspath, String PageProvider)
            throws IOException, Exception {
        DocumentModel root = session.getDocument(new PathRef(rootPath));
        serializeStructure(session, fspath, root, PageProvider);
    }

    private void serializeStructure(CoreSession session, String fsPath, DocumentModel doc, String PageProvider)
            throws IOException, Exception {

        exporter.serialize(session, doc, fsPath);

        if (doc.isFolder()) {

            DocumentModelList children = exporter.getChildren(session, doc, PageProvider);

            // getChildrenIterator
            for (DocumentModel child : children) {
                serializeStructure(session, fsPath + "/" + doc.getName(), child, PageProvider);
            }
        }
    }

    @Override
    public void exportXML(CoreSession session, String rootName, String fileSystemTarget) throws
            Exception {
        // TODO Auto-generated method stub
        //
        throw new UnsupportedOperationException();
    }

}
