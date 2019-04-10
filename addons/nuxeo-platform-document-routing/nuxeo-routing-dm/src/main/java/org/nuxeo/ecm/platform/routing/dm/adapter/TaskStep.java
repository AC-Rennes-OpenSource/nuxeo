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
 *     ldoguin
 */
package org.nuxeo.ecm.platform.routing.dm.adapter;

import java.util.Date;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * @deprecated since 5.9.2 - Use only routes of type 'graph'
 */
@Deprecated
public interface TaskStep {

    DocumentModel getDocument();

    String getId();

    List<String> getActors();

    String getName();

    String getDirective();

    List<String> getComments();

    Date getDueDate();

    Boolean hasAutomaticValidation();

}
