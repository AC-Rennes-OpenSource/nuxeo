/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *      Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.binary.metadata.api.operation;

import java.util.ArrayList;
import java.util.Map;

import org.nuxeo.binary.metadata.api.service.BinaryMetadataService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;

/**
 * @since 7.1
 */
@Operation(id = WriteMetadataToContextFromBinary.ID, category = Constants.CAT_EXECUTION, label = "Write Metadata To "
        + "Context From Binary", description = "Write Metadata To Context From "
        + "Binary given in input and given metadata to inject into the "
        + "Operation context (if not specified, all metadata will be injected) " + "", since = "7.1", addToStudio = true)
public class WriteMetadataToContextFromBinary {

    public static final String ID = "Context.WriteMetadataFromBinary";

    @Context
    protected BinaryMetadataService binaryMetadataService;

    @Context
    protected OperationContext operationContext;

    @Param(name = "processor", required = false, description = "The processor.")
    protected String processor = "exifTool";

    @Param(name = "metadata", required = false, description = "The processor.")
    protected StringList metadata;

    @OperationMethod
    public void run(Blob blob) {
        if (metadata != null && metadata.isEmpty()) {
            operationContext.put("binaryMetadata", binaryMetadataService
                    .readMetadata(blob));
        } else {
            ArrayList<String> metadataList = new ArrayList<>();
            for (String meta : metadata) {
                metadataList.add(meta);
            }
            operationContext.put("binaryMetadata", binaryMetadataService
                    .writeMetadata(blob, (Map<String, Object>) metadataList));
        }
    }
}
