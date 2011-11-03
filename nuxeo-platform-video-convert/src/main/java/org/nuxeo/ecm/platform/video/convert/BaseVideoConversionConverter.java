/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.platform.video.convert;

import static org.nuxeo.ecm.platform.video.convert.Constants.INPUT_FILE_PATH_PARAMETER;
import static org.nuxeo.ecm.platform.video.convert.Constants.OUTPUT_FILE_PATH_PARAMETER;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolderWithProperties;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.convert.plugins.CommandLineBasedConverter;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
public abstract class BaseVideoConversionConverter extends
        CommandLineBasedConverter {

    @Override
    protected Map<String, Blob> getCmdBlobParameters(BlobHolder blobHolder,
            Map<String, Serializable> stringSerializableMap)
            throws ConversionException {
        Map<String, Blob> cmdBlobParams = new HashMap<String, Blob>();
        try {
            cmdBlobParams.put(INPUT_FILE_PATH_PARAMETER, blobHolder.getBlob());
        } catch (ClientException e) {
            throw new ConversionException("Unable to get Blob for holder", e);
        }
        return cmdBlobParams;
    }

    @Override
    protected Map<String, String> getCmdStringParameters(BlobHolder blobHolder,
            Map<String, Serializable> parameters) throws ConversionException {
        Map<String, String> cmdStringParams = new HashMap<String, String>();

        String baseDir = getTmpDirectory(parameters);
        Path tmpPath = new Path(baseDir).append(getTmpDirectoryPrefix() + "_"
                + System.currentTimeMillis());

        File outDir = new File(tmpPath.toString());
        boolean dirCreated = outDir.mkdir();
        if (!dirCreated) {
            throw new ConversionException(
                    "Unable to create tmp dir for transformer output");
        }

        try {
            String baseName = FilenameUtils.getBaseName(blobHolder.getBlob().getFilename());
            String outFileName = baseName + getVideoExtension();
            cmdStringParams.put(OUTPUT_FILE_PATH_PARAMETER, new File(outDir,
                    outFileName).getAbsolutePath());
            cmdStringParams.put("height",
                    String.valueOf(parameters.get("height")));
            return cmdStringParams;
        } catch (ClientException e) {
            throw new ConversionException("Unable to get Blob for holder", e);
        }
    }

    @Override
    protected BlobHolder buildResult(List<String> cmdOutput,
            CmdParameters cmdParameters) throws ConversionException {
        String outputPath = cmdParameters.getParameters().get(
                OUTPUT_FILE_PATH_PARAMETER);
        File outputFile = new File(outputPath);
        List<Blob> blobs = new ArrayList<Blob>();

        Blob blob = new FileBlob(outputFile);
        blob.setFilename(outputFile.getName());
        blob.setMimeType(getVideoMimeType());
        blobs.add(blob);

        Map<String, Serializable> properties = new HashMap<String, Serializable>();
        properties.put("cmdOutput", (Serializable) cmdOutput);
        return new SimpleBlobHolderWithProperties(blobs, properties);
    }

    protected abstract String getVideoMimeType();

    protected abstract String getVideoExtension();

    protected abstract String getTmpDirectoryPrefix();

}
