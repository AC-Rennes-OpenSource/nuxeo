/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Tiago Cardoso <tcardoso@nuxeo.com>
 */
package org.nuxeo.ecm.platform.threed.tests;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.rendition.Rendition;
import org.nuxeo.ecm.platform.rendition.service.RenditionDefinition;
import org.nuxeo.ecm.platform.rendition.service.RenditionService;
import org.nuxeo.ecm.platform.threed.BatchConverterHelper;
import org.nuxeo.ecm.platform.threed.ThreeD;
import org.nuxeo.ecm.platform.threed.ThreeDRenderView;
import org.nuxeo.ecm.platform.threed.TransmissionThreeD;
import org.nuxeo.ecm.platform.threed.service.ThreeDService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.threed.ThreeDDocumentConstants.RENDER_VIEWS_PROPERTY;
import static org.nuxeo.ecm.platform.threed.ThreeDDocumentConstants.TRANSMISSIONS_PROPERTY;
import static org.nuxeo.ecm.platform.threed.rendition.ThreeDRenditionDefinitionProvider.THREED_RENDER_VIEW_RENDITION_KIND;
import static org.nuxeo.ecm.platform.threed.rendition.ThreeDRenditionDefinitionProvider.THREED_TRANSMISSION_RENDITION_KIND;

