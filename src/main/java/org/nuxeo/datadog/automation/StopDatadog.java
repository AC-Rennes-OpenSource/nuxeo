/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.datadog.automation;

import org.nuxeo.datadog.reporter.DatadogReporterService;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;

@Operation(id = StopDatadog.ID, category = Constants.CAT_SERVICES, label = "Stop datadog reporter", description = "Stops the Datadog reporter")
public class StopDatadog {

    protected static final String ID = "Datadog.Stop";

    @Context
    private DatadogReporterService ddrs;

    @OperationMethod
    public void run() {
        ddrs.stopReporter();
    }
}
