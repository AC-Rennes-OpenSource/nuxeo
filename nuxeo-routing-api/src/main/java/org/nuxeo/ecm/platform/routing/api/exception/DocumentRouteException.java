/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.routing.api.exception;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Basic document routing exception.
 *
 * @since 5.6
 */
public class DocumentRouteException extends NuxeoException {

    private static final long serialVersionUID = 1L;

    public DocumentRouteException() {
    }

    public DocumentRouteException(String message) {
        super(message);
    }

    public DocumentRouteException(Throwable cause) {
        super(cause);
    }

    public DocumentRouteException(String message, Throwable cause) {
        super(message, cause);
    }

}
