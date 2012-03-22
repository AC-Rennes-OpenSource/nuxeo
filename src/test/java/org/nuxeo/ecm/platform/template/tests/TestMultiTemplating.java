package org.nuxeo.ecm.platform.template.tests;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.processors.xdocreport.ZipXmlHelper;
import org.nuxeo.ecm.platform.template.service.TemplateProcessorService;
import org.nuxeo.runtime.api.Framework;

public class TestMultiTemplating extends SQLRepositoryTestCase {

    protected DocumentModel odtTemplateDoc;

    protected DocumentModel ftlTemplateDoc;

    protected DocumentModel testDoc;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.core.api");
        deployBundle("org.nuxeo.ecm.core");
        deployBundle("org.nuxeo.ecm.core.schema");
        deployBundle("org.nuxeo.ecm.core.event");
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
        deployContrib("org.nuxeo.ecm.platform.template.manager",
                "OSGI-INF/listener-contrib.xml");
        openSession();
    }

    @Override
    public void tearDown() {
        closeSession();
    }

    protected Blob getODTTemplateBlob() {
        File file = FileUtils.getResourceFileFromContext("data/DocumentsAttributes.odt");
        Blob fileBlob = new FileBlob(file);
        fileBlob.setFilename("DocumentsAttributes.odt");
        return fileBlob;
    }

    protected Blob getFTLTemplateBlob() {
        File file = FileUtils.getResourceFileFromContext("data/test.ftl");
        Blob fileBlob = new FileBlob(file);
        fileBlob.setFilename("test.ftl");
        return fileBlob;
    }

    protected void setupTestDocs() throws Exception {

        DocumentModel root = session.getRootDocument();

        // create ODT template
        odtTemplateDoc = session.createDocumentModel(root.getPathAsString(),
                "odtTemplatedDoc", "TemplateSource");
        odtTemplateDoc.setProperty("dublincore", "title", "ODT Template");
        odtTemplateDoc.setPropertyValue("tmpl:templateName", "odt");
        Blob fileBlob = getODTTemplateBlob();
        odtTemplateDoc.setProperty("file", "content", fileBlob);
        odtTemplateDoc = session.createDocument(odtTemplateDoc);

        // create FTL template
        ftlTemplateDoc = session.createDocumentModel(root.getPathAsString(),
                "ftlTemplatedDoc", "TemplateSource");
        ftlTemplateDoc.setProperty("dublincore", "title", "FTL Template");
        ftlTemplateDoc.setPropertyValue("tmpl:templateName", "ftl");
        fileBlob = getFTLTemplateBlob();
        ftlTemplateDoc.setProperty("file", "content", fileBlob);
        ftlTemplateDoc = session.createDocument(ftlTemplateDoc);

        // now create simple doc
        testDoc = session.createDocumentModel(root.getPathAsString(),
                "testDoc", "File");
        testDoc.setProperty("dublincore", "title", "MyTestDoc");
        testDoc.setProperty("dublincore", "description", "some description");

        // set dc:subjects
        List<String> subjects = new ArrayList<String>();
        subjects.add("Subject 1");
        subjects.add("Subject 2");
        subjects.add("Subject 3");
        testDoc.setPropertyValue("dc:subjects", (Serializable) subjects);
        testDoc = session.createDocument(testDoc);
    }

    public void testMultiBindings() throws Exception {

        setupTestDocs();

        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);

        // bind the doc to the odt template
        testDoc = tps.makeTemplateBasedDocument(testDoc, odtTemplateDoc, true);

        // check association
        TemplateBasedDocument tbd = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNotNull(tbd);
        assertEquals(1, tbd.getTemplateNames().size());
        assertEquals("odt", tbd.getTemplateNames().get(0));

        // check rendition
        Blob rendered = tbd.renderWithTemplate("odt");
        assertNotNull(rendered);

        String xmlContent = ZipXmlHelper.readXMLContent(rendered,
                ZipXmlHelper.OOO_MAIN_FILE);

        assertTrue(xmlContent.contains("Subject 1"));
        assertTrue(xmlContent.contains("Subject 2"));
        assertTrue(xmlContent.contains("Subject 3"));
        assertTrue(xmlContent.contains("MyTestDoc"));
        assertTrue(xmlContent.contains("Administrator"));

        // bind the doc to FTL template
        testDoc = tbd.setTemplate(ftlTemplateDoc, true);
        assertEquals(2, tbd.getTemplateNames().size());
        assertTrue(tbd.getTemplateNames().contains("odt"));
        assertTrue(tbd.getTemplateNames().contains("ftl"));

        // check Rendition
        Blob renderedHtml = tbd.renderWithTemplate("ftl");
        assertNotNull(renderedHtml);
        String htmlContent = renderedHtml.getString();
        assertTrue(htmlContent.contains(testDoc.getTitle()));
        assertTrue(htmlContent.contains(testDoc.getId()));

        // unbind odt
        testDoc = tps.detachTemplateBasedDocument(testDoc, "odt", true);
        tbd = testDoc.getAdapter(TemplateBasedDocument.class);
        assertEquals(1, tbd.getTemplateNames().size());
        assertEquals("ftl", tbd.getTemplateNames().get(0));

        // unbind ftl
        testDoc = tps.detachTemplateBasedDocument(testDoc, "ftl", true);
        tbd = testDoc.getAdapter(TemplateBasedDocument.class);
        assertNull(tbd);

    }

}
