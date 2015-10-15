/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Nuxeo
 */

package org.nuxeo.ecm.blob.azure;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.blob.AbstractCloudBinaryManager;
import org.nuxeo.ecm.blob.AbstractTestCloudBinaryManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import com.microsoft.azure.storage.Constants;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.core.StreamMd5AndLength;
import com.microsoft.azure.storage.core.Utility;

/**
 * WARNING: You must pass those variables to your test configuration:
 * <p/>
 *
 * <pre>
 *   -Dnuxeo.storage.azure.account.name: Azure account name
 *   -Dnuxeo.storage.azure.account.key: Azure account key
 *   -Dnuxeo.storage.azure.container: A test container name
 * </pre>
 *
 * @author <a href="mailto:ak@nuxeo.com">Arnaud Kervern</a>
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestAzureBinaryManager extends AbstractTestCloudBinaryManager<AzureBinaryManager> {

    protected final static List<String> PARAMETERS = Arrays.asList(AzureBinaryManager.ACCOUNT_KEY_PROPERTY, AzureBinaryManager.ACCOUNT_NAME_PROPERTY,
            AzureBinaryManager.CONTAINER_PROPERTY);

    protected static Map<String, String> properties = new HashMap<>();

    @BeforeClass
    public static void initialize() {
        AbstractCloudBinaryManager bm = new AzureBinaryManager();
        PARAMETERS.forEach(s -> {
            properties.put(s, Framework.getProperty(bm.getConfigurationKey(s)));
        });

        if (StringUtils.isBlank(properties.get(AzureBinaryManager.ACCOUNT_KEY_PROPERTY))) {
            properties.put(AzureBinaryManager.ACCOUNT_NAME_PROPERTY, System.getenv("AZURE_NAME_ACCOUNT"));
            properties.put(AzureBinaryManager.ACCOUNT_KEY_PROPERTY, System.getenv("AZURE_ACCESS_SECRET"));
        }

        // Ensure mandatory parameters are set
        PARAMETERS.forEach(s -> {
            assumeFalse(isBlank(properties.get(s)));
        });
    }

    @AfterClass
    public static void afterClass() {
        // Cleanup keys
        Properties props = Framework.getProperties();
        PARAMETERS.forEach(props::remove);
    }

    @Override
    protected AzureBinaryManager getBinaryManager() throws IOException {
        AzureBinaryManager binaryManager = new AzureBinaryManager();
        binaryManager.initialize("azuretest", properties);
        return binaryManager;
    }

    @Override
    protected Set<String> listObjects() {
        Set<String> digests = new HashSet<>();
        binaryManager.container.listBlobs().forEach(lb -> {
            try {
                digests.add(((CloudBlockBlob) lb).getName());
            } catch (URISyntaxException e) {
                // Do nothing.
            }
        });
        return digests;
    }

    @Test
    public void ensureDigestDecode() throws IOException, StorageException {
        Blob blob = Blobs.createBlob(CONTENT2);
        String contentMd5;
        try (InputStream is = blob.getStream()) {
            StreamMd5AndLength md5 = Utility.analyzeStream(is, blob.getLength(), 2 * Constants.MB, true, true);
            contentMd5 = md5.getMd5();
        }

        assertTrue(AzureFileStorage.isBlobDigestCorrect(CONTENT2_MD5, contentMd5));
    }
}
