/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Stephane Lacoin at Nuxeo (aka matic)
 */
package org.nuxeo.connect.tools.report;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.nuxeo.connect.tools.report.ReportConfiguration.Contribution;
import org.nuxeo.ecm.core.management.statuses.NuxeoInstanceIdentifierHelper;
import org.nuxeo.runtime.RuntimeServiceEvent;
import org.nuxeo.runtime.RuntimeServiceListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ResourcePublisher;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.reload.ReloadService;
import org.nuxeo.runtime.services.event.Event;
import org.nuxeo.runtime.services.event.EventListener;
import org.nuxeo.runtime.services.event.EventService;

/**
 * Reports aggregator, exposed as a service in the runtime.
 *
 * @since 8.3
 */
public class ReportComponent extends DefaultComponent {

    public interface Runner {

        void run(OutputStream out, Set<String> names) throws IOException;

        Set<String> list();
    }

    public static ReportComponent instance;

    public ReportComponent() {
        instance = this;
    }

    ReloadListener reloadListener;

    class ReloadListener implements EventListener {
        final ComponentContext context;

        ReloadListener(ComponentContext context) {
            this.context = context;
        }

        @Override
        public void handleEvent(Event event) {
            if (ReloadService.AFTER_RELOAD_EVENT_ID.equals(event.getId())) {
                applicationStarted(context);
                return;
            } else if (ReloadService.BEFORE_RELOAD_EVENT_ID.equals(event.getId())) {
                applicationStopped(context);
            }
        }

        @Override
        public boolean aboutToHandleEvent(Event event) {
            return true;
        }
    }

    @Override
    public void activate(ComponentContext context) {
        Framework.getService(EventService.class).addListener(ReloadService.RELOAD_TOPIC, reloadListener = new ReloadListener(context));
    }

    @Override
    public void deactivate(ComponentContext context) {
        Framework.getService(EventService.class).removeListener(ReloadService.RELOAD_TOPIC, reloadListener);
    }

    final ReportConfiguration configuration = new ReportConfiguration();

    final Service service = new Service();

    class Service implements ReportRunner {
        @Override
        public Set<String> list() {
            Set<String> names = new HashSet<>();
            for (Contribution contrib : configuration) {
                names.add(contrib.name);
            }
            return names;
        }

        @Override
        public void run(OutputStream out, Set<String> names) throws IOException {
            out.write('{');
            out.write('"');
            out.write(NuxeoInstanceIdentifierHelper.getServerInstanceName().getBytes());
            out.write('"');
            out.write(':');
            out.write('{');
            Iterator<Contribution> iterator = configuration.iterator(names);
            while (iterator.hasNext()) {
                Contribution contrib = iterator.next();
                out.write('"');
                out.write(contrib.name.getBytes());
                out.write('"');
                out.write(':');
                contrib.writer.write(out);
                if (iterator.hasNext()) {
                    out.write(',');
                }
                out.flush();
            }
            out.write('}');
            out.write('}');
            out.flush();
        }
    }

    final Management management = new Management();

    public class Management implements ReportServer {

        @Override
        public void run(String host, int port, String... names) throws IOException {
            ClassLoader tcl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(Runtime.class.getClassLoader());
            try (Socket sock = new Socket(host, port)) {
                try (OutputStream sink =
                        sock.getOutputStream()) {
                    service.run(sink, new HashSet<>(Arrays.asList(names)));
                }
            } catch (IOException cause) {
                throw cause;
            } finally {
                Thread.currentThread().setContextClassLoader(tcl);
            }
        }

    }

    @Override
    public void applicationStarted(ComponentContext context) {
        instance = this;
        Framework.getService(ResourcePublisher.class).registerResource("connect-report", "connect-report", ReportServer.class, management);
        Framework.addListener(new RuntimeServiceListener() {

            @Override
            public void handleEvent(RuntimeServiceEvent event) {
                if (event.id != RuntimeServiceEvent.RUNTIME_ABOUT_TO_STOP) {
                    return;
                }
                Framework.removeListener(this);
                applicationStopped(context);
            }

        });
    }

    protected void applicationStopped(ComponentContext context) {
        Framework.getService(ResourcePublisher.class).unregisterResource("connect-report", "connect-report");
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof Contribution) {
            configuration.addContribution((Contribution) contribution);
        } else {
            throw new IllegalArgumentException(String.format("unknown contribution of type %s in %s", contribution.getClass(), contributor));
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(Service.class)) {
            return adapter.cast(service);
        }
        return super.getAdapter(adapter);
    }

}