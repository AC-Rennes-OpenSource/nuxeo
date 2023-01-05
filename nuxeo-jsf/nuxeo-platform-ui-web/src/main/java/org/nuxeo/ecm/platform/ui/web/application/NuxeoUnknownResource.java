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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.ui.web.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import javax.faces.application.ViewResource;

import org.nuxeo.runtime.api.Framework;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Resource representing a facelet that is not found in this application.
 * <p>
 * This is used to avoid crashing triggering an exception when a facelet resource is missing.
 * <p>
 * Instead, a message referencing the missing resource is displayed in red and in bold where the facelet would have been
 * included.
 *
 * @since 6.0
 */
public class NuxeoUnknownResource extends ViewResource {

    public static final String MARKER = NuxeoUnknownResource.class.getName();

    public static final String PLACEHOLDER = "/facelet_not_found.xhtml";

    private static final Log log = LogFactory.getLog(NuxeoUnknownResource.class);

    protected final String path;

    protected String errorMessage;

    public NuxeoUnknownResource(String path) {
        super();
        this.path = path;
        /** FIX SECURITY ISSUE : CVE-2018-16341 / NXP-25746
         * (See PoC : https://github.com/puckiestyle/CVE-2018-16341)
         */
        errorMessage = "ERROR: facelet not found";
        if (Framework.isDevModeSet() && !path.contains("$") && !path.contains("#")) {
        	errorMessage += " at '" + path + "'";
        }
        /** END OF FIX */
    }

    @Override
    public URL getURL() {
        try {
            String urlPath = String.format("%s%s", MARKER, path);
            return new URL("", "", -1, urlPath, new NuxeoNotFoundResourceHandler());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    class NuxeoNotFoundResourceHandler extends URLStreamHandler {

        public NuxeoNotFoundResourceHandler() {
            super();
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            log.error(errorMessage);
            return new Connection(url);
        }

        class Connection extends URLConnection {

            public Connection(URL url) {
                super(url);
            }

            @Override
            public void connect() throws IOException {
            }

            @Override
            public InputStream getInputStream() throws IOException {
                String msg = String.format("<span><span style=\"color:red;font-weight:bold;\">%s</span><br/></span>",
                        StringEscapeUtils.escapeHtml(errorMessage));
                return new ByteArrayInputStream(msg.getBytes());
            }
        }
    }
}