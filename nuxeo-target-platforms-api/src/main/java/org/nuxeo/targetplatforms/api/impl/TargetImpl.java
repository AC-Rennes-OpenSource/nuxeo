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
 *     mcedica@nuxeo.com
 *     Anahide Tchertchian
 */
package org.nuxeo.targetplatforms.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.nuxeo.targetplatforms.api.Target;


/**
 * Common class to describe a target platform or package.
 *
 * @since 5.7.1
 */
public class TargetImpl extends TargetInfoImpl implements Target {

    private static final long serialVersionUID = 1L;

    protected List<String> types;

    // needed by GWT serialization
    protected TargetImpl() {
        super();
    }

    public TargetImpl(String id) {
        super(id);
    }

    public TargetImpl(String id, String name, String version,
            String refVersion, String label) {
        super(id, name, version, refVersion, label);
    }

    @Override
    public boolean isAfterVersion(String version) {
        if (version == null || version.trim().length() == 0) {
            return true;
        }
        return version.compareTo(getRefVersion()) <= 0;
    }

    @Override
    public boolean isStrictlyBeforeVersion(String version) {
        if (version == null || version.trim().length() == 0) {
            return true;
        }
        return version.compareTo(getRefVersion()) > 0;
    }

    @Override
    public boolean isVersion(String version) {
        if (version == null || version.trim().length() == 0) {
            return false;
        }
        return version.compareTo(getRefVersion()) == 0;
    }

    @Override
    public boolean isStrictlyBeforeVersion(Target version) {
        return isStrictlyBeforeVersion(version.getRefVersion());
    }

    @Override
    public boolean isAfterVersion(Target version) {
        return isAfterVersion(version.getRefVersion());
    }

    @Override
    public boolean isVersion(Target version) {
        return isVersion(getRefVersion());
    }

    @Override
    public List<String> getTypes() {
        if (types == null) {
            return Collections.emptyList();
        }
        return types;
    }

    public void setTypes(List<String> types) {
        if (types == null) {
            this.types = null;
        } else {
            this.types = new ArrayList<>(types);
        }
    }

    @Override
    public boolean matchesType(String type) {
        if (types == null) {
            return false;
        }
        return types.contains(type);
    }

}