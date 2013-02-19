package org.nuxeo.ecm.platform.groups.audit.service.acl.job.publish;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.InvalidChainException;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationParameters;
import org.nuxeo.ecm.automation.core.mail.Mailer;
import org.nuxeo.ecm.automation.core.operations.notification.SendMail;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.api.Framework;

public class PublishByMail implements IResultPublisher {
    private static final Log log = LogFactory.getLog(PublishByMail.class);

    public static final String PROPERTY_MAILFROM = "noreply@nuxeo.com";

    public static final String PROPERTY_ACLAUDIT_SENDMAIL_CHAIN = "ACL.Audit.SendMail";

    public static String FROM = "noreply@nuxeo.com";

    protected final AutomationService automation = Framework.getLocalService(AutomationService.class);

    protected FileBlob file;

    protected String documentName = "";

    protected String repository;

    protected String to;

    protected String defaultFrom;

    public PublishByMail(FileBlob fb, String to,
            String defaultFrom, String repository) {
        this.file = fb;
        this.repository = repository;
        this.to = to;
        this.defaultFrom = defaultFrom;
    }

    public void publish() throws ClientException {
        reconnectAndSendMail();
    }

    /* repository required to have a session and to build a document */
    protected void reconnectAndSendMail() throws ClientException {
        new UnrestrictedSessionRunner(repository) {
            @Override
            public void run() throws ClientException {
                DocumentModel docToSend = createDocument(session, file,
                        documentName, documentName);
                doCallOperationSendMail(session, docToSend, to, defaultFrom);
                log.debug("audit sent");
            }
        }.runUnrestricted();
    }

    protected void doCallOperationSendMail(CoreSession session,
            DocumentModel docToSend, String to, String defaultFrom)
            throws ClientException {
        String from = Framework.getProperty(PROPERTY_MAILFROM, defaultFrom);
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(docToSend);

        try {
            OperationChain chain = new OperationChain(
                    PROPERTY_ACLAUDIT_SENDMAIL_CHAIN);
            OperationParameters params = chain.add(SendMail.ID);// findParameters(chain,
                                                                // SendMail.ID);
            if (params == null) {
                log.error("failed to retrieve operation " + SendMail.ID
                        + " in chain " + chain);
                return;
            }
            params.set("from", from);
            params.set("to", to);
            params.set("message", "ACL Audit report");
            params.set("subject", "ACL Audit report");

            String[] str = { "file:content" };
            params.set("files", new StringList(str));
         // TODO: see SendMail test case where we can directly pass a blob

            logMailerConfiguration();

            // chain.g
            log.debug("Automation run " + PROPERTY_ACLAUDIT_SENDMAIL_CHAIN
                    + " for " + to);
            automation.run(ctx, chain);
            log.debug("Automation done " + PROPERTY_ACLAUDIT_SENDMAIL_CHAIN
                    + " for " + to);
        } catch (InvalidChainException e) {
            throw new ClientException(e);
        } catch (OperationException e) {
            throw new ClientException(e);
        } catch (Exception e) {
            throw new ClientException(e);
        }
    }

    protected OperationParameters findParameters(OperationChain chain, String id) {
        List<OperationParameters> params = chain.getOperations();
        for (OperationParameters p : params)
            if (p.id().equals(id))
                return p;
        return null;
    }

    protected DocumentModel createDocument(CoreSession session, Blob blob,
            String title, String filename) throws ClientException {
        DocumentModel document = session.createDocumentModel("File");
        document.setPropertyValue("file:content", (Serializable) blob);
        document.setPropertyValue("file:filename", filename);
        document.setPropertyValue("dublincore:title", title);
        return document;
    }

    protected void logMailerConfiguration(){
        Mailer m = SendMail.COMPOSER.getMailer();
        log.info("mail.smtp.auth:"+m.getConfiguration().get("mail.smtp.auth"));
        log.info("mail.smtp.starttls.enable:"+m.getConfiguration().get("mail.smtp.starttls.enable"));
        log.info("mail.smtp.host:"+m.getConfiguration().get("mail.smtp.host"));
        log.info("mail.smtp.user:"+m.getConfiguration().get("mail.smtp.user"));
        log.info("mail.smtp.password:"+m.getConfiguration().get("mail.smtp.password"));
    }
}
