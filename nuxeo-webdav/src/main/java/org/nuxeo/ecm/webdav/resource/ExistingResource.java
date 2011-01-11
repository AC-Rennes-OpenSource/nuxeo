/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *
 * $Id$
 */

package org.nuxeo.ecm.webdav.resource;

import net.java.dev.webdav.core.jaxrs.xml.properties.Win32CreationTime;
import net.java.dev.webdav.core.jaxrs.xml.properties.Win32FileAttributes;
import net.java.dev.webdav.core.jaxrs.xml.properties.Win32LastAccessTime;
import net.java.dev.webdav.core.jaxrs.xml.properties.Win32LastModifiedTime;
import net.java.dev.webdav.jaxrs.methods.*;
import net.java.dev.webdav.jaxrs.xml.elements.*;
import net.java.dev.webdav.jaxrs.xml.properties.LockDiscovery;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.webdav.Constants;
import org.nuxeo.ecm.webdav.Util;
import org.nuxeo.ecm.webdav.backend.WebDavBackend;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.net.URI;
import java.util.*;

import static javax.ws.rs.core.Response.Status.OK;

/**
 * An existing resource corresponds to an existing object (folder or file)
 * in the repository.
 */
public class ExistingResource extends AbstractResource {

    private static final Log log = LogFactory.getLog(ExistingResource.class);

    protected DocumentModel doc;

    protected ExistingResource(DocumentModel doc, HttpServletRequest request, WebDavBackend backend) throws Exception {
        super(doc.getPathAsString(), request, backend);
        this.doc = doc;
    }

    @DELETE
    public Response delete() throws Exception {
        if (backend.isLocked(doc.getRef()) && !backend.canUnlock(doc.getRef())) {
            return Response.status(423).build();
        }

        try {
            backend.removeItem(doc.getRef());
            backend.saveChanges();
            return Response.status(204).build();
        } catch (ClientException e) {
            log.error("Can't remove item: " + doc.getPathAsString(), e);
            backend.discardChanges();
            return Response.status(400).build();
        }
    }

    @COPY
    public Response copy(@HeaderParam("Destination") String dest,
                         @HeaderParam("Overwrite") String overwrite) throws Exception {
        return copyOrMove("COPY", dest, overwrite);
    }

    @MOVE
    public Response move(@HeaderParam("Destination") String dest,
                         @HeaderParam("Overwrite") String overwrite) throws Exception {
        if (backend.isLocked(doc.getRef()) && !backend.canUnlock(doc.getRef())) {
            return Response.status(423).build();
        }

        return copyOrMove("MOVE", dest, overwrite);
    }

    private Response copyOrMove(String method, @HeaderParam("Destination") String dest,
                                @HeaderParam("Overwrite") String overwrite) throws Exception {

        if (backend.isLocked(doc.getRef()) && !backend.canUnlock(doc.getRef())) {
            return Response.status(423).build();
        }

        dest = backend.encode(dest.getBytes(), "ISO-8859-1");

        URI destUri = new URI(dest);
        String destPath = destUri.getPath();
        while (destPath.endsWith("/")) {
            destPath = destPath.substring(0, destPath.length() - 1);
        }

        destPath = destPath.substring(
                RootResource.rootPath.length() + Constants.DAV_HOME.length(), destPath.length());
        log.info("to " + destPath);

        DocumentRef destRef = new PathRef(destPath);

        // Remove dest if it exists and the Overwrite header is set to "T".
        int status = 201;
        if (backend.exists(destRef)) {
            if ("F".equals(overwrite)) {
                return Response.status(412).build();
            }
            backend.removeItem(destRef);
            status = 204;
        }

        if (backend.isRename(doc.getPathAsString(), destPath)) {
            backend.renameItem(doc, Util.getNameFromPath(destPath));
        } else {
            String destParentPath = Util.getParentPath(destPath);
            PathRef destParentRef = new PathRef(destParentPath);
            if (!backend.exists(destParentRef)) {
                return Response.status(409).build();
            }
            if ("MOVE".equals(method)) {
                backend.moveItem(doc, destParentRef);
            } else {
                backend.copyItem(doc, destParentRef);
            }
        }
        backend.saveChanges();
        return Response.status(status).build();
    }

