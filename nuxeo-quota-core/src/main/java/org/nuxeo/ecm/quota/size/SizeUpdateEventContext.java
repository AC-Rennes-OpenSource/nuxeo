/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */

package org.nuxeo.ecm.quota.size;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.quota.QuotaStatsService;

/**
 * Custom EventContext used to propage info between the synchronous listener that is run by the
 * {@link QuotaStatsService} synchronously and the Asynchrous listener ( {@link QuotaComputerProcessor} that actually
 * does the work.
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 * @since 5.6
 */
public class SizeUpdateEventContext extends DocumentEventContext {

    public static final String DOCUMENT_UPDATE_INITIAL_STATISTICS = "documentUpdateInitialStats";

    private static final long serialVersionUID = 1L;

    public static final String BLOB_SIZE_PROPERTY_KEY = "blobSize";

    public static final String BLOB_DELTA_PROPERTY_KEY = "blobDelta";

    // holds the total size for all versions of a given doc
    // used when permanently deleting a doc and for the initial computation;
    // ( and restore)
    public static final String VERSIONS_SIZE_PROPERTY_KEY = "versionsSize";

    // used for the initial computation and restore
    // versions size to be added on the total size , differs from the versions
    // size if the doc is checked in
    public static final String VERSIONS_SIZE_ON_TOTAL_PROPERTY_KEY = "versionsSizeOnTotal";

    public static final String PARENT_UUIDS_PROPERTY_KEY = "parentUUIDs";

    public static final String SOURCE_EVENT_PROPERTY_KEY = "sourceEvent";

    // mark that an update trash is needed
    // used when permanently deleting a doc and in the initial computation
    // if the doc is in trash
    public static final String _UPDATE_TRASH_SIZE = "_UPDATE_TRASH";

    public SizeUpdateEventContext(CoreSession session, DocumentEventContext evtCtx, DocumentModel sourceDocument,
            BlobSizeInfo bsi, String sourceEvent) {
        super(session, evtCtx.getPrincipal(), sourceDocument, evtCtx.getDestination());
        setBlobSize(bsi.getBlobSize());
        setBlobDelta(bsi.getBlobSizeDelta());
        setProperty(SOURCE_EVENT_PROPERTY_KEY, sourceEvent);
    }

    public SizeUpdateEventContext(CoreSession session, BlobSizeInfo bsi, String sourceEvent,
            DocumentModel sourceDocument) {
        super(session, session.getPrincipal(), sourceDocument, null);
        setBlobSize(bsi.getBlobSize());
        setBlobDelta(bsi.getBlobSizeDelta());
        setProperty(SOURCE_EVENT_PROPERTY_KEY, sourceEvent);
    }

    public SizeUpdateEventContext(CoreSession session, DocumentEventContext evtCtx, BlobSizeInfo bsi, String sourceEvent) {
        super(session, evtCtx.getPrincipal(), evtCtx.getSourceDocument(), evtCtx.getDestination());
        setBlobSize(bsi.getBlobSize());
        setBlobDelta(bsi.getBlobSizeDelta());
        setProperty(SOURCE_EVENT_PROPERTY_KEY, sourceEvent);
    }

    public SizeUpdateEventContext(CoreSession session, DocumentEventContext evtCtx, long totalSize, String sourceEvent) {
        super(session, evtCtx.getPrincipal(), evtCtx.getSourceDocument(), evtCtx.getDestination());
        setBlobSize(totalSize);
        setBlobDelta(-totalSize);
        setProperty(SOURCE_EVENT_PROPERTY_KEY, sourceEvent);
    }

    public long getBlobSize() {
        return (Long) getProperty(BLOB_SIZE_PROPERTY_KEY);
    }

    public void setBlobSize(long blobSize) {
        setProperty(BLOB_SIZE_PROPERTY_KEY, new Long(blobSize));
    }

    public void setVersionsSize(long versionsSize) {
        setProperty(VERSIONS_SIZE_PROPERTY_KEY, new Long(versionsSize));
    }

    public long getVersionsSize() {
        if (getProperty(VERSIONS_SIZE_PROPERTY_KEY) != null) {
            return (Long) getProperty(VERSIONS_SIZE_PROPERTY_KEY);
        }
        return 0L;
    }

    /**
     * @since 5.7
     */
    public void setVersionsSizeOnTotal(long blobSize) {
        setProperty(VERSIONS_SIZE_ON_TOTAL_PROPERTY_KEY, blobSize);
    }

    /**
     * @since 5.7
     */
    public long getVersionsSizeOnTotal() {
        if (getProperty(VERSIONS_SIZE_ON_TOTAL_PROPERTY_KEY) != null) {
            return (Long) getProperty(VERSIONS_SIZE_ON_TOTAL_PROPERTY_KEY);
        }
        return 0L;
    }

    public long getBlobDelta() {
        return (Long) getProperty(BLOB_DELTA_PROPERTY_KEY);
    }

    public void setBlobDelta(long blobDelta) {
        setProperty(BLOB_DELTA_PROPERTY_KEY, new Long(blobDelta));
    }

    @SuppressWarnings("unchecked")
    public List<String> getParentUUIds() {
        return (List<String>) getProperty(PARENT_UUIDS_PROPERTY_KEY);
    }

    public void setParentUUIds(List<String> parentUUIds) {
        parentUUIds.removeAll(Collections.singleton(null));
        setProperty(PARENT_UUIDS_PROPERTY_KEY, (Serializable) parentUUIds);
    }

    public String getSourceEvent() {
        return (String) getProperty(SOURCE_EVENT_PROPERTY_KEY);
    }

    /**
     * @since 5.7
     */
    public long getTrashSize() {
        if (getProperty(_UPDATE_TRASH_SIZE) != null && (Boolean) getProperty(_UPDATE_TRASH_SIZE)) {
            return getBlobSize();
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nsourceDocument " + getSourceDocument().getId() + " " + getSourceDocument().getPathAsString());
        sb.append("\nprops " + getProperties().toString());
        return sb.toString();
    }

}
