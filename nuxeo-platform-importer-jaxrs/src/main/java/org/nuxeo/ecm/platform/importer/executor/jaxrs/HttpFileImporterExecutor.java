package org.nuxeo.ecm.platform.importer.executor.jaxrs;

import java.io.File;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.importer.base.GenericMultiThreadedImporter;
import org.nuxeo.ecm.platform.importer.source.FileWithMetadataSourceNode;
import org.nuxeo.ecm.platform.importer.source.SourceNode;

public class HttpFileImporterExecutor extends AbstractJaxRSImporterExecutor {

    private static final Log log = LogFactory.getLog(HttpFileImporterExecutor.class);

    @Override
    protected Log getJavaLogger() {
        return log;
    }

    @GET
    @Path("run")
    @Produces("text/plain; charset=UTF-8")
    public String run(@QueryParam("inputPath") String inputPath, @QueryParam("targetPath") String targetPath, @QueryParam("batchSize") Integer batchSize , @QueryParam("nbThreads") Integer nbTheards, @QueryParam("interactive") Boolean interactive) throws Exception {
        File srcFile = new File(inputPath);
        SourceNode source = new FileWithMetadataSourceNode(srcFile);
        Runnable task = new GenericMultiThreadedImporter(source, targetPath, batchSize, nbTheards, getLogger());
        return doRun(task, interactive);
    }

}
