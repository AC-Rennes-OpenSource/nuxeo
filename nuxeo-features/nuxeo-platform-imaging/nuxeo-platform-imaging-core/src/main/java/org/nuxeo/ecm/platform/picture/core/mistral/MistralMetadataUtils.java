/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.picture.core.mistral;

import static org.nuxeo.ecm.platform.picture.api.MetadataConstants.*;

import it.tidalwave.image.EditableImage;
import it.tidalwave.image.Rational;
import it.tidalwave.image.metadata.EXIFDirectory;
import it.tidalwave.image.op.ReadOp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.platform.picture.core.MetadataUtils;
import org.nuxeo.runtime.services.streaming.InputStreamSource;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.iptc.IptcDirectory;

/**
 * @author Max Stepanov
 */
public class MistralMetadataUtils implements MetadataUtils {

    private static final Log log = LogFactory.getLog(MistralMetadataUtils.class);

    private static final int BUFFER_LIMIT = 32000000;

    @Deprecated
    public Map<String, Object> getImageMetadata(InputStream in) {
        BufferedInputStream bin = null;
        if (in instanceof InputStream) {
            if (in instanceof BufferedInputStream) {
                bin = (BufferedInputStream) in;
            } else {
                in = bin = new BufferedInputStream((InputStream) in);
            }
            bin.mark(BUFFER_LIMIT);
        }
        Blob blob = new StreamingBlob(new InputStreamSource(bin));
        return getImageMetadata(blob);
    }

    @Deprecated
    public Map<String, Object> getImageMetadata(File file) {
        Blob blob = new FileBlob(file);
        return getImageMetadata(blob);
    }

