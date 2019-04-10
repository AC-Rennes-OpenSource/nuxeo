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
 *     Thierry Delprat
 */
package org.nuxeo.apidoc.introspection;

import org.nuxeo.apidoc.api.BaseNuxeoArtifact;
import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.apidoc.api.ServiceInfo;
import org.nuxeo.apidoc.api.VirtualNodesConsts;

public class ServiceInfoImpl extends BaseNuxeoArtifact implements ServiceInfo {

    protected final String serviceClassName;

    protected final ComponentInfo component;

    public ServiceInfoImpl(String serviceClassName, ComponentInfo component) {
        this.serviceClassName = serviceClassName;
        this.component = component;
    }

    @Override
    public String getId() {
        return serviceClassName;
    }

    @Override
    public String getArtifactType() {
        return TYPE_NAME;
    }

    @Override
    public String getVersion() {
        return component.getVersion();
    }

    @Override
    public String getComponentId() {
        return component.getId();
    }

    @Override
    public String getHierarchyPath() {
        return component.getHierarchyPath() + "/"
                + VirtualNodesConsts.Services_VNODE_NAME + "/" + this.getId();
    }

}
