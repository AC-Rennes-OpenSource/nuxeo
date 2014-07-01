/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */
package org.nuxeo.apidoc.test;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.apidoc.documentation.DocumentationHelper;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

public class TestDocumentationHelper extends NXRuntimeTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.platform.htmlsanitizer");
    }

    @Test
    public void test() throws Exception {
        assertEquals("<p> foo </p>\n" //
                + "<p> bar</p>", //
                DocumentationHelper.getHtml("foo\n\nbar"));
        assertEquals("<p> foo </p>\n" //
                + "<p> bar</p>", //
                DocumentationHelper.getHtml("foo\n\n<br/>\nbar"));
        assertEquals("<p> foo </p>\n" //
                + "<p>\n" //
                + "  <pre><code>bar\n" //
                + "</code></pre></p>\n" //
                + "<p> baz</p>", //
                DocumentationHelper.getHtml("foo\n<code>\nbar\n</code>\nbaz"));
        assertEquals("<p> foo <ul>\n" //
                + "    <li>bar</li></ul></p>",
                DocumentationHelper.getHtml("foo\n<ul>\n<li>bar</li>\n</ul>\n"));
        assertEquals("<p> foo </p>\n" //
                + "<p> bar</p>", //
                DocumentationHelper.getHtml("foo\n@author you\nbar"));
    }
}
