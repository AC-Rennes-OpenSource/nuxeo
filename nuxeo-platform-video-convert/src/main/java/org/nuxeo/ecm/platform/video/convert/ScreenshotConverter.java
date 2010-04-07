/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id$
 */
package org.nuxeo.ecm.platform.video.convert;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolderWithProperties;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.ecm.platform.commandline.executor.api.ExecResult;
import org.nuxeo.runtime.api.Framework;

/**
 * Extract a JPEG screenshot of the video at a given time offset (position).
 *
 * @author ogrisel
 */
public class ScreenshotConverter extends BaseVideoConverter implements
        Converter {

    public static final Log log = LogFactory.getLog(ScreenshotConverter.class);

    public static final String FFMPEG_SCREENSHOT_COMMAND = "ffmpeg-screenshot";

    protected CommandLineExecutorService cleService;

    public void init(ConverterDescriptor descriptor) {
        try {
            cleService = Framework.getService(CommandLineExecutorService.class);
        } catch (Exception e) {
            log.error(e, e);
            return;
        }
    }

    public BlobHolder convert(BlobHolder blobHolder,
            Map<String, Serializable> parameters) throws ConversionException {

        File outFile = null;
        Blob blob = null;
        InputFile inputFile = null;
        try {
            blob = blobHolder.getBlob();
            inputFile = new InputFile(blob);
            outFile = File.createTempFile("ScreenshotConverter-out-",
                    ".tmp.jpeg");

            CmdParameters params = new CmdParameters();
            params.addNamedParameter("inFilePath",
                    inputFile.file.getAbsolutePath());
            params.addNamedParameter("outFilePath", outFile.getAbsolutePath());
            Double position = 0.0;
            if (parameters != null) {
                position = (Double) parameters.get(Constants.POSITION_PARAMETER);
                if (position == null) {
                    position = 0.0;
                }
            }
            long positionParam = Math.round(position);
            params.addNamedParameter(Constants.POSITION_PARAMETER,
                    String.valueOf(positionParam));
            ExecResult result = cleService.execCommand(
                    FFMPEG_SCREENSHOT_COMMAND, params);

            Blob outBlob = StreamingBlob.createFromStream(
                    new FileInputStream(outFile), "image/jpeg").persist();
            outBlob.setFilename(String.format("video-screenshot-%05d.000.jpeg",
                    positionParam));
            Map<String, Serializable> properties = new HashMap<String, Serializable>();
            properties.put("duration", BaseVideoConverter.extractDuration(result.getOutput()));
            return new SimpleBlobHolderWithProperties(outBlob, properties);
        } catch (Exception e) {
            if (blob != null) {
                throw new ConversionException(
                        "error extracting screenshot from '"
                                + blob.getFilename() + "': " + e.getMessage(),
                        e);
            } else {
                throw new ConversionException(e.getMessage(), e);
            }
        } finally {
            FileUtils.deleteQuietly(outFile);
            if (inputFile != null && inputFile.isTempFile) {
                FileUtils.deleteQuietly(inputFile.file);
            }
        }
    }
}
