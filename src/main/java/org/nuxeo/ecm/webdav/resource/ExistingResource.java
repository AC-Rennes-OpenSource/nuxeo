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

import net.java.dev.webdav.jaxrs.methods.*;
import net.java.dev.webdav.jaxrs.xml.elements.*;
import net.java.dev.webdav.jaxrs.xml.properties.LockDiscovery;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.webdav.Constants;
import org.nuxeo.ecm.webdav.Util;
import org.nuxeo.ecm.webdav.locking.LockManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.net.URI;

/**
 * An existing resource corresponds to an existing object (folder or file)
 * in the repository.
 */
public class ExistingResource extends AbstractResource {

    private static final Log log = LogFactory.getLog(ExistingResource.class);

    protected DocumentModel doc;

    protected ExistingResource(String path, DocumentModel doc, HttpServletRequest request) throws Exception {
        super(path, request);
        this.doc = doc;
    }

    @DELETE
    public Response delete() throws Exception {
        if (lockManager.isLocked(path)) {
            String token = getTokenFromHeaders("if");
            if (!lockManager.canUnlock(path, token)) {
                Util.endTransaction();
                return Response.status(423).build();
            }
        }

        DocumentRef ref = new PathRef(path);
        session.removeDocument(ref);
        session.save();
        Util.endTransaction();
        return Response.ok().build();
    }

    @COPY
    public Response copy(@HeaderParam("Destination") String dest,
            @HeaderParam("Overwrite") String overwrite) throws Exception {
        return copyOrMove("COPY", dest, overwrite);
    }

    @MOVE
    public Response move(@HeaderParam("Destination") String dest,
            @HeaderParam("Overwrite") String overwrite) throws Exception {
        if (lockManager.isLocked(path)) {
            String token = getTokenFromHeaders("if");
            if (!lockManager.canUnlock(path, token)) {
                Util.endTransaction();
                return Response.status(423).build();
            }
        }

        return copyOrMove("MOVE", dest, overwrite);
    }

    private Response copyOrMove(String method, @HeaderParam("Destination") String dest,
            @HeaderParam("Overwrite") String overwrite) throws Exception {
        URI destUri = new URI(dest);
        String destPath = destUri.getPath();
        while (destPath.endsWith("/")) {
            destPath = destPath.substring(0, destPath.length() - 1);
        }

        destPath = destPath.substring(
                RootResource.rootPath.length() + Constants.DAV_HOME.length(), destPath.length());
        log.info("to " + destPath);

        if (lockManager.isLocked(destPath)) {
            String token = getTokenFromHeaders("if");
            if (!LockManager.getInstance().canUnlock(path, token)) {
                Util.endTransaction();
                return Response.status(423).build();
            }
        }

        DocumentRef sourceRef = new PathRef(path);
        DocumentRef destRef = new PathRef(destPath);

        String destParentPath = Util.getParentPath(destPath);
        PathRef destParentRef = new PathRef(destParentPath);
        if (!session.exists(destParentRef)) {
            Util.endTransaction();
            return Response.status(409).build();
        }

        // Remove dest if it exists and the Overwrite header is set to "T".
        int status = 201;
        if (session.exists(destRef)) {
            if ("F".equals(overwrite)) {
                return Response.status(412).build();
            }
            session.removeDocument(destRef);
            status = 204;
        }

        session.copy(sourceRef, destParentRef, Util.getNameFromPath(destPath));
        if ("MOVE".equals(method)) {
            session.removeDocument(sourceRef);
        }
        session.save();

        Util.endTransaction();
        return Response.status(status).build();
    }

    // Properties

    @PROPPATCH
    public Response proppatch(@Context UriInfo uriInfo) throws Exception {
        if (lockManager.isLocked(path)) {
            Util.endTransaction();
            return Response.status(423).build();
        }

        JAXBContext jc = Util.getJaxbContext();
        Unmarshaller u = jc.createUnmarshaller();
        try {
            PropertyUpdate propertyUpdate = (PropertyUpdate) u.unmarshal(request.getInputStream());
        } catch (JAXBException e) {
            Util.endTransaction();
            return Response.status(400).build();
        }
        //printXml(propertyUpdate);
        Util.endTransaction();
        return Response.ok().build();
    }

    @LOCK
    public Response lock() throws Exception {
        String token = getTokenFromHeaders("if");
        if (lockManager.isLocked(path) && !lockManager.canUnlock(path, token)) {
            Util.endTransaction();
            return Response.status(423).build();
        }

        LockInfo lockInfo;
        if (request.getHeader("content-length") != null) {
            try {
                Unmarshaller u = Util.getUnmarshaller();
                lockInfo = (LockInfo) u.unmarshal(request.getInputStream());
                Util.printAsXml(lockInfo);
                token = lockManager.lock(path);
            } catch (JAXBException e) {
                log.error(e);
                Util.endTransaction();
                // FIXME: check this is the right response code
                return Response.status(400).build();
            }
        } else if (token != null) {
            // OK
        } else {
            Util.endTransaction();
            return Response.status(400).build();
        }

        Prop prop = new Prop(new LockDiscovery(new ActiveLock(
                LockScope.EXCLUSIVE, LockType.WRITE, Depth.ZERO,
                new Owner("toto"),
                new TimeOut(10000L), new LockToken(new HRef("urn:uuid:" + token)),
                new LockRoot(new HRef("http://asdasd/"))
        )));
        Util.endTransaction();
        return Response.ok().entity(prop)
                .header("Lock-Token", "urn:uuid:" + token).build();
    }

    @UNLOCK
    public Response unlock() {
        if (lockManager.isLocked(path)) {
            String token = getTokenFromHeaders("lock-token");
            if (!lockManager.canUnlock(path, token)) {
                Util.endTransaction();
                return Response.status(423).build();
            }
            lockManager.unlock(path);
            Util.endTransaction();
            return Response.status(204).build();
        } else {
            // TODO: return an error
            Util.endTransaction();
            return Response.status(204).build();
        }
    }

    /**
     * We can't MKCOL over an existing resource.
     */
    @MKCOL
    public Response mkcol() {
        Util.endTransaction();
        return Response.status(405).build();
    }

}
