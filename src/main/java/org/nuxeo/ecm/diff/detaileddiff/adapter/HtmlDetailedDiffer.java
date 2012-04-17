/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Alexandre Russel
 *
 * $Id$
 */

package org.nuxeo.ecm.diff.detaileddiff.adapter;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.diff.detaileddiff.DetailedDiffException;
import org.outerj.daisy.diff.HtmlCleaner;
import org.outerj.daisy.diff.XslFilter;
import org.outerj.daisy.diff.html.HTMLDiffer;
import org.outerj.daisy.diff.html.HtmlSaxDiffOutput;
import org.outerj.daisy.diff.html.TextNodeComparator;
import org.outerj.daisy.diff.html.dom.DomTreeBuilder;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author Antoine Taillefer
 * @since 5.6
 */
public class HtmlDetailedDiffer implements MimeTypeDetailedDiffer {

    public List<Blob> getDetailedDiff(Blob leftBlob, Blob rightBlob,
            DocumentModel leftDoc, DocumentModel rightDoc)
            throws DetailedDiffException {

        try {
            // TODO: check StringBlob, throw exception if not

            List<Blob> blobResults = new ArrayList<Blob>();
            StringWriter sw = new StringWriter();

            SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactory.newInstance();
            TransformerHandler transformHandler = stf.newTransformerHandler();
            transformHandler.setResult(new StreamResult(sw));

            XslFilter htmlHeaderXslFilter = new XslFilter();

            ContentHandler postProcess = htmlHeaderXslFilter.xsl(
                    transformHandler, "xslfilter/htmlheader.xsl");

            // TODO: use Seam locale
            Locale locale = Locale.getDefault();
            String prefix = "diff";

            HtmlCleaner cleaner = new HtmlCleaner();

            InputSource leftIS = new InputSource(leftBlob.getStream());
            InputSource rightIS = new InputSource(rightBlob.getStream());

            DomTreeBuilder leftHandler = new DomTreeBuilder();
            cleaner.cleanAndParse(leftIS, leftHandler);
            TextNodeComparator leftComparator = new TextNodeComparator(
                    leftHandler, locale);

            DomTreeBuilder rightHandler = new DomTreeBuilder();
            cleaner.cleanAndParse(rightIS, rightHandler);
            TextNodeComparator rightComparator = new TextNodeComparator(
                    rightHandler, locale);

            postProcess.startDocument();
            postProcess.startElement("", "diffreport", "diffreport",
                    new AttributesImpl());
            postProcess.startElement("", "diff", "diff", new AttributesImpl());
            HtmlSaxDiffOutput output = new HtmlSaxDiffOutput(postProcess,
                    prefix);

            HTMLDiffer differ = new HTMLDiffer(output);
            differ.diff(leftComparator, rightComparator);

            postProcess.endElement("", "diff", "diff");
            postProcess.endElement("", "diffreport", "diffreport");
            postProcess.endDocument();

            Blob mainBlob = new StringBlob(sw.toString());
            sw.close();

            mainBlob.setFilename("detailedDiff.html");
            mainBlob.setMimeType("text/html");

            blobResults.add(mainBlob);
            return blobResults;

        } catch (Exception e) {
            throw new DetailedDiffException(e);
        }
    }
}
