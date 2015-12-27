/*
 * (C) Copyright 2006-2010 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.diff.content.adapter.factories;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.diff.content.ContentDiffAdapter;
import org.nuxeo.ecm.diff.content.adapter.ContentDiffAdapterFactory;
import org.nuxeo.ecm.diff.content.adapter.base.ConverterBasedContentDiffAdapter;

/**
 * Content diff adapter factory for all documents that have a blob holder adapter.
 *
 * @author Antoine Taillefer
 * @since 5.6
 */
public class BlobHolderContentDiffAdapterFactory implements ContentDiffAdapterFactory {

    public ContentDiffAdapter getAdapter(DocumentModel doc) {
        ConverterBasedContentDiffAdapter adapter = new ConverterBasedContentDiffAdapter();
        adapter.setAdaptedDocument(doc);
        return adapter;
    }

}
