/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume RENARD
 */
package org.nuxeo.retention.listeners;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.retention.RetentionConstants;
import org.nuxeo.retention.adapters.Record;
import org.nuxeo.retention.service.RetentionManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 11.1
 */
public class RetentionExpiredListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        final String eventId = event.getName();

        final DocumentEventContext docCxt = (DocumentEventContext) event.getContext();

        DocumentModel doc = null;
        if (eventId.equals(DocumentEventTypes.RETENTION_EXPIRED)) {
            doc = docCxt.getSourceDocument();
        } else {
            return;
        }
        if (!doc.hasFacet(RetentionConstants.RECORD_FACET)) {
            return;
        }
        Record record = doc.getAdapter(Record.class);
        RetentionManager retentionManager = Framework.getService(RetentionManager.class);
        retentionManager.executeRuleEndActions(record, ctx.getCoreSession());
    }


}