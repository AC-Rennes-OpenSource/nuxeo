package org.nuxeo.ecm.platform.template.tests;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.convert.ooomanager.OOoManagerService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.template.ContentInputType;
import org.nuxeo.template.InputType;
import org.nuxeo.template.TemplateInput;
import org.nuxeo.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.template.adapters.source.TemplateSourceDocument;
import org.nuxeo.template.adapters.source.TemplateSourceDocumentAdapterImpl;
import org.nuxeo.template.processors.convert.ConvertHelper;
import org.nuxeo.template.service.TemplateProcessorService;

public class TestODTProcessingWithConverter extends SQLRepositoryTestCase {

    private DocumentModel templateDoc;

    private DocumentModel testDoc;

    private static final Log log = LogFactory.getLog(TestODTProcessingWithConverter.class);

    protected OOoManagerService oooManagerService;

    protected static final String TEMPLATE_NAME = "mytestTemplate";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.core.api");
        deployBundle("org.nuxeo.ecm.core");
        deployBundle("org.nuxeo.ecm.core.schema");
        deployBundle("org.nuxeo.ecm.core.event");
        deployBundle("org.nuxeo.ecm.core.convert.api");
        deployBundle("org.nuxeo.ecm.platform.mimetype.api");
        deployBundle("org.nuxeo.ecm.platform.mimetype.core");
        deployBundle("org.nuxeo.ecm.core.convert");
        deployBundle("org.nuxeo.ecm.core.convert.plugins");
        deployBundle("org.nuxeo.ecm.platform.convert");
        deployBundle("org.nuxeo.ecm.platform.preview");
        deployBundle("org.nuxeo.ecm.platform.dublincore");
        deployContrib("org.nuxeo.template.manager",
                "OSGI-INF/core-types-contrib.xml");
        deployContrib("org.nuxeo.template.manager",
                "OSGI-INF/life-cycle-contrib.xml");
        deployContrib("org.nuxeo.template.manager",
                "OSGI-INF/adapter-contrib.xml");
        deployContrib("org.nuxeo.template.manager",
                "OSGI-INF/templateprocessor-service.xml");
        deployContrib("org.nuxeo.template.manager",
                "OSGI-INF/templateprocessor-contrib.xml");
        openSession();

        oooManagerService = Framework.getService(OOoManagerService.class);
        try {
            oooManagerService.startOOoManager();
        } catch (Exception e) {
            log.warn("Can't run OpenOffice, JOD converter will not be available.");
        }

    }

    @Override
    public void tearDown() throws Exception {
        oooManagerService = Framework.getService(OOoManagerService.class);
        if (oooManagerService.isOOoManagerStarted()) {
            oooManagerService.stopOOoManager();
        }
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.waitForAsyncCompletion();
        closeSession();
        super.tearDown();
    }

    protected void setupTestDocs() throws Exception {

        DocumentModel root = session.getRootDocument();

        // create the template
        templateDoc = session.createDocumentModel(root.getPathAsString(),
                "templatedDoc", "TemplateSource");
        templateDoc.setProperty("dublincore", "title", "MyTemplate");
        File file = FileUtils.getResourceFileFromContext("data/Container.odt");
        Blob fileBlob = new FileBlob(file);
        fileBlob.setFilename("Container.odt");
        templateDoc.setProperty("file", "content", fileBlob);
        templateDoc.setPropertyValue("tmpl:templateName", TEMPLATE_NAME);

        templateDoc = session.createDocument(templateDoc);

        // create the note
        testDoc = session.createDocumentModel(root.getPathAsString(),
                "testDoc", "Note");
        testDoc.setProperty("dublincore", "title", "MyTestNote2");
        testDoc.setProperty("dublincore", "description", "Simple note sample");

        File mdfile = FileUtils.getResourceFileFromContext("data/MDSample.md");
        Blob mdfileBlob = new FileBlob(mdfile);

        testDoc.setPropertyValue("note:note", mdfileBlob.getString());
        testDoc.setPropertyValue("note:mime_type", "text/x-web-markdown");

        File imgFile = FileUtils.getResourceFileFromContext("data/android.jpg");
        Blob imgBlob = new FileBlob(imgFile);
        imgBlob.setFilename("android.jpg");
        imgBlob.setMimeType("image/jpeg");

        List<Map<String, Serializable>> blobs = new ArrayList<Map<String, Serializable>>();
        Map<String, Serializable> blob1 = new HashMap<String, Serializable>();
        blob1.put("file", (Serializable) imgBlob);
        blob1.put("filename", "android.jpg");
        blobs.add(blob1);

        testDoc.setPropertyValue("files:files", (Serializable) blobs);

        testDoc = session.createDocument(testDoc);
    }

    public void testNoteWithMasterTemplateAndConverter() throws Exception {

        if (!oooManagerService.isOOoManagerStarted()) {
            log.info("Skipping test since no OOo server can be found");
            return;
        }

        setupTestDocs();

        // check the template

        TemplateSourceDocument source = templateDoc.getAdapter(TemplateSourceDocument.class);
        assertNotNull(source);

        // init params
        source.initTemplate(true);

        List<TemplateInput> params = source.getParams();
        // System.out.println(params);
        assertEquals(1, params.size());

        params.get(0).setType(InputType.Content);
        params.get(0).setSource(ContentInputType.HtmlPreview.getValue());

        templateDoc = source.saveParams(params, true);

        // test Converter
        templateDoc.setPropertyValue(
                TemplateSourceDocumentAdapterImpl.TEMPLATE_OUTPUT_PROP,
                "application/pdf");
        templateDoc = session.saveDocument(templateDoc);
        session.save();

        // associate Note to template
        TemplateBasedDocument templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNull(templateBased);
        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        assertNotNull(tps);
        testDoc = tps.makeTemplateBasedDocument(testDoc, templateDoc, true);
        templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templateBased);

        // render
        testDoc = templateBased.initializeFromTemplate(TEMPLATE_NAME, true);
        Blob blob = templateBased.renderWithTemplate(TEMPLATE_NAME);
        assertNotNull(blob);

        assertEquals("MyTestNote2.pdf", blob.getFilename());

        ConvertHelper helper = new ConvertHelper();
        Blob txtBlob = helper.convertBlob(blob, "text/plain");
        String txtContent = txtBlob.getString();

        // System.out.println(txtContent);

        assertTrue(txtContent.contains("TemplateBasedDocument"));
        assertTrue(txtContent.contains(testDoc.getTitle()));

    }

}
