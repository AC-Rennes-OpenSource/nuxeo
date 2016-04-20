/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Florent Guillaume
 */
package org.nuxeo.ecm.quota.size;

import static org.nuxeo.ecm.core.api.LifeCycleConstants.DELETE_TRANSITION;
import static org.nuxeo.ecm.core.api.LifeCycleConstants.UNDELETE_TRANSITION;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE_VERSION;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDOUT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED_BY_COPY;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_MOVED;
import static org.nuxeo.ecm.quota.size.SizeUpdateEventContext.DOCUMENT_UPDATE_INITIAL_STATISTICS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;

/**
 * Helper recomputing quotas.
 *
 * @since 5.6
 */
public class QuotaComputerProcessor {

    protected static final Log log = LogFactory.getLog(QuotaComputerProcessor.class);

    public void processQuotaComputation(SizeUpdateEventContext quotaCtx) {
        String sourceEvent = quotaCtx.getSourceEvent();
        CoreSession session = quotaCtx.getCoreSession();
        DocumentModel sourceDocument = quotaCtx.getSourceDocument();

        List<DocumentModel> parents = new ArrayList<DocumentModel>();

        log.debug(sourceEvent + "/ compute Quota on " + sourceDocument.getPathAsString() + " and parents");

        if (ABOUT_TO_REMOVE.equals(sourceEvent) || ABOUT_TO_REMOVE_VERSION.equals(sourceEvent)) {
            // use the store list of parentIds
            for (String id : quotaCtx.getParentUUIds()) {
                if (session.exists(new IdRef(id))) {
                    parents.add(session.getDocument(new IdRef(id)));
                }
            }
        } else if (DOCUMENT_MOVED.equals(sourceEvent)) {

            if (quotaCtx.getParentUUIds() != null && quotaCtx.getParentUUIds().size() > 0) {
                // use the store list of parentIds
                for (String id : quotaCtx.getParentUUIds()) {
                    if (session.exists(new IdRef(id))) {
                        parents.add(session.getDocument(new IdRef(id)));
                    }
                }
            } else {
                parents.addAll(getParents(sourceDocument, session));
            }
        } else {
            // DELETE_TRANSITION
            // UNDELETE_TRANSITION
            // BEFORE_DOC_UPDATE
            // DOCUMENT_CREATED
            // DOCUMENT_CREATED_BY_COPY
            // DOCUMENT_CHECKEDIN
            // DOCUMENT_CHECKEDOUT

            // several events in the bundle may impact the same doc,
            // so it may have already been modified
            sourceDocument = session.getDocument(sourceDocument.getRef());
            // TODO fix DocumentModel.refresh() to correctly take into account
            // dynamic facets, then use this instead:
            // sourceDocument.refresh();

            if (sourceDocument.getRef() == null) {
                log.error("SourceDocument has no ref");
            } else {
                parents.addAll(getParents(sourceDocument, session));
            }

            QuotaAware quotaDoc = sourceDocument.getAdapter(QuotaAware.class);
            // process Quota on target Document
            if (!DOCUMENT_CREATED_BY_COPY.equals(sourceEvent)) {
                if (quotaDoc == null) {
                    log.debug("  add Quota Facet on " + sourceDocument.getPathAsString());
                    quotaDoc = QuotaAwareDocumentFactory.make(sourceDocument, false);

                } else {
                    log.debug("  update Quota Facet on " + sourceDocument.getPathAsString());
                }
                if (DOCUMENT_CHECKEDIN.equals(sourceEvent)) {
                    long versionSize = getVersionSizeFromCtx(quotaCtx);
                    quotaDoc.addVersionsSize(versionSize, false);
                    quotaDoc.addTotalSize(versionSize, true);

                } else if (DOCUMENT_CHECKEDOUT.equals(sourceEvent)) {
                    // All quota computation are now handled on Checkin
                } else if (DELETE_TRANSITION.equals(sourceEvent) || UNDELETE_TRANSITION.equals(sourceEvent)) {
                    quotaDoc.addTrashSize(quotaCtx.getBlobSize(), true);
                } else if (DOCUMENT_UPDATE_INITIAL_STATISTICS.equals(sourceEvent)) {
                    quotaDoc.addInnerSize(quotaCtx.getBlobSize(), false);
                    quotaDoc.addTotalSize(quotaCtx.getVersionsSize(), false);
                    quotaDoc.addTrashSize(quotaCtx.getTrashSize(), false);
                    quotaDoc.addVersionsSize(quotaCtx.getVersionsSize(), true);
                } else {
                    // BEFORE_DOC_UPDATE
                    // DOCUMENT_CREATED
                    quotaDoc.addInnerSize(quotaCtx.getBlobDelta(), true);
                }
            } else {
                // When we copy some doc that are not folderish, we don't
                // copy the versions so we can't rely on the copied quotaDocInfo
                if (!sourceDocument.isFolder()) {
                    quotaDoc.resetInfos(false);
                    quotaDoc.setInnerSize(quotaCtx.getBlobSize(), true);
                }
            }

        }
        if (parents.size() > 0) {
            if (DOCUMENT_CHECKEDIN.equals(sourceEvent)) {
                long versionSize = getVersionSizeFromCtx(quotaCtx);

                processOnParents(parents, versionSize, 0L, versionSize, true, false, true);
            } else if (DOCUMENT_CHECKEDOUT.equals(sourceEvent)) {
                // All quota computation are now handled on Checkin
            } else if (DELETE_TRANSITION.equals(sourceEvent) || UNDELETE_TRANSITION.equals(sourceEvent)) {
                processOnParents(parents, 0, quotaCtx.getBlobSize(), false, true);
            } else if (ABOUT_TO_REMOVE_VERSION.equals(sourceEvent)) {
                processOnParents(parents, quotaCtx.getBlobDelta(), 0L, quotaCtx.getBlobDelta(), true, false, true);
            } else if (ABOUT_TO_REMOVE.equals(sourceEvent)) {
                // when permanently deleting the doc clean the trash if the doc
                // is in trash and all
                // archived versions size
                log.debug("Processing document about to be removed on parents. Total: " + quotaCtx.getBlobDelta()
                        + " , trash size: " + quotaCtx.getTrashSize() + " , versions size: "
                        + quotaCtx.getVersionsSize());
                processOnParents(parents, quotaCtx.getBlobDelta(),
                        quotaCtx.getBlobDelta()-quotaCtx.getVersionsSize(),
                        quotaCtx.getVersionsSize(),
                        true, quotaCtx.getProperties().get(SizeUpdateEventContext._UPDATE_TRASH_SIZE) != null
                                && (Boolean) quotaCtx.getProperties().get(SizeUpdateEventContext._UPDATE_TRASH_SIZE),
                        true);
            } else if (DOCUMENT_MOVED.equals(sourceEvent)) {
                // update versionsSize on source parents since all archived
                // versions
                // are also moved
                processOnParents(parents, quotaCtx.getBlobDelta(), 0L, quotaCtx.getVersionsSize(), true, false, true);
            } else if (DOCUMENT_UPDATE_INITIAL_STATISTICS.equals(sourceEvent)) {
                QuotaAware quotaDoc = sourceDocument.getAdapter(QuotaAware.class);
                if (quotaDoc.getInnerSize() > 0) {
                    processOnParents(parents, quotaCtx.getBlobSize() + quotaCtx.getVersionsSize(),
                            quotaCtx.getBlobSize(),
                            quotaCtx.getVersionsSize(), true,
                            quotaCtx.getProperties().get(SizeUpdateEventContext._UPDATE_TRASH_SIZE) != null
                            && (Boolean) quotaCtx.getProperties().get(SizeUpdateEventContext._UPDATE_TRASH_SIZE),
                            true);
                } else {
                    log.debug("No inner size, parents not updated");
                }
            } else if (DOCUMENT_CREATED_BY_COPY.equals(sourceEvent)) {
                processOnParents(parents, quotaCtx.getBlobSize());
            } else {
                processOnParents(parents, quotaCtx.getBlobDelta());
            }
        }
    }

