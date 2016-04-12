/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.adapter.impl;

import static org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.drive.adapter.FileItem;
import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.FolderItem;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * {@link DocumentModel} backed implementation of a {@link FolderItem}.
 *
 * @author Antoine Taillefer
 */
public class DocumentBackedFolderItem extends AbstractDocumentBackedFileSystemItem implements FolderItem {

    private static final Log log = LogFactory.getLog(DocumentBackedFolderItem.class);

    private static final long serialVersionUID = 1L;

    private static final String FOLDER_ITEM_CHILDREN_PAGE_PROVIDER = "FOLDER_ITEM_CHILDREN";

    protected static final String MAX_DESCENDANTS_BATCH_SIZE_PROPERTY = "org.nuxeo.drive.maxDescendantsBatchSize";

    protected static final String MAX_DESCENDANTS_BATCH_SIZE_DEFAULT = "1000";

    protected boolean canCreateChild;

    protected boolean canGetDescendants;

    public DocumentBackedFolderItem(String factoryName, DocumentModel doc) {
        this(factoryName, doc, false);
    }

    public DocumentBackedFolderItem(String factoryName, DocumentModel doc, boolean relaxSyncRootConstraint) {
        super(factoryName, doc, relaxSyncRootConstraint);
        initialize(doc);
    }

    public DocumentBackedFolderItem(String factoryName, FolderItem parentItem, DocumentModel doc) {
        this(factoryName, parentItem, doc, false);
    }

    public DocumentBackedFolderItem(String factoryName, FolderItem parentItem, DocumentModel doc,
            boolean relaxSyncRootConstraint) {
        super(factoryName, parentItem, doc, relaxSyncRootConstraint);
        initialize(doc);
    }

    protected DocumentBackedFolderItem() {
        // Needed for JSON deserialization
    }

