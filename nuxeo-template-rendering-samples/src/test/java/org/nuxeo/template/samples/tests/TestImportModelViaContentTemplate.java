/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.template.samples.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.content.template", //
    "org.nuxeo.template.manager.api", //
    "org.nuxeo.template.manager", //
    "org.nuxeo.template.manager.jaxrs", //
    "org.nuxeo.template.manager.samples", //
    "org.nuxeo.ecm.core.io", //
    "studio.extensions.template-module-demo", //
})
public class TestImportModelViaContentTemplate {

  @Inject
  protected CoreSession session;

  DocumentModel rootDocument;

  DocumentModel workspace;

  DocumentModel docToExport;

  @Test
  public void testImportContentTemplateArchive() throws Exception {

    // check result

    StringBuffer sb = new StringBuffer();
    DocumentModelList docs = session.query(
        "select * from Document where ecm:mixinType in ('Template','TemplateBased') order by ecm:path");
    for (DocumentModel doc : docs) {
      sb.append("path: " + doc.getPathAsString() + " type: " + doc.getType() + " title:" + doc.getTitle() + " name:"
          + doc.getName() + " uuid:" + doc.getId());
      TemplateBasedDocument templateDoc = doc.getAdapter(TemplateBasedDocument.class);
      if (templateDoc != null) {
        for (String tName : templateDoc.getTemplateNames()) {
          sb.append(" target: " + tName + "-" + templateDoc.getSourceTemplateDocRef(tName));
          assertTrue(session.exists(templateDoc.getSourceTemplateDocRef(tName)));
        }
      } else {
        TemplateSourceDocument source = doc.getAdapter(TemplateSourceDocument.class);
        assertNotNull(source);
      }
      sb.append("\n");
    }

    // String dump = sb.toString();
    // System.out.println("Import completed : " + docs.size() + " docs");
    // System.out.println(dump);

  }

  @Test
  public void testWebTemplateRendering() throws Exception {

    PathRef ref = new PathRef("/default-domain/templates/WebTemplate");
    assertTrue(session.exists(ref));

    DocumentModel webTemplate = session.getDocument(ref);
    TemplateSourceDocument source = webTemplate.getAdapter(TemplateSourceDocument.class);
    assertNotNull(source);

    List<TemplateBasedDocument> using = source.getTemplateBasedDocuments();
    assertNotNull(using);
    assertEquals(1, using.size());

    TemplateBasedDocument note = using.get(0);

    Blob blob = note.renderWithTemplate(source.getName());
    assertNotNull(blob);

    String html = blob.getString();
    assertNotNull(html);

    String targetUrl = "templates/doc/" + note.getAdaptedDoc().getId() + "/resource/" + source.getName() + "/style.css";
    assertTrue(html.contains(targetUrl));

  }
}
