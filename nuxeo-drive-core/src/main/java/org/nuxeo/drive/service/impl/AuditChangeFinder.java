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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.RootlessItemException;
import org.nuxeo.drive.service.FileSystemChangeFinder;
import org.nuxeo.drive.service.FileSystemItemAdapterService;
import org.nuxeo.drive.service.FileSystemItemChange;
import org.nuxeo.drive.service.NuxeoDriveEvents;
import org.nuxeo.drive.service.NuxeoDriveManager;
import org.nuxeo.drive.service.SynchronizationRoots;
import org.nuxeo.drive.service.TooManyChangesException;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;

/**
 * Implementation of {@link FileSystemChangeFinder} using the
 * {@link AuditReader}.
 *
 * @author Antoine Taillefer
 */
public class AuditChangeFinder implements FileSystemChangeFinder {

    private static final long serialVersionUID = 1963018967324857522L;

    private static final Log log = LogFactory.getLog(AuditChangeFinder.class);

    @Override
    public List<FileSystemItemChange> getFileSystemChanges(CoreSession session,
            Set<IdRef> lastActiveRootRefs, SynchronizationRoots activeRoots,
            long lastSuccessfulSyncDate, long syncDate, int limit)
            throws ClientException, TooManyChangesException {
        String principalName = session.getPrincipal().getName();
        List<FileSystemItemChange> changes = new ArrayList<FileSystemItemChange>();

        // Note: lastActiveRootRefs is not used: we could remove it from the
        // public API
        // and from the client as well but it might be useful to optimize future
        // alternative implementations FileSystemChangeFinder component so it
        // might
        // be better to leave it part of the public API as currently.

        // Find changes from the log under active roots or events that are
        // linked to the un-registration or deletion of formerly synchronized
        // roots
        List<LogEntry> entries = queryAuditEntries(session, activeRoots,
                lastSuccessfulSyncDate, syncDate, limit);

        // First pass over the entries: check that there was no root
        // registration / un-registration during that period.
        for (LogEntry entry : entries) {
            if (NuxeoDriveEvents.EVENT_CATEGORY.equals(entry.getCategory())) {
                // This is a root registration event for the current user:
                // the list of active roots has changed and the cache might
                // need to be invalidated: let's make sure we perform a
                // query with the actual active roots
                log.debug(String.format(
                        "Detected sync root change for user '%s' in audit log:"
                                + " invalidating the root cache and refetching the changes.",
                        principalName));
                NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
                driveManager.invalidateSynchronizationRootsCache(principalName);
                Map<String, SynchronizationRoots> synchronizationRoots = driveManager.getSynchronizationRoots(session.getPrincipal());
                SynchronizationRoots updatedActiveRoots = synchronizationRoots.get(session.getRepositoryName());
                entries = queryAuditEntries(session, updatedActiveRoots,
                        lastSuccessfulSyncDate, syncDate, limit);
                break;
            }
        }

        if (entries.size() >= limit) {
            throw new TooManyChangesException(
                    "Too many changes found in the audit logs.");
        }
        for (LogEntry entry : entries) {
            FileSystemItemChange change = null;
            DocumentRef docRef = new IdRef(entry.getDocUUID());
            ExtendedInfo fsIdInfo = entry.getExtendedInfos().get(
                    "fileSystemItemId");
            if (fsIdInfo != null) {
                // This document has been deleted or is an unregistered
                // synchronization root, we just know the FileSystemItem id and
                // name.
                log.debug(String.format(
                        "Found extended info in audit log entry, document %s has been deleted or is an unregistered synchronization root.",
                        docRef));
                boolean isChangeSet = false;
                // First try to adapt the document as a FileSystemItem to
                // provide it to the FileSystemItemChange entry.
                // This can succeed for an unregistered synchronization root
                // that can still be adapted as a FileSystemItem, for example in
                // the case of the "My Docs" virtual folder in the permission
                // based hierarchy implementation.
                if (session.exists(docRef)) {
                    change = getFileSystemItemChange(session, docRef, entry,
                            fsIdInfo.getValue(String.class));
                    if (change != null) {
                        isChangeSet = true;
                    }
                }
                if (!isChangeSet) {
                    // If the document is not adaptable as a FileSystemItem,
                    // typically if it has been deleted or if it is a regular
                    // unregistered synchronization root, only provide the
                    // FileSystemItem id and name to the FileSystemItemChange
                    // entry.
                    log.debug(String.format(
                            "Document %s doesn't exist or is not adaptable as a FileSystemItem, only providing the FileSystemItem id and name to the FileSystemItemChange entry.",
                            docRef));
                    String fsId = fsIdInfo.getValue(String.class);
                    String fsName = entry.getExtendedInfos().get(
                            "fileSystemItemName").getValue(String.class);
                    change = new FileSystemItemChangeImpl(entry.getEventId(),
                            entry.getEventDate().getTime(),
                            entry.getRepositoryId(), entry.getDocUUID(), fsId,
                            fsName);
                }
                log.debug(String.format(
                        "Adding FileSystemItemChange entry for document %s to the change summary.",
                        docRef));
                changes.add(change);
            } else {
                // No extended info in the audit log entry, this should not be a
                // deleted document nor an unregistered synchronization root.
                log.debug(String.format(
                        "No extended info found in audit log entry, document %s has not been deleted nor is an unregistered synchronization root.",
                        docRef));
                if (!session.exists(docRef)) {
                    log.debug(String.format(
                            "Document %s doesn't exist, not adding any entry to the change summary.",
                            docRef));
                    // deleted documents are mapped to deleted file
                    // system items in a separate event: no need to try
                    // to propagate this event.
                    // TODO: find a consistent way to map ACL removals as
                    // file system deletion change
                    // See https://jira.nuxeo.com/browse/NXP-11159
                    continue;
                }
                // Let's try to adapt the document as a FileSystemItem to
                // provide it to the FileSystemItemChange entry.
                change = getFileSystemItemChange(session, docRef, entry, null);
                if (change == null) {
                    // Non-adaptable documents are ignored
                    log.debug(String.format(
                            "Document %s is not adaptable as a FileSystemItem, not adding any entry to the change summary.",
                            docRef));
                } else {
                    log.debug(String.format(
                            "Adding FileSystemItemChange entry for document %s to the change summary.",
                            docRef));
                    changes.add(change);
                }
            }
        }
        return changes;
    }

