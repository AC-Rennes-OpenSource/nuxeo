/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *      Nelson Silva
 */
package org.nuxeo.ecm.liveconnect.google.drive.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.runtime.api.Framework;

/**
 * Converter that relies on {@link org.nuxeo.ecm.core.blob.BlobProvider} conversions.
 *
 * @since 7.3
 */
public class GoogleDriveBlobConverter implements Converter {

    protected ConverterDescriptor descriptor;

    @Override
    public void init(ConverterDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {

        Blob srcBlob, dstBlob;

        srcBlob = blobHolder.getBlob();
        if (srcBlob == null) {
            return null;
        }

        try {
            dstBlob = convert(srcBlob);
        } catch (IOException e) {
            throw new ConversionException("Unable to fetch conversion", e);
        }

        if (dstBlob == null) {
            return null;
        }

        return new SimpleCachableBlobHolder(dstBlob);
    }

    protected Blob convert(Blob blob) throws IOException {
        String mimetype = descriptor.getDestinationMimeType();
        InputStream is = Framework.getService(BlobManager.class).getConvertedStream(blob, mimetype);
        return is == null ? null : Blobs.createBlob(is, mimetype);
    }
}
