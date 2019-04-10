/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.ecm.platform.media.streaming;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static org.nuxeo.ecm.platform.media.convert.ConverterConstants.STREAMABLE_MEDIA_CONVERTER_NAME;
import static org.nuxeo.ecm.platform.media.streaming.MediaStreamingConstants.STREAM_MEDIA_FIELD;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.DocumentBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Generate a streamable version of the media and it to the document.
 *
 * @author "<a href=\"mailto:bjalon@nuxeo.com\">Benjamin JALON</a>"
 */
public class MediaStreamingUpdaterListener implements PostCommitEventListener {

    protected static final Log log = LogFactory.getLog(MediaStreamingUpdaterListener.class);

    protected MediaStreamingService mediaStreamingService;

    protected ConversionService conversionService;

    protected MediaStreamingService getMediaStreamingService()
            throws ClientException {
        if (mediaStreamingService == null) {
            try {
                mediaStreamingService = Framework.getService(MediaStreamingService.class);
            } catch (Exception e) {
                throw new ClientException(e);
            }
        }
        return mediaStreamingService;
    }

    protected ConversionService getConversionService() throws ClientException {
        if (conversionService == null) {
            try {
                conversionService = Framework.getService(ConversionService.class);
            } catch (Exception e) {
                throw new ClientException(e);
            }
        }

        return conversionService;
    }

    @Override
    public void handleEvent(EventBundle events) throws ClientException {

        if (!getMediaStreamingService().isServiceActivated()) {
            log.debug("Streaming service not activated");
            return;
        }
        log.debug("Streaming service activated");

        if (!events.containsEventName(DOCUMENT_CREATED)
                && !events.containsEventName(DOCUMENT_UPDATED)) {
            log.debug("Nothing to do, not a creation or modification event");
            return;
        }
        for (Event event : events) {
            if (DOCUMENT_CREATED.equals(event.getName())
                    || DOCUMENT_UPDATED.equals(event.getName())) {
                handleEvent(event);
            }
        }
    }

    public void handleEvent(Event event) throws ClientException {

        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            log.debug("Nothing to do, not a documentEvent");
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        CoreSession session = docCtx.getCoreSession();
        if (session == null) {
            log.error("Can not generate streamable blob since session is null");
            return;
        }

        try {
            DocumentModel doc = docCtx.getSourceDocument();

            if (!getMediaStreamingService().isStreamableMedia(doc)) {
                log.debug("Nothing to do, not a streamable media");
                return;
            }

            if (doc.getPropertyValue(STREAM_MEDIA_FIELD) != null) {
                log.debug("Nothing to do, stream already generated");
                return;
            }

            log.debug("Try to generate the streamable media");
            doc = generateStreamableVideo(session, doc);

            session.saveDocument(doc);
            session.save();
        } catch (ClientException e) {
            log.error("Streamable blob generation aborted", e);
        }
    }

    protected DocumentModel generateStreamableVideo(CoreSession session,
            DocumentModel doc) throws ClientException {

        BlobHolder blobHolder = new DocumentBlobHolder(doc, "file:content");

        BlobHolder result = null;
        try {
            result = getConversionService().convert(
                    STREAMABLE_MEDIA_CONVERTER_NAME, blobHolder, null);
        } catch (ConversionException e) {
            throw new ClientException(e);
        } catch (ClientException e) {
            throw new ClientException(e);
        }

        DocumentModel docReFetched = session.getDocument(doc.getRef());
        docReFetched.setPropertyValue(STREAM_MEDIA_FIELD,
                (Serializable) result.getBlobs().get(0));

        return docReFetched;
    }

}