    public Map<String, Object> getImageMetadata(Blob blob) {
        Map<String, Object> metadata = new HashMap<String, Object>();

        try {
            /* EXIF */
            EditableImage image = EditableImage.create(new ReadOp(blob.getStream(), ReadOp.Type.METADATA));
            EXIFDirectory exif = image.getEXIFDirectory();

            int width;
            int height;
            if (exif.isPixelXDimensionAvailable() && exif.isPixelYDimensionAvailable()) {
                width = exif.getPixelXDimension();
                height = exif.getPixelYDimension();
            } else {
                image = EditableImage.create(new ReadOp(blob.getStream()));
                width = image.getWidth();
                height = image.getHeight();
            }
            metadata.put(META_WIDTH, width);
            metadata.put(META_HEIGHT, height);

            if (exif.isImageDescriptionAvailable()) {
                String description = exif.getImageDescription().trim();
                if (description.length() > 0) {
                    metadata.put(META_DESCRIPTION, description);
                }
            }

            if (exif.isUserCommentAvailable()) {
                String comment = new String(exif.getUserComment()).trim();
                if (comment.length() > 0) {
                    metadata.put(META_COMMENT, comment);
                }
            }

            if (exif.isMakeAvailable() || exif.isModelAvailable()) {
                String equipment = (exif.getMake() + " " + exif.getModel()).trim();
                if (equipment.length() > 0) {
                    metadata.put(META_EQUIPMENT, equipment);
                }
            }

            if (exif.isDateTimeOriginalAvailable()) {
                metadata.put(META_ORIGINALDATE, exif.getDateTimeOriginalAsDate());
            }

            if (exif.isXResolutionAvailable() && exif.isYResolutionAvailable()) {
                metadata.put(META_HRESOLUTION, exif.getXResolution().intValue());
                metadata.put(META_VRESOLUTION, exif.getYResolution().intValue());
            }

            if (exif.isCopyrightAvailable()) {
                String copyright = exif.getCopyright().trim();
                if (copyright.length() > 0) {
                    metadata.put(META_COPYRIGHT, copyright);
                }
            }

            if (exif.isExposureTimeAvailable()) {
                Rational exposure = exif.getExposureTime();
                int n = exposure.getNumerator();
                int d = exposure.getDenominator();
                if (d >= n && d % n == 0) {
                    exposure = new Rational(1, d / n);
                }
                metadata.put(META_EXPOSURE, exposure.toString());
            }

            if (exif.isISOSpeedRatingsAvailable()) {
                metadata.put(META_ISOSPEED, "ISO-" + exif.getISOSpeedRatings());
            }

            if (exif.isFocalLengthAvailable()) {
                metadata.put(META_FOCALLENGTH, exif.getFocalLength().doubleValue());
            }

            if (exif.isColorSpaceAvailable()) {
                metadata.put(META_COLORSPACE, exif.getColorSpace());
            }

            if (exif.isWhiteBalanceAvailable()) {
                metadata.put(META_WHITEBALANCE, exif.getWhiteBalance().toString().toLowerCase());
            }

            if (exif.isInterColourProfileAvailable()) {
                metadata.put(META_ICCPROFILE, exif.getICCProfile());
            }
        } catch (IOException e) {
            log.error("Failed to get EXIF metadata", e);
        }

        try {
            /* IPTC */
            Metadata md = null;
            if (MistralMimeUtils.MIME_IMAGE_JPEG.equals(blob.getMimeType())) {
                md = JpegMetadataReader.readMetadata(blob.getStream());
            }
            if (md != null) {
                Directory iptc = md.getDirectory(IptcDirectory.class);
                if (iptc.containsTag(IptcDirectory.TAG_BY_LINE)) {
                    metadata.put(META_BYLINE, iptc.getString(IptcDirectory.TAG_BY_LINE));
                }
                if (iptc.containsTag(IptcDirectory.TAG_CAPTION)) {
                    metadata.put(META_CAPTION, iptc.getString(IptcDirectory.TAG_CAPTION));
                }
                if (iptc.containsTag(IptcDirectory.TAG_CATEGORY)) {
                    metadata.put(META_CATEGORY, iptc.getString(IptcDirectory.TAG_CATEGORY));
                }
                if (iptc.containsTag(IptcDirectory.TAG_CITY)) {
                    metadata.put(META_CITY, iptc.getString(IptcDirectory.TAG_CITY));
                }
                if (iptc.containsTag(IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION)) {
                    metadata.put(META_COUNTRY, iptc.getString(IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION));
                }
                if (iptc.containsTag(IptcDirectory.TAG_CREDIT)) {
                    metadata.put(META_CREDIT, iptc.getString(IptcDirectory.TAG_CREDIT));
                }
                if (iptc.containsTag(IptcDirectory.TAG_DATE_CREATED)) {
                    Date date = new Date();
                    if (iptc.containsTag(IptcDirectory.TAG_TIME_CREATED)) {
                        System.out.println("iptc.time=" + iptc.getString(IptcDirectory.TAG_TIME_CREATED));
                    }
                    System.out.println("iptc.date=" + iptc.getString(IptcDirectory.TAG_DATE_CREATED));
                    metadata.put(META_DATE, date);
                }
                if (iptc.containsTag(IptcDirectory.TAG_HEADLINE)) {
                    metadata.put(META_HEADLINE, iptc.getString(IptcDirectory.TAG_HEADLINE));
                }
                if (iptc.containsTag(135)) {
                    metadata.put(META_LANGUAGE, iptc.getString(135));
                }
                if (iptc.containsTag(IptcDirectory.TAG_OBJECT_NAME)) {
                    metadata.put(META_OBJECTNAME, iptc.getString(IptcDirectory.TAG_OBJECT_NAME));
                }
                if (iptc.containsTag(IptcDirectory.TAG_SUPPLEMENTAL_CATEGORIES)) {
                    metadata.put(META_SUPPLEMENTALCATEGORIES, iptc.getString(IptcDirectory.TAG_SUPPLEMENTAL_CATEGORIES));
                }
                if (iptc.containsTag(IptcDirectory.TAG_SOURCE)) {
                    metadata.put(META_SOURCE, iptc.getString(IptcDirectory.TAG_SOURCE));
                }
            }
        } catch (IOException e) {
            log.error("Failed to get IPTC metadata", e);
        } catch (JpegProcessingException e) {
            log.error("Failed to get IPTC metadata", e);
        }

        return metadata;
    }

}