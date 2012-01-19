/*
 * (C) Copyright 2006-20012 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.platform.template.listeners;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_CREATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.template.TemplateInput;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocumentAdapterImpl;
import org.nuxeo.ecm.platform.template.adapters.source.TemplateSourceDocument;
import org.nuxeo.ecm.platform.template.service.TemplateProcessorService;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener to manage initialization :
 *
 * <ul>
 *   <li> of the TemplateSourceDocument : init the parameters </li>
 *   <li> of the other DocumentModels if they need to be automatically associated to a template </li>
 * </ul>
 *
 * @author Tiry (tdelprat@nuxeo.com)
 *
 */
public class TemplateInitListener implements EventListener {

    private static final Log log = LogFactory.getLog(TemplateInitListener.class);

    public void handleEvent(Event event) throws ClientException {

        EventContext ctx = event.getContext();

        if (ABOUT_TO_CREATE.equals(event.getName()) || BEFORE_DOC_UPDATE.equals(event.getName()) )
        {
            if (ctx instanceof DocumentEventContext) {
                DocumentEventContext docCtx = (DocumentEventContext) ctx;

                DocumentModel targetDoc = docCtx.getSourceDocument();

                if (targetDoc.isVersion()) {
                    return ;
                }

                TemplateSourceDocument templateDoc = targetDoc.getAdapter(TemplateSourceDocument.class);
                if (templateDoc!=null) {
                    // init template source
                    List<TemplateInput> params = templateDoc.getParams();
                    if (params==null || params.size()==0) {
                        try {
                            templateDoc.initTemplate(false);
                        } catch (Exception e) {
                            log.error("Error during parameter automatic initialization", e);
                        }
                    }
                } else {
                    TemplateBasedDocument tmplBased = targetDoc.getAdapter(TemplateBasedDocument.class);
                    if (tmplBased==null) {
                        // if not templateBased see if we must add the facet because of the type binding
                        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
                        String targetTemplateUid = tps.getTypeMapping().get(targetDoc.getType());
                        if (targetTemplateUid!=null) {
                            DocumentRef templateRef = new IdRef(targetTemplateUid);
                            // check if source template is visible
                            if (docCtx.getCoreSession().exists(templateRef)) {
                                DocumentModel sourceTemplateDoc =docCtx.getCoreSession().getDocument(templateRef);
                                // add the facet
                                targetDoc.addFacet(TemplateBasedDocumentAdapterImpl.TEMPLATEBASED_FACET);
                                tmplBased = targetDoc.getAdapter(TemplateBasedDocument.class);
                                // bind the template
                                tmplBased.setTemplate(sourceTemplateDoc, false);
                            }
                        }
                    }
                }

            }
        }
    }
}
