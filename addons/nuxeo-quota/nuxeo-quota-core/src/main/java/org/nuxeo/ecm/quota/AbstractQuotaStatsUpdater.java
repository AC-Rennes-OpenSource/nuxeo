/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.quota;

import static org.nuxeo.ecm.core.api.LifeCycleConstants.TRANSITION_EVENT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE_VERSION;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_RESTORE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDOUT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED_BY_COPY;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_MOVED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_RESTORED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static org.nuxeo.ecm.platform.audit.service.NXAuditEventsService.DISABLE_AUDIT_LOGGER;
import static org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener.DISABLE_DUBLINCORE_LISTENER;
import static org.nuxeo.ecm.platform.ec.notification.NotificationConstants.DISABLE_NOTIFICATION_SERVICE;
import static org.nuxeo.ecm.platform.publisher.listeners.DomainEventsListener.DISABLE_DOMAIN_LISTENER;
import static org.nuxeo.ecm.platform.htmlsanitizer.HtmlSanitizerListener.DISABLE_HTMLSANITIZER_LISTENER;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.versioning.VersioningService;

/**
 * Abstract class implementing {@code QuotaStatsUpdater} to handle common cases.
 * <p>
 * Provides abstract methods to override for common events.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
public abstract class AbstractQuotaStatsUpdater implements QuotaStatsUpdater {

    protected String name;

    protected String label;

    protected String descriptionLabel;

    protected static Log log = LogFactory.getLog(AbstractQuotaStatsUpdater.class);

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setDescriptionLabel(String descriptionLabel) {
        this.descriptionLabel = descriptionLabel;
    }

    @Override
    public String getDescriptionLabel() {
        return descriptionLabel;
    }

    @Override
    public void updateStatistics(CoreSession session, DocumentEventContext docCtx, Event event) throws ClientException {
        DocumentModel doc = docCtx.getSourceDocument();

        if (!needToProcessEventOnDocument(event, doc)) {
            log.debug("Exit Listener !!!!");
            return;
        }

        String eventName = event.getName();

        try {
            if (DOCUMENT_CREATED.equals(eventName)) {
                processDocumentCreated(session, doc, docCtx);
            } else if (ABOUT_TO_REMOVE.equals(eventName) || ABOUT_TO_REMOVE_VERSION.equals(eventName)) {
                processDocumentAboutToBeRemoved(session, doc, docCtx);
            } else if (DOCUMENT_CREATED_BY_COPY.equals(eventName)) {
                processDocumentCopied(session, doc, docCtx);
            } else if (DOCUMENT_MOVED.equals(eventName)) {
                DocumentRef sourceParentRef = (DocumentRef) docCtx.getProperty(CoreEventConstants.PARENT_PATH);
                DocumentModel sourceParent = session.getDocument(sourceParentRef);
                processDocumentMoved(session, doc, sourceParent, docCtx);
            } else if (DOCUMENT_UPDATED.equals(eventName)) {
                processDocumentUpdated(session, doc, docCtx);
            } else if (BEFORE_DOC_UPDATE.equals(eventName)) {
                processDocumentBeforeUpdate(session, doc, docCtx);
            } else if (TRANSITION_EVENT.equals(eventName)) {
                processDocumentTrashOp(session, doc, docCtx);
            } else if (DOCUMENT_CHECKEDIN.equals(eventName)) {
                processDocumentCheckedIn(session, doc, docCtx);
            } else if (DOCUMENT_CHECKEDOUT.equals(eventName)) {
                processDocumentCheckedOut(session, doc, docCtx);
            } else if (DOCUMENT_RESTORED.equals(eventName)) {
                processDocumentRestored(session, doc, docCtx);
            } else if (BEFORE_DOC_RESTORE.equals(eventName)) {
                processDocumentBeforeRestore(session, doc, docCtx);
            }
        } catch (ClientException e) {
            ClientException e2 = handleException(e, event);
            if (e2 != null) {
                throw e2;
            }
        }
    }

    protected List<DocumentModel> getAncestors(CoreSession session, DocumentModel doc) throws ClientException {
        List<DocumentModel> ancestors = new ArrayList<DocumentModel>();
        if (doc != null && doc.getParentRef() != null) {
            doc = session.getDocument(doc.getParentRef());
            while (doc != null && !doc.getPath().isRoot()) {
                ancestors.add(doc);
                doc = session.getDocument(doc.getParentRef());
            }
        }
        return ancestors;
    }

    protected abstract ClientException handleException(ClientException e, Event event);

    protected abstract boolean needToProcessEventOnDocument(Event event, DocumentModel targetDoc);

    protected abstract void processDocumentCreated(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentCopied(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentCheckedIn(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentCheckedOut(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx) throws ClientException;

    protected abstract void processDocumentUpdated(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentMoved(CoreSession session, DocumentModel doc, DocumentModel sourceParent,
            DocumentEventContext docCtx) throws ClientException;

    protected abstract void processDocumentAboutToBeRemoved(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx) throws ClientException;

    protected abstract void processDocumentBeforeUpdate(CoreSession session, DocumentModel targetDoc,
            DocumentEventContext docCtx) throws ClientException;

    protected abstract void processDocumentTrashOp(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentRestored(CoreSession session, DocumentModel doc, DocumentEventContext docCtx)
            throws ClientException;

    protected abstract void processDocumentBeforeRestore(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx) throws ClientException;

    protected void setSystemContextData(DocumentModel doc) {
        // do not send notifications
        doc.putContextData(DISABLE_NOTIFICATION_SERVICE, true);
        doc.putContextData(DISABLE_DUBLINCORE_LISTENER, true);
        doc.putContextData(DISABLE_AUDIT_LOGGER, true);
        doc.putContextData(DISABLE_DOMAIN_LISTENER, true);
        doc.putContextData(DISABLE_HTMLSANITIZER_LISTENER, true);
        doc.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
        // force no versioning after quota modifications
        doc.putContextData(VersioningService.VERSIONING_OPTION, VersioningOption.NONE);
    }
}
