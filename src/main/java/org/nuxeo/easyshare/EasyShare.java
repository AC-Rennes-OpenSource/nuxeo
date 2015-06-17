/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Michal Obrebski - Nuxeo
 */

package org.nuxeo.easyshare;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.ec.notification.email.EmailHelper;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.notification.api.Notification;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * The root entry for the WebEngine module.
 *
 * @author mikeobrebski
 */
@Path("/easyshare")
@Produces("text/html;charset=UTF-8")
@WebObject(type = "EasyShare")
public class EasyShare extends ModuleRoot {

  private static final String DEFAULT_PAGE_INDEX = "0";
  private static final Long PAGE_SIZE = 20L;
  private static final String SHARE_DOC_TYPE = "EasyShareFolder";
  private static AutomationService automationService;
  protected final Log log = LogFactory.getLog(EasyShare.class);

  @GET
  public Object doGet() {
    return getView("index");
  }

  public EasyShareUnrestrictedRunner buildUnrestrictedRunner(final String docId, final Long pageIndex) {

    return new EasyShareUnrestrictedRunner() {
      @Override
      public Object run(CoreSession session, IdRef docRef) throws ClientException {
        if (session.exists(docRef)) {
          DocumentModel docShare = session.getDocument(docRef);

          if (!SHARE_DOC_TYPE.equals(docShare.getType())) {
            return Response.serverError().status(Response.Status.NOT_FOUND).build();
          }

          if (!checkIfShareIsValid(docShare)) {
            return getView("expired").arg("docShare", docShare);
          }

          DocumentModel document = session.getDocument(new IdRef(docId));

          String query = buildQuery(document);

          if (query == null) {
            return getView("denied");
          }

          try {

            OperationContext opCtx = new OperationContext(session);
            OperationChain chain = new OperationChain("getEasyShareContent");
            chain.add("Document.Query")
                .set("query", query)
                .set("currentPageIndex", pageIndex)
                .set("pageSize", PAGE_SIZE);

            PaginableDocumentModelListImpl paginable = (PaginableDocumentModelListImpl) getAutomationService().run(opCtx, chain);

            OperationContext ctx = new OperationContext(session);
            ctx.setInput(docShare);

            // Audit Log
            Map<String, Object> params = new HashMap();
            params.put("event", "Access");
            params.put("category", "Document");
            params.put("comment", "IP: " + request.getRemoteAddr());
            getAutomationService().run(ctx, "Audit.Log", params);

            return getView("folderList")
                .arg("isFolder", document.isFolder() && !SHARE_DOC_TYPE.equals(document.getType()))  //Backward compatibility to non-collection
                .arg("currentPageIndex", paginable.getCurrentPageIndex())
                .arg("numberOfPages", paginable.getNumberOfPages())
                .arg("docShare", docShare)
                .arg("docList", paginable)
                .arg("previousPageAvailable", paginable.isPreviousPageAvailable())
                .arg("nextPageAvailable", paginable.isNextPageAvailable())
                .arg("currentPageStatus", paginable.getProvider().getCurrentPageStatus());

          } catch (Exception ex) {
            log.error(ex.getMessage());
            return getView("denied");
          }

        } else {
          return getView("denied");
        }
      }
    };
  }


  protected static String buildQuery(DocumentModel documentModel) {

	  //Backward compatibility to non-collection
    if (documentModel.isFolder() && !SHARE_DOC_TYPE.equals(documentModel.getType())) {
      return " SELECT * FROM Document WHERE ecm:parentId = '" + documentModel.getId() + "' AND " +
          "ecm:mixinType != 'HiddenInNavigation' AND " +
          "ecm:mixinType != 'NotCollectionMember' AND " +
          "ecm:isCheckedInVersion = 0 AND " +
          "ecm:currentLifeCycleState != 'deleted'";

    } else if (SHARE_DOC_TYPE.equals(documentModel.getType())) {
      return "SELECT * FROM Document where ecm:mixinType != 'HiddenInNavigation' AND " +
          "ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted' " +
          "AND collectionMember:collectionIds/* = '" + documentModel.getId() + "'" +
          "OR ecm:parentId = '" + documentModel.getId() + "'";
    }

    return null;
  }

  private boolean checkIfShareIsValid(DocumentModel docShare) {
    Date today = new Date();
    if (today.after(docShare.getProperty("dc:expired").getValue(Date.class))) {

      //Email notification
      Map mail = new HashMap<>();
      sendNotification("easyShareExpired", docShare, mail);

      return false;

    }
    return true;
  }


