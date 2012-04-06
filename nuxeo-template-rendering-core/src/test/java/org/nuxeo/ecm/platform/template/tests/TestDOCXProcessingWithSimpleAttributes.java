package org.nuxeo.ecm.platform.template.tests;

import java.io.File;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.processors.xdocreport.ZipXmlHelper;

public class TestDOCXProcessingWithSimpleAttributes extends
        SimpleTemplateDocTestCase {

    public void testDocumentsAttributes() throws Exception {
        TemplateBasedDocument adapter = setupTestDocs();
        DocumentModel testDoc = adapter.getAdaptedDoc();
        assertNotNull(testDoc);

        Blob newBlob = adapter.renderAndStoreAsAttachment(TEMPLATE_NAME, true);

        // System.out.println(((FileBlob) newBlob).getFile().getAbsolutePath());

        String xmlContent = ZipXmlHelper.readXMLContent(newBlob,
                ZipXmlHelper.DOCX_MAIN_FILE);

        assertTrue(xmlContent.contains("Subject 1"));
        assertTrue(xmlContent.contains("Subject 2"));
        assertTrue(xmlContent.contains("Subject 3"));
        assertTrue(xmlContent.contains("MyTestDoc"));
        assertTrue(xmlContent.contains("Administrator"));

    }

    @Override
    protected Blob getTemplateBlob() {
        File file = FileUtils.getResourceFileFromContext("data/DocumentsAttributes.docx");
        Blob fileBlob = new FileBlob(file);
        fileBlob.setFilename("DocumentsAttributes.odt");
        return fileBlob;
    }

}
