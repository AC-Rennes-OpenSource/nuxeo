/*
 * (C) Copyright 2006-2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Bogdan Stefanescu
 *     Thierry Delprat
 */
package org.nuxeo.apidoc.introspection;

import org.nuxeo.apidoc.api.BaseNuxeoArtifact;
import org.nuxeo.apidoc.api.ExtensionInfo;
import org.nuxeo.apidoc.api.VirtualNodesConsts;
import org.nuxeo.runtime.model.ComponentName;

public class ExtensionInfoImpl extends BaseNuxeoArtifact implements
        ExtensionInfo {

    protected String id;

    protected ComponentInfoImpl component;

    protected String extensionPoint;

    protected String documentation;

    protected String xml;

    protected ComponentName targetComponentName;

    protected Object[] contribution;

    public ExtensionInfoImpl(ComponentInfoImpl component, String xpoint) {
        this.id = component.getId() + "--" + xpoint;
        this.component = component;
        this.extensionPoint = xpoint;
    }

    @Override
    public String getExtensionPoint() {
        return targetComponentName.getName() + "--" + extensionPoint;
    }

    public String getComponentName() {
        return component.getId();
    }

    public ComponentInfoImpl getComponent() {
        return component;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    @Override
    public String getDocumentation() {
        return documentation;
    }

    @Override
    public ComponentName getTargetComponentName() {
        return targetComponentName;
    }

    public void setTargetComponentName(ComponentName targetComponentName) {
        this.targetComponentName = targetComponentName;
    }

    public Object[] getContribution() {
        return contribution;
    }

    public void setContribution(Object[] contribution) {
        this.contribution = contribution;
    }

    @Override
    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    @Override
    public String getVersion() {
        return component.getVersion();
    }

    @Override
    public String getArtifactType() {
        return ExtensionInfo.TYPE_NAME;
    }

    @Override
    public String getHierarchyPath() {
        return component.getHierarchyPath() + "/"
                + VirtualNodesConsts.Contributions_VNODE_NAME + "/" + getId();
    }

}
