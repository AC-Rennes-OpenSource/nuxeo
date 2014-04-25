package org.nuxeo.segment.io.web;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.segment.io.SegmentIO;

import com.github.segmentio.models.Providers;

@WebObject(type = "segmentIOScriptResource")
@Path("/segmentIO")
public class SegmentIOScriptResource extends ModuleRoot {

    @GET
    public Object anonymous() {
        return buildScript(null);
    }

    @GET
    @Path("user/{login}")
    public Object signed(@PathParam("login") String login) {
        return buildScript(login);
    }

    protected String buildJsonProvidersOptions() {
        SegmentIO segmentIO = Framework.getLocalService(SegmentIO.class);
        Providers providers = segmentIO.getProviders();
        StringBuffer json = new StringBuffer("{");
        for (String pname : providers.keySet()) {
            json.append(pname);
            json.append(" : ");
            json.append(providers.get(pname).toString());
            json.append(" , ");
        }
        json.append("}");
        return json.toString();
    }

    protected Object buildScript(String login) {

        SegmentIO segmentIO = Framework.getLocalService(SegmentIO.class);

        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("writeKey", segmentIO.getWriteKey());

        NuxeoPrincipal principal = (NuxeoPrincipal) getContext().getPrincipal();

        if (principal!=null) {
            if (login!=null && principal.getName().equals(login)) {
                ctx.put("principal", principal);
            }
        }
        ctx.put("providers", buildJsonProvidersOptions());
        return getView("script").args(ctx);
    }
}
