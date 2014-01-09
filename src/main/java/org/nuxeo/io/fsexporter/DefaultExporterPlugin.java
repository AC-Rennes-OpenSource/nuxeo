package org.nuxeo.io.fsexporter;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

public class DefaultExporterPlugin implements FSExporterPlugin {

    @Override
    public DocumentModelList getChildren(CoreSession session,
            DocumentModel doc, boolean ExportDeletedDocuments,
            String myPageProvider) throws ClientException, Exception {

        PageProviderService ppService = null;
        try {
            ppService = Framework.getService(PageProviderService.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (Serializable) session);

        PageProvider<DocumentModel> pp = null;

        if (myPageProvider == null) {
            myPageProvider = "GET_CHILDREN_FSEXPORTER";
        }

        // call the page provider
        try {
            pp = (PageProvider<DocumentModel>) ppService.getPageProvider(
                    myPageProvider, null, null, null, props,
                    new Object[] { doc.getId() });
        } catch (Exception e) {
            e.printStackTrace();
        }

        int countPages = 1;
        // get all the documents of the first page
        DocumentModelList children = new DocumentModelListImpl(
                pp.getCurrentPage());

        // if there is more than one page, get the children of all the other pages and put into one list
        List<DocumentModel> childrenTemp = new ArrayList<DocumentModel>();

        if (pp.getNumberOfPages() > 1) {
            while (countPages < pp.getNumberOfPages()) {
                pp.nextPage();
                childrenTemp = pp.getCurrentPage();
                for (DocumentModel childTemp : childrenTemp) {
                    children.add(childTemp);
                }
                countPages++;
            }
        }
        // return the complete list of documents
        return children;
    }

    @Override
    public File serialize(CoreSession session, DocumentModel docfrom, String fsPath)
            throws Exception {
        File folder = null;
        File newFolder = null;
        folder = new File(fsPath);

        // if target directory doesn't exist, create it
        if (!folder.exists()) {
            folder.mkdir();
        }

        if (docfrom.hasFacet("Folderish")) {
            newFolder = new File(fsPath + "/" + docfrom.getName());
            newFolder.mkdir();
        }

        // get all the blobs of the blobholder
        BlobHolder myblobholder = docfrom.getAdapter(BlobHolder.class);
        if (myblobholder != null) {
            java.util.List<Blob> listblobs = myblobholder.getBlobs();
            int i = 1;
            for (Blob blob : listblobs) {
                // call the method to determine the name of the exported file
                String FileNameToExport = getFileName(blob, docfrom, folder, i);
                // export the file to the target file system
                File target = new File(folder, FileNameToExport);
                //exportFileInXML(session, docfrom, fsPath + "/" + FileNameToExport);
                blob.transferTo(target);
                i++;
            }
        }
        if (newFolder != null) {
            folder = newFolder;
        }
        return folder;
    }


    protected String getFileName(Blob blob, DocumentModel docfrom, File folder, int i)  {
        String prefix = "";
        // if not principal file, prefix = name of the file which contains
        // the blobs
        if (i != 1) {
            prefix = docfrom.getName() + "-";
        }

     // if already existing file, prefix with "timestamp"
        File alreadyExistingBlob = new File(folder, prefix
                + blob.getFilename());

        if (alreadyExistingBlob.exists()) {
            prefix = String.valueOf(new Date().getTime()) + "-" + prefix;
        }

        return prefix + blob.getFilename();
    }
}