    /**
     * Return the current time to query the logDate field of the audit log. This
     * time intentionally truncated to 0 milliseconds to have a consistent
     * behavior across databases.
     */
    @Override
    public long getCurrentDate() {
        long now = System.currentTimeMillis();
        return now - (now % 1000);
    }

    @SuppressWarnings("unchecked")
    protected List<LogEntry> queryAuditEntries(CoreSession session,
            SynchronizationRoots activeRoots, long lastSuccessfulSyncDate,
            long syncDate, int limit) {
        AuditReader auditService = Framework.getLocalService(AuditReader.class);
        // Set fixed query parameters
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("repositoryId", session.getRepositoryName());

        // Build query and set dynamic parameters
        StringBuilder auditQuerySb = new StringBuilder(
                "from LogEntry log where ");
        auditQuerySb.append("log.repositoryId = :repositoryId");
        auditQuerySb.append(" and ");
        auditQuerySb.append("(");
        if (!activeRoots.getPaths().isEmpty()) {
            // detect changes under the currently active roots for the
            // current user
            auditQuerySb.append("(");
            auditQuerySb.append("log.category = 'eventDocumentCategory'");
            // TODO: don't hardcode event ids (contribute them?)
            auditQuerySb.append(" and (log.eventId = 'documentCreated' or log.eventId = 'documentModified' or log.eventId = 'documentMoved' or log.eventId = 'documentCreatedByCopy'  or log.eventId = 'documentRestored')");
            auditQuerySb.append(" or ");
            auditQuerySb.append("log.category = 'eventLifeCycleCategory'");
            auditQuerySb.append(" and log.eventId = 'lifecycle_transition_event' and log.docLifeCycle != 'deleted' ");
            auditQuerySb.append(") and (");
            auditQuerySb.append(getCurrentRootFilteringClause(
                    activeRoots.getPaths(), params));
            auditQuerySb.append(") or ");
        }
        // detect any root (un-)registration changes for the roots previously
        // seen by the current user
        auditQuerySb.append("log.category = '");
        auditQuerySb.append(NuxeoDriveEvents.EVENT_CATEGORY);
        auditQuerySb.append("') and (");
        auditQuerySb.append(getJPADateClause(lastSuccessfulSyncDate, syncDate,
                params));
        // we intentionally sort by eventDate even if the range filtering is
        // done on the logDate: eventDate is useful to reflect the ordering of
        // events occurring inside the same transaction while the nearly
        // monotonic behavior of logDate is useful for ensuring that consecutive
        // range queries to the audit won't miss any events even when long
        // running transactions are logged after a delay.
        auditQuerySb.append(") order by log.repositoryId asc, log.eventDate desc");
        String auditQuery = auditQuerySb.toString();

        if (log.isDebugEnabled()) {
            log.debug("Querying audit log: " + auditQuery + " with params: "
                    + params);
        }
        List<LogEntry> entries = (List<LogEntry>) auditService.nativeQuery(
                auditQuery, params, 1, limit);

        // Post filter the output to remove (un)registration that are unrelated
        // to the current user.
        List<LogEntry> postFilteredEntries = new ArrayList<LogEntry>();
        String principalName = session.getPrincipal().getName();
        for (LogEntry entry : entries) {
            ExtendedInfo impactedUserInfo = entry.getExtendedInfos().get(
                    "impactedUserName");
            if (impactedUserInfo != null
                    && !principalName.equals(impactedUserInfo.getValue(String.class))) {
                // ignore event that only impact other users
                continue;
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format(
                        "Change detected at eventDate=%s logDate=%s: %s on %s",
                        entry.getEventDate(), entry.getLogDate(),
                        entry.getEventId(), entry.getDocPath()));
            }
            postFilteredEntries.add(entry);
        }
        return postFilteredEntries;
    }

    protected String getCurrentRootFilteringClause(Set<String> rootPaths,
            Map<String, Object> params) {
        StringBuilder rootPathClause = new StringBuilder();
        int rootPathCount = 0;
        for (String rootPath : rootPaths) {
            rootPathCount++;
            String rootPathParam = "rootPath" + rootPathCount;
            if (rootPathClause.length() > 0) {
                rootPathClause.append(" or ");
            }
            rootPathClause.append(String.format("log.docPath like :%s",
                    rootPathParam));
            params.put(rootPathParam, rootPath + '%');

        }
        return rootPathClause.toString();
    }

    protected String getJPADateClause(long lastSuccessfulSyncDate,
            long syncDate, Map<String, Object> params) {
        params.put("lastSuccessfulSyncDate", new Date(lastSuccessfulSyncDate));
        params.put("syncDate", new Date(syncDate));
        return "log.logDate >= :lastSuccessfulSyncDate and log.logDate < :syncDate";
    }

    protected FileSystemItemChange getFileSystemItemChange(CoreSession session,
            DocumentRef docRef, LogEntry entry, String expectedFileSystemItemId)
            throws ClientException {
        DocumentModel doc = session.getDocument(docRef);
        // TODO: check the facet, last root change and list of roots
        // to have a special handling for the roots.
        FileSystemItem fsItem = null;
        try {
            fsItem = Framework.getLocalService(
                    FileSystemItemAdapterService.class).getFileSystemItem(doc);
        } catch (RootlessItemException e) {
            // Can happen for an unregistered synchronization root that cannot
            // be adapted as a FileSystemItem: nothing to do.
            log.debug(String.format(
                    "RootlessItemException thrown while trying to adapt document %s as a FileSystemItem.",
                    docRef));
        }
        if (fsItem == null) {
            log.debug(String.format(
                    "Document %s is not adaptable as a FileSystemItem, returning null.",
                    docRef));
            return null;
        }
        if (expectedFileSystemItemId != null
                && !expectedFileSystemItemId.equals(fsItem.getId())) {
            log.debug(String.format(
                    "Id %s of FileSystemItem adapted from document %s doesn't match expected FileSystemItem id %s, returning null.",
                    fsItem.getId(), docRef, expectedFileSystemItemId));
            return null;
        }
        log.debug(String.format(
                "Document %s is adaptable as a FileSystemItem, providing it to the FileSystemItemChange entry.",
                docRef));
        // EventDate is able to reflect the ordering of the events
        // inside a transaction (e.g. when several documents are
        // created, updated, deleted at once) hence it's useful
        // to pass that info to the client even though the change
        // detection filtering is using the logDate to have a nearly
        // guaranteed monotonic behavior that evenDate cannot
        // guarantee when facing long transactions.
        return new FileSystemItemChangeImpl(entry.getEventId(),
                entry.getEventDate().getTime(), entry.getRepositoryId(),
                entry.getDocUUID(), fsItem);
    }

}
