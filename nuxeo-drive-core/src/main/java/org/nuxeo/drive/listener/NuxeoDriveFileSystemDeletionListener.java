/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Olivier Grisel <ogrisel@nuxeo.com>
 */
package org.nuxeo.drive.listener;

import java.security.Principal;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.RootlessItemException;
import org.nuxeo.drive.service.FileSystemItemAdapterService;
import org.nuxeo.drive.service.NuxeoDriveEvents;
import org.nuxeo.drive.service.NuxeoDriveManager;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;

/**
 * Event listener to track events that should be mapped to file system item
 * deletions in the the ChangeSummary computation.
 *
 * In particular this includes
 *
 * <li>Synchronization root unregistration (user specific)</li>
 *
 * <li>Simple document or root document lifecycle change to the 'deleted' state</li>
 *
 * <li>Simple document or root physical removal from the directory.</li>
 */
public class NuxeoDriveFileSystemDeletionListener implements EventListener {

    @Override
    public void handleEvent(Event event) throws ClientException {
        DocumentEventContext ctx;
        if (event.getContext() instanceof DocumentEventContext) {
            ctx = (DocumentEventContext) event.getContext();
        } else {
            // Not interested in events that are not related to documents
            return;
        }
        DocumentModel doc = ctx.getSourceDocument();
        if (doc.hasFacet(FacetNames.SYSTEM_DOCUMENT)) {
            // Not interested in system documents
            return;
        }
        DocumentModel docForLogEntry = doc;
        if (DocumentEventTypes.BEFORE_DOC_UPDATE.equals(event.getName())) {
            docForLogEntry = handleBeforeDocUpdate(ctx, doc);
            if (docForLogEntry == null) {
                return;
            }
        }
        if (DocumentEventTypes.ABOUT_TO_MOVE.equals(event.getName())
                && !handleAboutToMove(ctx, doc)) {
            return;
        }
        if (LifeCycleConstants.TRANSITION_EVENT.equals(event.getName())
                && !handleLifeCycleTransition(ctx)) {
            return;
        }
        if (DocumentEventTypes.ABOUT_TO_REMOVE.equals(event.getName())
                && !handleAboutToRemove(doc)) {
            return;
        }
        // Some events will only impact a specific user (e.g. root
        // unregistration)
        String impactedUserName = (String) ctx.getProperty(NuxeoDriveEvents.IMPACTED_USERNAME_PROPERTY);
        logDeletionEvent(docForLogEntry, ctx.getPrincipal(), impactedUserName);
    }

    protected DocumentModel handleBeforeDocUpdate(DocumentEventContext ctx,
            DocumentModel doc) throws ClientException {
        // Interested in update of a BlobHolder whose blob has been removed
        boolean blobRemoved = false;
        DocumentModel previousDoc = (DocumentModel) ctx.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
        if (previousDoc != null) {
            BlobHolder previousBh = previousDoc.getAdapter(BlobHolder.class);
            if (previousBh != null) {
                BlobHolder bh = doc.getAdapter(BlobHolder.class);
                if (bh != null) {
                    blobRemoved = previousBh.getBlob() != null
                            && bh.getBlob() == null;
                }
            }
        }
        if (blobRemoved) {
            // Use previous doc holding a Blob for it to be adaptable as a
            // FileSystemItem
            return previousDoc;
        } else {
            return null;
        }
    }

    protected boolean handleAboutToMove(DocumentEventContext ctx,
            DocumentModel doc) throws ClientException {
        // Interested in a move from a synchronization root to a non
        // synchronized container
        DocumentRef dstRef = (DocumentRef) ctx.getProperty(CoreEventConstants.DESTINATION_REF);
        if (dstRef == null) {
            return false;
        }
        CoreSession session = doc.getCoreSession();
        IdRef dstIdRef;
        if (dstRef instanceof IdRef) {
            dstIdRef = (IdRef) dstRef;
        } else {
            DocumentModel dstDoc = session.getDocument(dstRef);
            dstIdRef = new IdRef(dstDoc.getId());
        }
        if (Framework.getLocalService(NuxeoDriveManager.class).getSynchronizationRootReferences(
                session).contains(dstIdRef)) {
            return false;
        }
        return true;
    }

    protected boolean handleLifeCycleTransition(DocumentEventContext ctx)
            throws ClientException {
        String transition = (String) ctx.getProperty(LifeCycleConstants.TRANSTION_EVENT_OPTION_TRANSITION);
        // Interested in 'deleted' life cycle transition only
        return transition != null
                && LifeCycleConstants.DELETE_TRANSITION.equals(transition);

    }

    protected boolean handleAboutToRemove(DocumentModel doc)
            throws ClientException {
        // Document deletion of document that are already in deleted
        // state should not be marked as FS deletion to avoid duplicates
        return !LifeCycleConstants.DELETED_STATE.equals(doc.getCurrentLifeCycleState());
    }

    protected void logDeletionEvent(DocumentModel doc, Principal principal,
            String impactedUserName) throws ClientException {

        AuditLogger logger = Framework.getLocalService(AuditLogger.class);
        if (logger == null) {
            // The log is not deployed (probably in unittest)
            return;
        }
        FileSystemItem fsItem = null;
        try {
            fsItem = Framework.getLocalService(
                    FileSystemItemAdapterService.class).getFileSystemItem(doc,
                    true);
        } catch (RootlessItemException e) {
            // can happen when deleting a folder under and unregistered root:
            // nothing to do
            return;
        }
        if (fsItem == null) {
            return;
        }

        LogEntry entry = logger.newLogEntry();
        entry.setEventId(NuxeoDriveEvents.DELETED_EVENT);
        // XXX: shall we use the server local for the event date or UTC?
        entry.setEventDate(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());
        entry.setCategory((String) NuxeoDriveEvents.EVENT_CATEGORY);
        entry.setDocUUID(doc.getId());
        entry.setDocPath(doc.getPathAsString());
        entry.setPrincipalName(principal.getName());
        entry.setDocType(doc.getType());
        entry.setRepositoryId(doc.getRepositoryName());
        entry.setDocLifeCycle(doc.getCurrentLifeCycleState());

        Map<String, ExtendedInfo> extendedInfos = new HashMap<String, ExtendedInfo>();
        if (impactedUserName != null) {
            extendedInfos.put("impactedUserName",
                    logger.newExtendedInfo(impactedUserName));
        }
        // We do not serialize the whole object as it's too big to fit in a
        // StringInfo column and
        extendedInfos.put("fileSystemItemId",
                logger.newExtendedInfo(fsItem.getId()));
        extendedInfos.put("fileSystemItemName",
                logger.newExtendedInfo(fsItem.getName()));
        entry.setExtendedInfos(extendedInfos);
        logger.addLogEntries(Collections.singletonList(entry));
    }

}
