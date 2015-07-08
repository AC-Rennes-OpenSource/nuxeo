package org.nuxeo.template.processors.xslt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob;
import org.nuxeo.template.api.TemplateInput;
import org.nuxeo.template.api.TemplateProcessor;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.processors.AbstractTemplateProcessor;

public class XSLTProcessor extends AbstractTemplateProcessor implements TemplateProcessor {

    @Override
    public Blob renderTemplate(TemplateBasedDocument templateBasedDocument, String templateName) {

        BlobHolder bh = templateBasedDocument.getAdaptedDoc().getAdapter(BlobHolder.class);
        if (bh == null) {
            return null;
        }

        Blob xmlContent = bh.getBlob();
        if (xmlContent == null) {
            return null;
        }

        Blob sourceTemplateBlob = getSourceTemplateBlob(templateBasedDocument, templateName);

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tFactory.newTransformer(new StreamSource(sourceTemplateBlob.getStream()));
        } catch (TransformerConfigurationException | IOException e) {
            throw new NuxeoException(e);
        }
        transformer.setErrorListener(new ErrorListener() {

            @Override
            public void warning(TransformerException exception) throws TransformerException {
                log.warn("Problem during transformation", exception);
            }

            @Override
            public void fatalError(TransformerException exception) throws TransformerException {
                log.error("Fatal error during transformation", exception);
            }

            @Override
            public void error(TransformerException exception) throws TransformerException {
                log.error("Error during transformation", exception);
            }
        });
        transformer.setURIResolver(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            transformer.transform(new StreamSource(xmlContent.getStream()), new StreamResult(out));
        } catch (TransformerException | IOException e) {
            throw new NuxeoException(e);
        }

        Blob result = new ByteArrayBlob(out.toByteArray(), "text/xml");
        String targetFileName = FileUtils.getFileNameNoExt(templateBasedDocument.getAdaptedDoc().getTitle());
        // result.setFilename(targetFileName + ".xml");
        result.setFilename(targetFileName + ".html");

        return result;

    }

    @Override
    public List<TemplateInput> getInitialParametersDefinition(Blob blob) {
        return new ArrayList<TemplateInput>();
    }

}
