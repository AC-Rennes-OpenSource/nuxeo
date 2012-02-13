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
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.template.ContentInputType;
import org.nuxeo.ecm.platform.template.InputType;
import org.nuxeo.ecm.platform.template.TemplateInput;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.adapters.source.TemplateSourceDocument;
import org.nuxeo.ecm.platform.template.processors.xdocreport.ZipXmlHelper;
import org.nuxeo.ecm.platform.template.service.TemplateProcessorService;
import org.nuxeo.runtime.api.Framework;

public class TestProcessingWithFileNote extends SQLRepositoryTestCase {

    private DocumentModel templateDoc;

    private DocumentModel testDoc;

    private static final Log log = LogFactory.getLog(TestProcessingWithFileNote.class);

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
        deployBundle("org.nuxeo.ecm.platform.convert");
        deployBundle("org.nuxeo.ecm.platform.preview");
        deployBundle("org.nuxeo.ecm.platform.dublincore");
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/core-types-contrib.xml");
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/life-cycle-contrib.xml");
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/adapter-contrib.xml");
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/templateprocessor-service.xml");
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/templateprocessor-contrib.xml");
        openSession();

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

        templateDoc = session.createDocument(templateDoc);

        // create the note
        testDoc = session.createDocumentModel(root.getPathAsString(),
                "testDoc", "Note");
        testDoc.setProperty("dublincore", "title", "MyTestNote2");
        testDoc.setProperty("dublincore", "description", "Simple note sample");

        //File mdfile = FileUtils.getResourceFileFromContext("data/MDSample.md");
        File mdfile = FileUtils.getResourceFileFromContext("data/debug.md");
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

    @Override
    public void tearDown() {
        closeSession();
    }

    public void testNoteWithMasterTemplate() throws Exception {

        setupTestDocs();

        // check the template

        TemplateSourceDocument source = templateDoc.getAdapter(TemplateSourceDocument.class);
        assertNotNull(source);

        // init params
        source.initTemplate(true);

        List<TemplateInput> params = source.getParams();
        System.out.println(params);
        assertEquals(1, params.size());
        // assertEquals(InputType.PictureProperty, params.get(0).getType());
        // assertEquals(InputType.Include, params.get(1).getType());

        // Set params value
        // params.get(0).setType(InputType.PictureProperty);
        // params.get(0).setSource("files:files/0/file");
        params.get(0).setType(InputType.Content);
        params.get(0).setSource(ContentInputType.HtmlPreview.getValue());

        templateDoc = source.saveParams(params, true);

        // associate Note to template
        TemplateBasedDocument templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNull(templateBased);
        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        assertNotNull(tps);
        testDoc = tps.makeTemplateBasedDocument(testDoc, templateDoc, true);
        templateBased = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templateBased);

        // associate to template
        // templateBased.setTemplate(templateDoc, true);

        // render
        testDoc = templateBased.initializeFromTemplate(true);
        Blob blob = templateBased.renderWithTemplate();
        assertNotNull(blob);

        assertEquals("MyTestNote2.odt", blob.getFilename());

        String xmlContent = ZipXmlHelper.readXMLContent(blob,
                ZipXmlHelper.OOO_MAIN_FILE);

        // verify that note content has been merged in ODT
        //assertTrue(xmlContent.contains("TemplateBasedDocument"));
        //assertTrue(xmlContent.contains(testDoc.getTitle()));

        File testFile = new File ("/tmp/testOOo.odt");
        blob.transferTo(testFile);

    }

}