  private static AutomationService getAutomationService() {
    if (automationService == null) {
      automationService = Framework.getService(AutomationService.class);
    }
    return automationService;
  }


  @Path("{shareId}/{folderId}")
  @GET
  public Object getFolderListing(@PathParam("shareId") String shareId, @PathParam("folderId") final String folderId,
                                 @DefaultValue(DEFAULT_PAGE_INDEX) @QueryParam("p") final Long pageIndex) {
    return buildUnrestrictedRunner(folderId, pageIndex).runUnrestricted(shareId);
  }

  @Path("{shareId}")
  @GET
  public Object getShareListing(@PathParam("shareId") String shareId,
                                @DefaultValue(DEFAULT_PAGE_INDEX) @QueryParam("p") Long pageIndex) {
    return buildUnrestrictedRunner(shareId, pageIndex).runUnrestricted(shareId);
  }

  public String getFileName(DocumentModel doc) throws ClientException {
    BlobHolder blobHolder = doc.getAdapter(BlobHolder.class);
    if (blobHolder != null && blobHolder.getBlob() != null) {
      return blobHolder.getBlob().getFilename();
    }
    return doc.getName();
  }

  @GET
  @Path("{shareId}/{fileId}/{fileName}")
  public Response getFileStream(@PathParam("shareId") final String shareId, @PathParam("fileId") String fileId) throws ClientException {

    return (Response) new EasyShareUnrestrictedRunner() {
      @Override
      public Object run(CoreSession session, IdRef docRef) throws ClientException {
        if (session.exists(docRef)) {
          try {
            DocumentModel doc = session.getDocument(docRef);
            DocumentModel docShare = session.getDocument(new IdRef(shareId));

            if (!checkIfShareIsValid(docShare)) {
              return Response.serverError().status(Response.Status.NOT_FOUND).build();
            }

            Blob blob = doc.getAdapter(BlobHolder.class).getBlob();

            // Audit Log
            OperationContext ctx = new OperationContext(session);
            ctx.setInput(doc);

            // Audit.Log automation parameter setting
            Map<String, Object> params = new HashMap<>();
            params.put("event", "Download");
            params.put("category", "Document");
            params.put("comment", "IP: " + request.getRemoteAddr());
            AutomationService service = Framework.getLocalService(AutomationService.class);
            service.run(ctx, "Audit.Log", params);

            if (doc.isProxy()) {
              DocumentModel liveDoc = session.getSourceDocument(docRef);
              ctx.setInput(liveDoc);
              service.run(ctx, "Audit.Log", params);
            }

            // Email notification
            Map mail = new HashMap<>();
            mail.put("filename", blob.getFilename());
            sendNotification("easyShareDownload", docShare, mail);

            return Response.ok(blob.getStream(), blob.getMimeType()).build();

          } catch (Exception ex) {
            log.error("error ", ex);
            return Response.serverError().status(Response.Status.NOT_FOUND).build();
          }

        } else {
          return Response.serverError().status(Response.Status.NOT_FOUND).build();
        }
      }
    }.runUnrestricted(fileId);

  }

  public void sendNotification(String notification, DocumentModel docShare, Map<String, Object> mail) {

    Boolean hasNotification = docShare.getProperty("eshare:hasNotification").getValue(Boolean.class);

    if (hasNotification) {
      //Email notification
      String email = docShare.getProperty("eshare:contactEmail").getValue(String.class);
      try {
        log.debug("Easyshare: starting email");
        EmailHelper emailHelper = new EmailHelper();
        Map<String, Object> mailProps = new Hashtable();
        mailProps.put("mail.from", Framework.getProperty("mail.from", "system@nuxeo.com"));
        mailProps.put("mail.to", email);
        mailProps.put("ip", this.request.getRemoteAddr());
        mailProps.put("docShare", docShare);

        try {
          Notification notif = NotificationServiceHelper.getNotificationService().getNotificationByName(notification);

          if (notif.getSubjectTemplate() != null) {
            mailProps.put(NotificationConstants.SUBJECT_TEMPLATE_KEY, notif.getSubjectTemplate());
          }

          mailProps.put(NotificationConstants.SUBJECT_KEY, NotificationServiceHelper.getNotificationService().getEMailSubjectPrefix() + " " + notif.getSubject());
          mailProps.put(NotificationConstants.TEMPLATE_KEY, notif.getTemplate());

          mailProps.putAll(mail);

          emailHelper.sendmail(mailProps);

        } catch (ClientException e) {
          log.warn(e.getMessage());
        }

        log.debug("Easyshare: completed email");
      } catch (Exception ex) {
        log.error("Cannot send easyShare notification email", ex);
      }
    }
  }

}