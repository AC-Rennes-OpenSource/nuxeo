/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 *
 */

package org.nuxeo.ecm.platform.routing.core.audit.es;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.elasticsearch.audit.ESAuditBackend;
import org.nuxeo.elasticsearch.http.readonly.filter.AuditRequestFilter;
import org.nuxeo.runtime.api.Framework;

public class RoutingAuditRequestFilter extends AuditRequestFilter {

    private CoreSession session;

    public void init(CoreSession session, String indices, String types, String rawQuery, String payload) {
        this.session = session;
        this.principal = (NuxeoPrincipal) session.getPrincipal();
        this.indices = ESAuditBackend.IDX_NAME;
        this.types = ESAuditBackend.IDX_TYPE;
        this.rawQuery = rawQuery;
        this.payload = payload;
        if (payload == null && !principal.isAdministrator()) {
            // here we turn the UriSearch query_string into a body search
            extractPayloadFromQuery();
        }
    }

    @Override
    public String getPayload() throws JSONException {
        if (principal.isAdministrator()) {
            return payload;
        }
        if (filteredPayload == null) {
            if (payload.contains("\\")) {
                // JSONObject removes backslash so we need to hide them
                payload = payload.replaceAll("\\\\", BACKSLASH_MARKER);
            }
            JSONObject payloadJson = new JSONObject(payload);
            JSONObject query;
            if (payloadJson.has("query")) {
                query = payloadJson.getJSONObject("query");

                payloadJson.remove("query");
            } else {
                query = new JSONObject("{\"match_all\":{}}");
            }
            JSONObject categoryFilter = new JSONObject().put("term", new JSONObject().put(
                    DocumentEventContext.CATEGORY_PROPERTY_KEY, DocumentRoutingConstants.ROUTING_CATEGORY));

            DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
            List<DocumentRoute> wfModels = documentRoutingService.getAvailableDocumentRouteModel(session);
            List<String> modelNames = new ArrayList<String>();
            for (DocumentRoute model : wfModels) {
                if (session.hasPermission(model.getDocument().getRef(), DocumentRoutingConstants.CAN_DATA_VISU)) {
                    modelNames.add(model.getModelName());
                }
            }

            JSONObject wfModelFilter = new JSONObject().put("terms", new JSONObject().put(
                    "extended.modelName", modelNames.toArray(new String[modelNames.size()])));

            JSONArray fs = new JSONArray().put(categoryFilter).put(wfModelFilter);

            JSONObject filter = new JSONObject().put("bool", new JSONObject().put("must",fs));

            JSONObject newQuery = new JSONObject().put("filtered",
                    new JSONObject().put("query", query).put("filter", filter));
            payloadJson.put("query", newQuery);
            filteredPayload = payloadJson.toString();
            if (filteredPayload.contains(BACKSLASH_MARKER)) {
                filteredPayload = filteredPayload.replaceAll(BACKSLASH_MARKER, "\\\\");
            }

        }
        return filteredPayload;
    }

}