    // Properties

    @PROPPATCH
    public Response proppatch(@Context UriInfo uriInfo) throws Exception {
        if (backend.isLocked(doc.getRef()) && !backend.canUnlock(doc.getRef())) {
            return Response.status(423).build();
        }

        JAXBContext jc = Util.getJaxbContext();
        Unmarshaller u = jc.createUnmarshaller();
        PropertyUpdate propertyUpdate;
        try {
            propertyUpdate = (PropertyUpdate) u.unmarshal(request.getInputStream());
        } catch (JAXBException e) {
            return Response.status(400).build();
        }
        //Util.printAsXml(propertyUpdate);

        List<RemoveOrSet> list = propertyUpdate.list();

        final List<PropStat> propStats = new ArrayList<PropStat>();
        for (RemoveOrSet set : list) {
            Prop prop = set.getProp();
            List<Object> properties = prop.getProperties();
            for (Object property : properties) {
                PropStat propStat = new PropStat(new Prop(property), new Status(OK));
                propStats.add(propStat);
            }
        }

        //@TODO: patch properties if need.

        //Fake proppatch response
        final net.java.dev.webdav.jaxrs.xml.elements.Response response
                = new net.java.dev.webdav.jaxrs.xml.elements.Response(
                new HRef(uriInfo.getRequestUri()),
                null,
                null,
                null,
                new PropStat(new Prop(new Win32CreationTime()), new Status(OK)),
                new PropStat(new Prop(new Win32FileAttributes()), new Status(OK)),
                new PropStat(new Prop(new Win32LastAccessTime()), new Status(OK)),
                new PropStat(new Prop(new Win32LastModifiedTime()), new Status(OK))
        );

        return Response.status(207).entity(new MultiStatus(response)).build();
    }

    /**
     * We can't MKCOL over an existing resource.
     */
    @MKCOL
    public Response mkcol() {
        return Response.status(405).build();
    }

    @HEAD
    public Response head() {
        return Response.status(200).build();
    }

    @LOCK
    public Response lock(@Context UriInfo uriInfo) throws Exception {
        String token = null;
        Prop prop = null;
        if (backend.isLocked(doc.getRef()) && !backend.canUnlock(doc.getRef())) {
            if (!backend.canUnlock(doc.getRef())) {
                return Response.status(423).build();
            } else {
                token = backend.getCheckoutUser(doc.getRef());
                prop = new Prop(new LockDiscovery(new ActiveLock(
                        LockScope.EXCLUSIVE, LockType.WRITE, Depth.ZERO,
                        new Owner(token),
                        new TimeOut(10000L), new LockToken(new HRef("urn:uuid:" + token)),
                        new LockRoot(new HRef(uriInfo.getRequestUri()))
                )));
                return Response.ok().entity(prop)
                        .header("Lock-Token", "urn:uuid:" + token).build();
            }
        }

        token = backend.lock(doc.getRef());
        if (StringUtils.isEmpty(token)) {
            return Response.status(400).build();
        }

        prop = new Prop(new LockDiscovery(new ActiveLock(
                LockScope.EXCLUSIVE, LockType.WRITE, Depth.ZERO,
                new Owner(backend.getCheckoutUser(doc.getRef())),
                new TimeOut(10000L), new LockToken(new HRef("urn:uuid:" + token)),
                new LockRoot(new HRef(uriInfo.getRequestUri()))
        )));

        return Response.ok().entity(prop)
                .header("Lock-Token", "urn:uuid:" + token).build();
    }

    @UNLOCK
    public Response unlock() throws Exception {
        if (backend.isLocked(doc.getRef())) {
            if (!backend.canUnlock(doc.getRef())) {
                return Response.status(423).build();
            } else {
                backend.unlock(doc.getRef());
                return Response.status(204).build();
            }
        } else {
            // TODO: return an error
            return Response.status(204).build();
        }
    }

}
