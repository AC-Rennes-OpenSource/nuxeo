/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thierry Delprat
 */
package org.nuxeo.apidoc.documentation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.apidoc.api.AbstractDocumentationItem;
import org.nuxeo.apidoc.api.BaseNuxeoArtifact;
import org.nuxeo.apidoc.api.DocumentationItem;
import org.nuxeo.apidoc.introspection.BundleGroupImpl;
import org.nuxeo.apidoc.introspection.BundleInfoImpl;

import com.cforcoding.jmd.MarkDownParserAndSanitizer;

public class ResourceDocumentationItem extends AbstractDocumentationItem implements DocumentationItem {

    protected String content;

    protected String filename;

    protected BaseNuxeoArtifact target;

    protected String type;

    public ResourceDocumentationItem(String filename, String content, BundleInfoImpl target, String type) {
        this.content = content;
        this.filename = filename;
        this.target = target;
        this.type = type;
    }

    public ResourceDocumentationItem(ResourceDocumentationItem other, BundleGroupImpl target) {
        this.content = other.content;
        this.filename = other.filename;
        this.target = target;
        this.type = other.type;
    }

    @Override
    public String getTitle() {
        return getCleanName() + " " + target.getId();
    }

    protected String getCleanName() {
        if (filename == null || filename.toLowerCase().startsWith("readme")) {
            return "ReadMe";
        }
        int idx = filename.indexOf(".");
        if (idx > 0) {
            return filename.substring(0, idx);
        }
        return filename;
    }

    @Override
    public String getContent() {
        MarkDownParserAndSanitizer parser = new MarkDownParserAndSanitizer();
        String xHtml = parser.transform(content);
        return xHtml;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getRenderingType() {
        return "html";
    }

    @Override
    public List<String> getApplicableVersion() {
        return Arrays.asList(target.getVersion());
    }

    @Override
    public String getTarget() {
        return target.getId();
    }

    @Override
    public String getTargetType() {
        return target.getArtifactType();
    }

    @Override
    public boolean isApproved() {
        return true;
    }

    @Override
    public String getId() {
        return getTargetType() + "--" + filename;
    }

    @Override
    public String getUUID() {
        return null;
    }

    @Override
    public Map<String, String> getAttachments() {
        return new HashMap<>();
    }

    @Override
    public boolean isPlaceHolder() {
        return true;
    }

    @Override
    public String getEditId() {
        return null;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