    /**
     * @param quotaCtx
     * @return
     */
    private long getVersionSizeFromCtx(SizeUpdateEventContext quotaCtx) {
        return quotaCtx.getBlobSize();
    }

    protected void processOnParents(List<DocumentModel> parents, long delta)
            {
        processOnParents(parents, delta, 0L, 0L, true, false, false);
    }

    protected void processOnParents(List<DocumentModel> parents, long delta, long trash, boolean total, boolean trashOp)
            {
        processOnParents(parents, delta, trash, 0L, total, trashOp, false);
    }

    protected void processOnParents(List<DocumentModel> parents, long deltaTotal, long trashSize, long deltaVersions,
            boolean total, boolean trashOp, boolean versionsOp) {
        for (DocumentModel parent : parents) {
            // process Quota on target Document
            QuotaAware quotaDoc = parent.getAdapter(QuotaAware.class);
            boolean toSave = false;
            if (quotaDoc == null) {
                log.debug("   add Quota Facet on parent " + parent.getPathAsString());
                quotaDoc = QuotaAwareDocumentFactory.make(parent, false);
                toSave = true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("   update Quota Facet on parent " + parent.getPathAsString() + " (" + quotaDoc.getQuotaInfo() + ")");
                }
            }
            if (total) {
                quotaDoc.addTotalSize(deltaTotal, false);
                toSave = true;
            }
            if (trashOp) {
                quotaDoc.addTrashSize(trashSize, false);
                toSave = true;
            }
            if (versionsOp) {
                quotaDoc.addVersionsSize(deltaVersions, false);
                toSave = true;
            }
            if (toSave) {
                quotaDoc.save(true);
            }
            try {
                quotaDoc.invalidateTotalSizeCache();
            } catch (IOException e) {
                log.error(e.getMessage() + ": unable to invalidate cache " + QuotaAware.QUOTA_TOTALSIZE_CACHE_NAME + " for " + quotaDoc.getDoc().getId());
            }
            if (log.isDebugEnabled()) {
                log.debug("   ==> " + parent.getPathAsString() + " (" + quotaDoc.getQuotaInfo() + ")");
            }
        }
    }

    protected List<DocumentModel> getParents(DocumentModel sourceDocument, CoreSession session) {
        List<DocumentModel> parents = new ArrayList<DocumentModel>();
        // use getParentDocumentRefs instead of getParentDocuments , beacuse
        // getParentDocuments doesn't fetch the root document
        DocumentRef[] parentRefs = session.getParentDocumentRefs(sourceDocument.getRef());
        for (DocumentRef documentRef : parentRefs) {
            parents.add(session.getDocument(documentRef));
        }
        return parents;
    }
}
