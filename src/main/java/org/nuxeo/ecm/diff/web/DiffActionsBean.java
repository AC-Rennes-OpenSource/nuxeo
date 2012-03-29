/*
 * (C) Copyright 20012 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     ataillefer
 *
 */

package org.nuxeo.ecm.diff.web;

import static org.jboss.seam.ScopeType.CONVERSATION;
import static org.jboss.seam.ScopeType.PAGE;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.diff.model.DiffDisplayBlock;
import org.nuxeo.ecm.diff.model.DocumentDiff;
import org.nuxeo.ecm.diff.service.DiffDisplayService;
import org.nuxeo.ecm.diff.service.DocumentDiffService;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager;
import org.nuxeo.runtime.api.Framework;

/**
 * Handles document diff actions.
 *
 * @author <a href="mailto:ataillefer@nuxeo.com">Antoine Taillefer</a>
 */
@Name("diffActions")
@Scope(CONVERSATION)
public class DiffActionsBean implements Serializable {

    private static final long serialVersionUID = -5507491210664361778L;

    private static final String DOC_DIFF_VIEW = "view_doc_diff";

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(create = true, required = false)
    protected transient NavigationContext navigationContext;

    @In(create = true)
    protected transient DocumentsListsManager documentsListsManager;

    protected DocumentModel leftDoc;

    protected DocumentModel rightDoc;

    protected boolean isVersionDiff = false;

