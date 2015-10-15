/*
 * (C) Copyright 2011-2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.binary.Binary;
import org.nuxeo.ecm.core.storage.common.AbstractTestCloudBinaryManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * ***** NOTE THAT THE TESTS WILL REMOVE ALL FILES IN THE BUCKET!!! *****
 * <p>
 * This test must be run with at least the following system properties set:
 * <ul>
 * <li>nuxeo.s3storage.bucket</li>
 * <li>nuxeo.s3storage.awsid or AWS_ACCESS_KEY_ID</li>
 * <li>nuxeo.s3storage.awssecret or AWS_SECRET_ACCESS_KEY</li>
 * </ul>
 * <p>
 * ***** NOTE THAT THE TESTS WILL REMOVE ALL FILES IN THE BUCKET!!! *****
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestS3BinaryManager extends AbstractTestCloudBinaryManager<S3BinaryManager> {

    @BeforeClass
    public static void beforeClass() {
        // this also checks in system properties for the configuration
        String bucketName = Framework.getProperty(S3BinaryManager.BUCKET_NAME_KEY);
        if (bucketName == null) {
            // NOTE THAT THE TESTS WILL REMOVE ALL FILES IN THE BUCKET!!!
            // ********** NEVER COMMIT THE SECRET KEYS !!! **********
            bucketName = "CHANGETHIS";
            String idKey = "CHANGETHIS";
            String secretKey = "CHANGETHIS";
            // ********** NEVER COMMIT THE SECRET KEYS !!! **********
            Properties props = Framework.getProperties();
            props.setProperty(S3BinaryManager.BUCKET_NAME_KEY, bucketName);
            props.setProperty(S3BinaryManager.AWS_ID_KEY, idKey);
            props.setProperty(S3BinaryManager.AWS_SECRET_KEY, secretKey);
        }
        boolean disabled = bucketName.equals("CHANGETHIS");
        assumeTrue("No AWS credentials configured", !disabled);
    }

    @AfterClass
    public static void afterClass() {
        Properties props = Framework.getProperties();
        props.remove(S3BinaryManager.CONNECTION_MAX_KEY);
        props.remove(S3BinaryManager.CONNECTION_RETRY_KEY);
        props.remove(S3BinaryManager.CONNECTION_TIMEOUT_KEY);
    }

    @After
    public void tearDown() throws Exception {
        removeObjects();
    }

    @Test
    public void testS3BinaryManagerOverwrite() throws Exception {
        // store binary
        byte[] bytes = CONTENT.getBytes("UTF-8");
        Binary binary = binaryManager.getBinary(Blobs.createBlob(CONTENT));
        assertNotNull(binary);
        assertEquals(bytes.length, binary.getLength());
        assertNull(Framework.getProperty("cachedBinary"));

        // store the same content again
        Binary binary2 = binaryManager.getBinary(Blobs.createBlob(CONTENT));
        assertNotNull(binary2);
        assertEquals(bytes.length, binary2.getLength());
        // check that S3 bucked was not called for no valid reason
        assertEquals(binary2.getDigest(), Framework.getProperty("cachedBinary"));
    }

    @Test
    public void testS3MaxConnections() throws Exception {
        // cleaned by tearDown
        Properties props = Framework.getProperties();
        props.put(S3BinaryManager.CONNECTION_MAX_KEY, "1");
        props.put(S3BinaryManager.CONNECTION_RETRY_KEY, "0");
        props.put(S3BinaryManager.CONNECTION_TIMEOUT_KEY, "5000"); // 5s
        binaryManager = new S3BinaryManager();
        binaryManager.initialize("repo", Collections.emptyMap());

        // store binary
        byte[] bytes = CONTENT.getBytes("UTF-8");
        binaryManager.getBinary(Blobs.createBlob(CONTENT));

        S3Object o = binaryManager.amazonS3.getObject(binaryManager.bucketName, CONTENT_MD5);
        try {
            binaryManager.amazonS3.getObject(binaryManager.bucketName, CONTENT_MD5);
            fail("Should throw AmazonClientException");
        } catch (AmazonClientException e) {
            Throwable c = e.getCause();
            assertTrue(c.getClass().getName(), c instanceof ConnectionPoolTimeoutException);
        }
        o.close();
    }

    @Override
    protected S3BinaryManager getBinaryManager() throws IOException {
        S3BinaryManager binaryManager = new S3BinaryManager();
        binaryManager.initialize("repo", Collections.emptyMap());
        return binaryManager;
    }

    /**
     * Lists all objects that look like MD5 digests.
     */
    protected Set<String> listObjects() {
        Set<String> digests = new HashSet<>();
        ObjectListing list = null;
        do {
            if (list == null) {
                list = binaryManager.amazonS3.listObjects(binaryManager.bucketName);
            } else {
                list = binaryManager.amazonS3.listNextBatchOfObjects(list);
            }
            for (S3ObjectSummary summary : list.getObjectSummaries()) {
                String digest = summary.getKey();
                if (!S3BinaryManager.isMD5(digest)) {
                    continue;
                }
                digests.add(digest);
            }
        } while (list.isTruncated());
        return digests;
    }
}
