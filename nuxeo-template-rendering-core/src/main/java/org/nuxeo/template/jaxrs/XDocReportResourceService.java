package org.nuxeo.template.jaxrs;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.automation.server.jaxrs.io.JsonWriter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.blob.InputStreamBlob;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;
import org.nuxeo.template.processors.xdocreport.FieldDefinitionGenerator;

import fr.opensagres.xdocreport.remoting.resources.domain.BinaryData;
import fr.opensagres.xdocreport.remoting.resources.domain.Filter;
import fr.opensagres.xdocreport.remoting.resources.domain.Resource;
import fr.opensagres.xdocreport.remoting.resources.services.rest.JAXRSResourcesService;

public class XDocReportResourceService extends AbstractResourceService
        implements JAXRSResourcesService {

    protected static final Log log = LogFactory.getLog(XDocReportResourceService.class);

    public XDocReportResourceService(CoreSession session) {
        super(session);
    }

    public Resource getRoot(Filter filter) {
        return getRoot();
    }

    public List<BinaryData> download(List<String> resourcePaths) {
        return null;
    }

    public String getName() {
        return "Nuxeo-Repository";
    }

    public Resource getRoot() {
        Resource root = new Resource();
        root.setType(Resource.FOLDER_TYPE);
        root.setName("Nuxeo");
        List<Resource> children = new ArrayList<Resource>();
        List<TemplateSourceDocument> templates = getTemplates();
        for (TemplateSourceDocument template : templates) {
            children.add(ResourceWrapper.wrap(template));
        }
        root.setChildren(children);
        return root;
    }

    public BinaryData download(String resourcePath) {

        List<TemplateSourceDocument> templates = getTemplates();
        for (TemplateSourceDocument template : templates) {
            if (template.getName().equals(resourcePath)
                    || template.getId().equals(resourcePath)) {
                try {
                    return BinaryDataWrapper.wrap(template.getTemplateBlob());
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @GET
    @Path("model/list")
    public String listModels() throws Exception {
        SchemaManager sm = Framework.getLocalService(SchemaManager.class);
        DocumentType[] docTypes = sm.getDocumentTypes();
        List<String> names = new ArrayList<String>();

        for (DocumentType dt : docTypes) {
            names.add(dt.getName());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        JsonGenerator gen = JsonWriter.createGenerator(out);
        gen.writeObject(names);

        return out.toString();
    }

    @GET
    @Path("model/{type}")
    public String getFieldDefinition(@PathParam("type")
    String type) {
        try {
            return FieldDefinitionGenerator.generate(type);
        } catch (Exception e) {
            log.error("Error during field xml definition generation", e);
            return e.getMessage();
        }
    }

    @Override
    public void upload(BinaryData dataIn) {
        String id = dataIn.getResourceId();
        if (id != null) {
            IdRef ref = new IdRef(id);
            try {
                DocumentModel target = session.getDocument(ref);
                TemplateSourceDocument template = target.getAdapter(TemplateSourceDocument.class);
                if (template != null) {
                    Blob oldBlob = template.getTemplateBlob();

                    Blob newBlob = new InputStreamBlob(dataIn.getStream());

                    // make stream resettable
                    newBlob = newBlob.persist();
                    newBlob.setFilename(oldBlob.getFilename());
                    newBlob.setMimeType(oldBlob.getMimeType());

                    template.setTemplateBlob(newBlob, true);
                }
            } catch (Exception e) {
                log.error("Error during template upload", e);
            }
        }
    }

}
