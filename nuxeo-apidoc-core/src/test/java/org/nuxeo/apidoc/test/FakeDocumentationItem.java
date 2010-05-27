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
 */

package org.nuxeo.apidoc.test;

import java.util.List;
import java.util.Map;

import org.nuxeo.apidoc.api.DocumentationItem;

public class FakeDocumentationItem implements DocumentationItem {

    public List<String> applicableVersion;
    public String content;
    public String id;
    public String renderingType;
    public String target;
    public String targetType;
    public String title;
    public String type;
    public String typeLabel;
    public String uuid;
    public boolean approved=false;

    public FakeDocumentationItem() {

    }

    public FakeDocumentationItem(DocumentationItem item) {
        applicableVersion = item.getApplicableVersion();
        content = item.getContent();
        id = item.getId();
        renderingType = item.getRenderingType();
        target = item.getTarget();
        title = item.getTitle();
        type = item.getType();
        typeLabel = item.getTypeLabel();
        uuid = item.getUUID();
        approved = item.isApproved();
        type = item.getTargetType();
    }

    public List<String> getApplicableVersion() {
        return applicableVersion;
    }

    public String getContent() {
        return content;
    }

    public String getId() {
        return id;
    }

    public String getRenderingType() {
        return renderingType;
    }

    public String getTarget() {
        return target;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public String getUUID() {
        return uuid;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getTargetType() {
        return targetType;
    }

    public Map<String, String> getAttachments() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isPlaceHolder() {
        return false;
    }

    public String getEditId() {
        // TODO Auto-generated method stub
        return null;
    }

}