/**
 * Test 3D renditions
 *
 * @since 8.4
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.commandline.executor", "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.actions",
        "org.nuxeo.ecm.platform.rendition.api", "org.nuxeo.ecm.platform.rendition.core",
        "org.nuxeo.ecm.platform.picture.api", "org.nuxeo.ecm.platform.picture.core" })
@LocalDeploy({ "org.nuxeo.ecm.platform.threed.api", "org.nuxeo.ecm.platform.threed.core",
        "org.nuxeo.ecm.platform.threed.convert" })
public class TestThreeDRenditions {

    public static final List<String> EXPECTED_ALL_RENDITION_DEFINITION_NAMES = Arrays.asList("left", "front",
            "isometric", "top", "11", "03", "33", "100");

    public static final List<String> EXPECTED_FILTERED_RENDITION_DEFINITION_NAMES = Arrays.asList("front", "left", "11",
            "33", "100");

    public static final List<String> THREED_RENDITION_DEFINITION_KINDS = Arrays.asList(
            THREED_RENDER_VIEW_RENDITION_KIND, THREED_TRANSMISSION_RENDITION_KIND);

    protected static final String TEST_MODEL = "suzanne";

    @Inject
    protected CoreSession session;

    @Inject
    protected RenditionService renditionService;

    @Inject
    protected RuntimeHarness runtimeHarness;

    @Inject
    protected ThreeDService threeDService;

    @Inject
    protected EventServiceAdmin eventServiceAdmin;

    @Before
    public void setUp() {
        eventServiceAdmin.setListenerEnabledFlag("threeDBatchGenerationListener", false);
    }

    protected void updateThreeDDocument(DocumentModel doc, ThreeD threeD) {
        Collection<Blob> results = threeDService.batchConvert(threeD);

        List<ThreeDRenderView> threeDRenderViews = BatchConverterHelper.getRenders(results);
        List<TransmissionThreeD> colladaThreeDs = BatchConverterHelper.getTransmissons(results);
        List<TransmissionThreeD> transmissionThreeDs = colladaThreeDs.stream()
                                                                     .map(threeDService::convertColladaToglTF)
                                                                     .collect(Collectors.toList());

        List<Map<String, Serializable>> transmissionList = new ArrayList<>();
        transmissionList.addAll(
            transmissionThreeDs.stream().map(TransmissionThreeD::toMap).collect(Collectors.toList()));
        doc.setPropertyValue(TRANSMISSIONS_PROPERTY, (Serializable) transmissionList);

        List<Map<String, Serializable>> renderViewList = new ArrayList<>();
        renderViewList.addAll(
            threeDRenderViews.stream().map(ThreeDRenderView::toMap).collect(Collectors.toList()));
        doc.setPropertyValue(RENDER_VIEWS_PROPERTY, (Serializable) renderViewList);

    }

    protected List<RenditionDefinition> getThreeDRenditionDefinitions(DocumentModel doc) {
        return renditionService.getAvailableRenditionDefinitions(doc).stream().filter(renditionDefinition ->
            THREED_RENDITION_DEFINITION_KINDS.contains(renditionDefinition.getKind())).collect(Collectors.toList());
    }

    protected List<Rendition> getThreeDAvailableRenditions(DocumentModel doc, boolean onlyVisible) {
        return renditionService.getAvailableRenditions(doc, onlyVisible).stream().filter(rendition ->
            THREED_RENDITION_DEFINITION_KINDS.contains(rendition.getKind())).collect(Collectors.toList());
    }

    protected static ThreeD getTestThreeD() throws IOException {
        List<Blob> resources = new ArrayList<>();
        Blob blob, main;
        try (InputStream is = TestThreeDRenditions.class.getResourceAsStream("/test-data/" + TEST_MODEL + ".obj")) {
            assertNotNull(String.format("Failed to load resource: %s.obj", TEST_MODEL), is);
            main = Blobs.createBlob(is);
            main.setFilename(TEST_MODEL + ".obj");

        }

        try (InputStream is = TestThreeDRenditions.class.getResourceAsStream("/test-data/" + TEST_MODEL + ".mtl")) {
            assertNotNull(String.format("Failed to load resource: %s.mtl", TEST_MODEL), is);
            blob = Blobs.createBlob(is);
            blob.setFilename(TEST_MODEL + ".mtl");
            resources.add(blob);
        }
        return new ThreeD(main, resources);
    }

    @Test
    public void shouldExposeAllAutomaticThreeDAsRenditions() throws IOException {
        ThreeD threeD = getTestThreeD();
        DocumentModel doc = session.createDocumentModel("/", "threed", "ThreeD");
        doc = session.createDocument(doc);

        assertEquals(0, getThreeDRenditionDefinitions(doc).size());

        updateThreeDDocument(doc, threeD);

        List<RenditionDefinition> renditionDefinitions = getThreeDRenditionDefinitions(doc);
        assertEquals(8, renditionDefinitions.size());
        for (RenditionDefinition definition : renditionDefinitions) {
            assertTrue(EXPECTED_ALL_RENDITION_DEFINITION_NAMES.contains(definition.getName()));
        }

        List<Rendition> availableRenditions = getThreeDAvailableRenditions(doc, false);
        assertEquals(8, availableRenditions.size());
        // they are all visible
        availableRenditions = getThreeDAvailableRenditions(doc, true);
        assertEquals(8, availableRenditions.size());
    }

    @Test
    public void shouldExposeOnlyExposedAsRenditions() throws Exception {
        ThreeD threeD = getTestThreeD();
        DocumentModel doc = session.createDocumentModel("/", "threed", "ThreeD");
        doc = session.createDocument(doc);
        runtimeHarness.deployContrib("org.nuxeo.ecm.platform.threed.core",
                "OSGI-INF/threed-service-contrib-override.xml");

        assertEquals(0, getThreeDRenditionDefinitions(doc).size());

        updateThreeDDocument(doc, threeD);

        List<RenditionDefinition> renditionDefinitions = getThreeDRenditionDefinitions(doc);
        assertEquals(5, renditionDefinitions.size());
        for (RenditionDefinition definition : renditionDefinitions) {
            assertTrue(EXPECTED_FILTERED_RENDITION_DEFINITION_NAMES.contains(definition.getName()));
        }

        List<Rendition> availableRenditions = getThreeDAvailableRenditions(doc, false);
        assertEquals(5, availableRenditions.size());
        // they are all but one visible
        availableRenditions = getThreeDAvailableRenditions(doc, true);
        assertEquals(4, availableRenditions.size());

        runtimeHarness.undeployContrib("org.nuxeo.ecm.platform.threed.core",
                "OSGI-INF/threed-service-contrib-override.xml");
    }

    @Test
    public void testBatchConverterHelper() throws Exception {
        ThreeD threeD = getTestThreeD();
        Collection<Blob> results = threeDService.batchConvert(threeD);
        List<ThreeDRenderView> renderviews = BatchConverterHelper.getRenders(results);
        List<TransmissionThreeD> transmissions = BatchConverterHelper.getTransmissons(results);
        for (ThreeDRenderView rV : renderviews) {
            assertEquals(1, threeDService.getAutomaticRenderViews().stream()
                .filter(aRV -> aRV.getName().equals(rV.getTitle())).count());
        }
        for (TransmissionThreeD tTD : transmissions) {
            assertEquals(1, threeDService.getAvailableLODs().stream()
                .filter(aLOD -> aLOD.getName().equals(tTD.getName())).count());
        }
    }

}
