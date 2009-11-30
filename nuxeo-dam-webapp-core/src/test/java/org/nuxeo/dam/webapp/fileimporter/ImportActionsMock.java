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
 *     Nuxeo
 */

package org.nuxeo.dam.webapp.fileimporter;

import org.nuxeo.dam.platform.context.ImportActionsBean;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;

public class ImportActionsMock extends ImportActionsBean {

    private static final long serialVersionUID = 1L;

    public ImportActionsMock(CoreSession coreSession, FileManager fileManager) {
        documentManager = coreSession;
        this.fileManagerService = fileManager;
    }

    public void setFileManagerService(FileManager fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    // don't need UI messages
    @Override
    public void logDocumentWithTitle(String facesMessage, String someLogString,
            DocumentModel document) {

    }

    @Override
    protected void sendImportSetCreationEvent() {
    }

}