    /*--------------------- FileSystemItem ---------------------*/
    @Override
    public void rename(String name) {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            // Update doc properties
            DocumentModel doc = getDocument(session);
            doc.setPropertyValue("dc:title", name);
            doc = session.saveDocument(doc);
            session.save();
            // Update FileSystemItem attributes
            this.docTitle = name;
            this.name = name;
            updateLastModificationDate(doc);
        }
    }

    /*--------------------- FolderItem -----------------*/
    @Override
    @SuppressWarnings("unchecked")
    public List<FileSystemItem> getChildren() {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            PageProviderService pageProviderService = Framework.getLocalService(PageProviderService.class);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put(CORE_SESSION_PROPERTY, (Serializable) session);
            PageProvider<DocumentModel> childrenPageProvider = (PageProvider<DocumentModel>) pageProviderService.getPageProvider(
                    FOLDER_ITEM_CHILDREN_PAGE_PROVIDER, null, null, 0L, props, docId);
            Long pageSize = childrenPageProvider.getPageSize();

            List<FileSystemItem> children = new ArrayList<FileSystemItem>();
            int nbChildren = 0;
            boolean reachedPageSize = false;
            boolean hasNextPage = true;
            // Since query results are filtered, make sure we iterate on PageProvider to get at most its page size
            // number of
            // FileSystemItems
            while (nbChildren < pageSize && hasNextPage) {
                List<DocumentModel> dmChildren = childrenPageProvider.getCurrentPage();
                for (DocumentModel dmChild : dmChildren) {
                    FileSystemItem child = getFileSystemItemAdapterService().getFileSystemItem(dmChild, this);
                    if (child != null) {
                        children.add(child);
                        nbChildren++;
                        if (nbChildren == pageSize) {
                            reachedPageSize = true;
                            break;
                        }
                    }
                }
                if (!reachedPageSize) {
                    hasNextPage = childrenPageProvider.isNextPageAvailable();
                    if (hasNextPage) {
                        childrenPageProvider.nextPage();
                    }
                }
            }

            return children;
        }
    }

    @Override
    public boolean getCanGetDescendants() {
        return canGetDescendants;
    }

    @Override
    public List<FileSystemItem> getDescendants(int max, String lowerId) {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {

            // Limit batch size sent by the client
            int maxDescendantsBatchSize = Integer.parseInt(Framework.getService(ConfigurationService.class)
                                                                    .getProperty(MAX_DESCENDANTS_BATCH_SIZE_PROPERTY,
                                                                            MAX_DESCENDANTS_BATCH_SIZE_DEFAULT));
            if (max > maxDescendantsBatchSize) {
                throw new NuxeoException(
                        String.format(
                                "Maximum number of descendants %d is greater than the maximum batch size allowed %d. If you need to increase this limit you can set the %s configuration property but this is not recommended for performance reasons.",
                                max, maxDescendantsBatchSize, MAX_DESCENDANTS_BATCH_SIZE_PROPERTY));
            }

            // Fetch documents
            StringBuilder sb = new StringBuilder(String.format("SELECT * FROM Document WHERE ecm:ancestorId = '%s'",
                    docId));
            sb.append(" AND ecm:currentLifeCycleState != 'deleted'");
            sb.append(" AND ecm:mixinType != 'HiddenInNavigation'");
            // Don't need to add ecm:isCheckedInVersion = 0 because versions are already excluded by the
            // ecm:ancestorId clause since they have no path
            if (!StringUtils.isEmpty(lowerId)) {
                String lowerDocId = parseFileSystemId(lowerId)[2];
                sb.append(String.format(" AND ecm:uuid > '%s'", lowerDocId));
            }
            sb.append(" ORDER BY ecm:uuid");
            String query = sb.toString();
            if (log.isDebugEnabled()) {
                log.debug(String.format("Getting %d descendants of %s using query: %s", max, docPath, query));
            }
            DocumentModelList dmDescendants = session.query(query, max);

            // Adapt documents as FileSystemItems
            List<FileSystemItem> descendants = new ArrayList<>();
            for (DocumentModel dmDescendant : dmDescendants) {
                // TODO: optimize FileSystemItem path computation
                FileSystemItem descendant = getFileSystemItemAdapterService().getFileSystemItem(dmDescendant);
                if (descendant != null) {
                    descendants.add(descendant);
                }
            }
            return descendants;
        }
    }

    @Override
    public boolean getCanCreateChild() {
        return canCreateChild;
    }

    @Override
    public FolderItem createFolder(String name) {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            DocumentModel folder = getFileManager().createFolder(session, name, docPath);
            if (folder == null) {
                throw new NuxeoException(
                        String.format(
                                "Cannot create folder named '%s' as a child of doc %s. Probably because of the allowed sub-types for this doc type, please check them.",
                                name, docPath));
            }
            return (FolderItem) getFileSystemItemAdapterService().getFileSystemItem(folder, this);
        } catch (NuxeoException e) {
            e.addInfo(String.format("Error while trying to create folder %s as a child of doc %s", name, docPath));
            throw e;
        } catch (IOException e) {
            throw new NuxeoException(String.format("Error while trying to create folder %s as a child of doc %s", name,
                    docPath), e);
        }
    }

    @Override
    public FileItem createFile(Blob blob) {
        String fileName = blob.getFilename();
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            // TODO: manage conflict (overwrite should not necessarily be true)
            DocumentModel file = getFileManager().createDocumentFromBlob(session, blob, docPath, true, fileName);
            if (file == null) {
                throw new NuxeoException(
                        String.format(
                                "Cannot create file '%s' as a child of doc %s. Probably because there are no file importers registered, please check the contributions to the <extension target=\"org.nuxeo.ecm.platform.filemanager.service.FileManagerService\" point=\"plugins\"> extension point.",
                                fileName, docPath));
            }
            return (FileItem) getFileSystemItemAdapterService().getFileSystemItem(file, this);
        } catch (NuxeoException e) {
            e.addInfo(String.format("Error while trying to create file %s as a child of doc %s", fileName, docPath));
            throw e;
        } catch (IOException e) {
            throw new NuxeoException(String.format("Error while trying to create file %s as a child of doc %s",
                    fileName, docPath), e);
        }
    }

    /*--------------------- Protected -----------------*/
    protected void initialize(DocumentModel doc) {
        this.name = docTitle;
        this.folder = true;
        this.canCreateChild = !doc.hasFacet(FacetNames.PUBLISH_SPACE);
        if (canCreateChild) {
            if (Framework.getService(ConfigurationService.class).isBooleanPropertyTrue(
                    PERMISSION_CHECK_OPTIMIZED_PROPERTY)) {
                // In optimized mode consider that canCreateChild <=> canRename because canRename <=> WriteProperties
                // and by default WriteProperties <=> Write <=> AddChildren
                this.canCreateChild = canRename;
            } else {
                // In non optimized mode check AddChildren
                this.canCreateChild = doc.getCoreSession().hasPermission(doc.getRef(), SecurityConstants.ADD_CHILDREN);
            }
        }
        this.canGetDescendants = true;
    }

    protected FileManager getFileManager() {
        return Framework.getLocalService(FileManager.class);
    }

    /*---------- Needed for JSON deserialization ----------*/
    protected void setCanCreateChild(boolean canCreateChild) {
        this.canCreateChild = canCreateChild;
    }

    protected void setCanGetDescendants(boolean canGetDescendants) {
        this.canGetDescendants = canGetDescendants;
    }

}