    /**
     * Checks if the diff action is available for the
     * {@link DocumentsListsManager#CURRENT_DOCUMENT_SELECTION} working list.
     *
     * @return true if can diff the current document selection
     */
    public boolean getCanDiffCurrentDocumentSelection() {

        return getCanDiffWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SELECTION);
    }

    /**
     * Checks if the diff action is available for the
     * {@link VersionDocumentsListsConstants#CURRENT_VERSION_SELECTION} working
     * list.
     *
     * @return true if can diff the current version selection
     */
    public boolean getCanDiffCurrentVersionSelection() {

        return getCanDiffWorkingList(DocumentsListsManager.CURRENT_VERSION_SELECTION);
    }

    /**
     * Checks if the diff action is available for the
     * {@link DocumentsListsManager#DEFAULT_WORKING_LIST} working list.
     *
     * @return true if can diff the current default working list selection
     */
    public boolean getCanDiffCurrentDefaultSelection() {

        return getCanDiffWorkingList(DocumentsListsManager.DEFAULT_WORKING_LIST);
    }

    /**
     * Checks if the diff action is available for the {@code listName} working
     * list.
     * <p>
     * Condition: the working list has exactly 2 documents.
     *
     * @param listName the list name
     * @return true if can diff the {@code listName} working list
     */
    public boolean getCanDiffWorkingList(String listName) {

        List<DocumentModel> currentSelectionWorkingList = documentsListsManager.getWorkingList(listName);
        return currentSelectionWorkingList != null
                && currentSelectionWorkingList.size() == 2;
    }

    /**
     * Prepares a diff of the current document selection.
     *
     * @return the view id
     * @throws ClientException the client exception
     */
    public String prepareCurrentDocumentSelectionDiff() throws ClientException {

        isVersionDiff = false;
        return prepareWorkingListDiff(DocumentsListsManager.CURRENT_DOCUMENT_SELECTION);
    }

    /**
     * Prepares a diff of the current version selection.
     *
     * @return the view id
     * @throws ClientException the client exception
     */
    public String prepareCurrentVersionSelectionDiff() throws ClientException {

        isVersionDiff = true;
        return prepareWorkingListDiff(DocumentsListsManager.CURRENT_VERSION_SELECTION);
    }

    /**
     * Prepares a diff of the current default selection.
     *
     * @return the view id
     * @throws ClientException the client exception
     */
    public String prepareCurrentDefaultSelectionDiff() throws ClientException {

        isVersionDiff = false;
        return prepareWorkingListDiff(DocumentsListsManager.DEFAULT_WORKING_LIST);
    }

    /**
     * Prepares a diff of the {@code listName} working list.
     *
     * @return the view id
     * @throws ClientException the client exception
     */
    public String prepareWorkingListDiff(String listName)
            throws ClientException {

        List<DocumentModel> workingList = getWorkingList(listName);

        leftDoc = workingList.get(0);
        rightDoc = workingList.get(1);

        return refresh();
    }

    /**
     * Prepares a diff of the selected version with the live doc.
     *
     * @param version the version
     * @return the view id
     * @throws ClientException the client exception
     */
    public String prepareCurrentVersionDiff(VersionModel selectedVersion)
            throws ClientException {

        isVersionDiff = true;

        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (currentDocument == null) {
            throw new ClientException(
                    "Cannot make a diff between selected version and current document since current document is null.");
        }

        DocumentModel docVersion = documentManager.getDocumentWithVersion(
                currentDocument.getRef(), selectedVersion);
        if (docVersion == null) {
            throw new ClientException(
                    "Cannot make a diff between selected version and current document since selected version document is null.");
        }

        leftDoc = currentDocument;
        rightDoc = docVersion;

        return DOC_DIFF_VIEW;
    }

    /**
     * Refreshes the diff between leftDoc and rightDoc.
     *
     * @return the view id
     * @throws ClientException the client exception
     */
    public String refresh() throws ClientException {

        // Fetch docs from repository
        if (isDocumentDiffAvailable()) {
            leftDoc = documentManager.getDocument(leftDoc.getRef());
            rightDoc = documentManager.getDocument(rightDoc.getRef());
        }

        return DOC_DIFF_VIEW;
    }

    /**
     * Checks if document diff is available.
     *
     * @return true, if is document diff available
     */
    public boolean isDocumentDiffAvailable() {
        return leftDoc != null && rightDoc != null;
    }

    /**
     * Gets the document diff.
     *
     * @return the document diff between leftDoc and rightDoc if leftDoc and
     *         rightDoc aren't null, else null
     * @throws ClientException the client exception
     */
    @Factory(value = "defaultDiffDisplayBlocks", scope = PAGE)
    public List<DiffDisplayBlock> getDefaultDiffDisplayBlocks()
            throws ClientException {

        if (leftDoc == null || rightDoc == null) {
            return new ArrayList<DiffDisplayBlock>();
        }

        DocumentDiff docDiff = getDocumentDiffService().diff(documentManager,
                leftDoc, rightDoc);
        return getDiffDisplayService().getDiffDisplayBlocks(docDiff, leftDoc,
                rightDoc);
    }

    /**
     * Gets the {@code listName} working list.
     *
     * @return the {@code listName} working list
     * @throws ClientException the client exception
     */
    protected final List<DocumentModel> getWorkingList(String listName)
            throws ClientException {

        List<DocumentModel> currentSelectionWorkingList = documentsListsManager.getWorkingList(listName);

        if (currentSelectionWorkingList == null
                || currentSelectionWorkingList.size() != 2) {
            throw new ClientException(
                    String.format(
                            "Cannot make a diff of the %s working list: need to have exactly 2 documents in the working list.",
                            listName));
        }
        return currentSelectionWorkingList;
    }

    /**
     * Gets the document diff service.
     *
     * @return the document diff service
     * @throws ClientException if cannot get the document diff service
     */
    protected final DocumentDiffService getDocumentDiffService()
            throws ClientException {

        DocumentDiffService documentDiffService;

        try {
            documentDiffService = Framework.getService(DocumentDiffService.class);
        } catch (Exception e) {
            throw ClientException.wrap(e);
        }
        if (documentDiffService == null) {
            throw new ClientException("DocumentDiffService is null.");
        }
        return documentDiffService;
    }

    /**
     * Gets the diff display service.
     *
     * @return the diff display service
     * @throws ClientException the client exception
     */
    protected final DiffDisplayService getDiffDisplayService()
            throws ClientException {

        DiffDisplayService diffDisplayService;

        try {
            diffDisplayService = Framework.getService(DiffDisplayService.class);
        } catch (Exception e) {
            throw ClientException.wrap(e);
        }
        if (diffDisplayService == null) {
            throw new ClientException("DiffDisplayService is null.");
        }
        return diffDisplayService;
    }

    public DocumentModel getLeftDoc() {
        return leftDoc;
    }

    public void setLeftDoc(DocumentModel leftDoc) {
        this.leftDoc = leftDoc;
    }

    public DocumentModel getRightDoc() {
        return rightDoc;
    }

    public void setRightDoc(DocumentModel rightDoc) {
        this.rightDoc = rightDoc;
    }

    public boolean isVersionDiff() {
        return isVersionDiff;
    }

    public void setVersionDiff(boolean isVersionDiff) {
        this.isVersionDiff = isVersionDiff;
    }
}
