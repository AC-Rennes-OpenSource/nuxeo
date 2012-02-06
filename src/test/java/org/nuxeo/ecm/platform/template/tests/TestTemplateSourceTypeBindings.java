package org.nuxeo.ecm.platform.template.tests;

import java.io.File;
import java.util.Map;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.adapters.source.TemplateSourceDocument;
import org.nuxeo.ecm.platform.template.service.TemplateProcessorService;
import org.nuxeo.runtime.api.Framework;

public class TestTemplateSourceTypeBindings extends SQLRepositoryTestCase {

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

    protected TemplateSourceDocument createTemplateDoc(String name)
            throws Exception {

        DocumentModel root = session.getRootDocument();

        // create template
        DocumentModel templateDoc = session.createDocumentModel(
                root.getPathAsString(), name, "TemplateSource");
        templateDoc.setProperty("dublincore", "title", name);
        File file = FileUtils.getResourceFileFromContext("data/testDoc.odt");
        Blob fileBlob = new FileBlob(file);
        fileBlob.setFilename("testDoc.odt");
        templateDoc.setProperty("file", "content", fileBlob);
        templateDoc = session.createDocument(templateDoc);

        TemplateSourceDocument result = templateDoc.getAdapter(TemplateSourceDocument.class);
        assertNotNull(result);
        return result;
    }

    public void testTypeBindingAndOverride() throws Exception {

        // test simple mapping
        TemplateSourceDocument t1 = createTemplateDoc("t1");
        t1.setForcedTypes(new String[] { "File", "Note" }, true);

        assertTrue(t1.getForcedTypes().contains("File"));
        assertTrue(t1.getForcedTypes().contains("Note"));

        session.save();

        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);

        Map<String, String> mapping = tps.getTypeMapping();

        assertEquals(t1.getAdaptedDoc().getId(), mapping.get("File"));
        assertEquals(t1.getAdaptedDoc().getId(), mapping.get("Note"));

        // wait for Async listener to run !
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();

        // test override
        TemplateSourceDocument t2 = createTemplateDoc("t2");
        t2.setForcedTypes(new String[] { "Note" }, true);

        assertFalse(t2.getForcedTypes().contains("File"));
        assertTrue(t2.getForcedTypes().contains("Note"));

        session.save();

        // wait for Async listener to run !
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();

        session.save();

        mapping = tps.getTypeMapping();
        assertEquals(t1.getAdaptedDoc().getId(), mapping.get("File"));
        assertEquals(t2.getAdaptedDoc().getId(), mapping.get("Note"));

        // check update on initial template
        // refetch staled DocumentModel
        t1 = session.getDocument(new IdRef(t1.getAdaptedDoc().getId())).getAdapter(
                TemplateSourceDocument.class);
        assertTrue(t1.getForcedTypes().contains("File"));
        assertFalse(t1.getForcedTypes().contains("Note"));
    }

    public void testAutomaticTemplateBinding() throws Exception {

        // create a template and a simple mapping
        TemplateSourceDocument t1 = createTemplateDoc("t1");
        t1.setForcedTypes(new String[] { "File" }, true);
        assertTrue(t1.getForcedTypes().contains("File"));
        session.save();

        // wait for Async listener to run !
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();

        // now create a simple file
        DocumentModel root = session.getRootDocument();
        DocumentModel simpleFile = session.createDocumentModel(
                root.getPathAsString(), "myTestFile", "File");
        simpleFile = session.createDocument(simpleFile);

        // verify that template has been associated
        TemplateBasedDocument templatizedFile = simpleFile.getAdapter(TemplateBasedDocument.class);
        assertNotNull(templatizedFile);

        // remove binding
        t1.setForcedTypes(new String[] {}, true);
        session.save();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();

        // now create a simple file
        DocumentModel simpleFile2 = session.createDocumentModel(
                root.getPathAsString(), "myTestFile2", "File");
        simpleFile2 = session.createDocument(simpleFile2);

        // verify that template has NOT been associated
        assertNull(simpleFile2.getAdapter(TemplateBasedDocument.class));

    }

    public void testManualTemplateBinding() throws Exception {

        // create a template and no mapping
        TemplateSourceDocument t1 = createTemplateDoc("t1");
        session.save();

        // now create a simple Note
        DocumentModel root = session.getRootDocument();
        DocumentModel simpleNote = session.createDocumentModel(
                root.getPathAsString(), "myTestFile", "Note");
        simpleNote = session.createDocument(simpleNote);

        // verify that not template is associated
        assertNull(simpleNote.getAdapter(TemplateBasedDocument.class));

        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        simpleNote = tps.makeTemplateBasedDocument(simpleNote,
                t1.getAdaptedDoc(), true);

        // verify that template has been associated
        assertNotNull(simpleNote.getAdapter(TemplateBasedDocument.class));

    }

    @Override
    public void tearDown() {
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.waitForAsyncCompletion();
        closeSession();
    }

}
