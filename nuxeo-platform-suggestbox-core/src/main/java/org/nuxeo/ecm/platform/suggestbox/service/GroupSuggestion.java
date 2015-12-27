/*
 * (C) Copyright 2010-2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.suggestbox.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Suggest to navigate to a specific group profile.
 */
public class GroupSuggestion extends Suggestion {

    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "group";

    protected final String groupId;

    public GroupSuggestion(String groupId, String label, String iconURL) {
        super(groupId, CommonSuggestionTypes.GROUP, label, iconURL);
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getObjectUrl() {
        List<String> items = new ArrayList<String>();
        items.add(PREFIX);
        items.add(groupId);
        return StringUtils.join(items, "/");
    }
}
