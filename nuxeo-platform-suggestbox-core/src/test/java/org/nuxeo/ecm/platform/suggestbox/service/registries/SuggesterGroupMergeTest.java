package org.nuxeo.ecm.platform.suggestbox.service.registries;

import java.util.List;
import java.util.ArrayList;

import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.*;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionService;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionServiceImpl;
import org.nuxeo.ecm.platform.suggestbox.service.descriptors.SuggesterGroupDescriptor;
import org.nuxeo.ecm.platform.suggestbox.service.descriptors.SuggesterGroupItemDescriptor;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.suggestbox.core",
        "org.nuxeo.ecm.platform.suggestbox.core.test" })
public class SuggesterGroupMergeTest {

    @Inject
    protected SuggestionService suggestionService;

    /**
     * Tests the merge of the 'searchbox' suggesterGroup contribution.
     * <p>
     * Before merge, the list of suggesters for this suggesterGroup is:
     * ["documentLookupByTitle", "searchByUsersAndGroups", "searchByDate",
     * "searchByKeywords"]
     * <p>
     * After merge, it should be: ["myNewSuggesterBegin",
     * "myNewSuggesterBeforeUsers", "searchByUsersAndGroups",
     * "myNewSuggesterAfterUsers", "searchByDate", "searchByKeywords",
     * "myNewSuggesterEnd", "myNewSuggesterVeryEnd"]
     * <p>
     * See following steps for details.
     * <p>
     * 1/ Remove a non-existent suggester 'nonExistentSuggester' => nothing
     * should be done.
     * <p>
     * 2/ Remove an existing suggester 'documentLookupByTitle' => should be
     * removed.
     * <p>
     * 3/ Append a suggester with the name of an existing suggester
     * 'searchByUsersAndGroups' => nothing should be done.
     * <p>
     * 4/ Append a suggester 'myNewSuggesterBegin' before a non-existent
     * suggester => should be appended at the beginning of the suggesters list.
     * <p>
     * 5/ Append a suggester 'myNewSuggesterBeforeUsers' before an existing
     * suggester 'searchByUsersAndGroups' => should be appended before the
     * existing suggester.
     * <p>
     * 6/ Append a suggester 'myNewSuggesterEnd' after a non-existent suggester
     * => should be appended at the end of the suggesters list.
     * <p>
     * 7/ Append a suggester 'myNewSuggesterAfterUsers' after an existing
     * suggester 'searchByUsersAndGroups' => should be appended after the
     * existing suggester.
     * <p>
     * 8/ Append a suggester 'myNewSuggesterVeryEnd' with no particular
     * attributes => should be appended at the end of the suggesters list.
     */
    @Test
    public void testSuggesterGroupMerge() throws ClientException {

        // check service implementation
        assertTrue(suggestionService instanceof SuggestionServiceImpl);

        // check suggesterGroup registry
        SuggesterGroupRegistry suggesterGroups = ((SuggestionServiceImpl) suggestionService).getSuggesterGroups();
        assertNotNull(suggesterGroups);

        // check service supports merge
        assertTrue(suggesterGroups.isSupportingMerge());

        // check 'searchbox' suggesterGroup
        SuggesterGroupDescriptor sgd = suggesterGroups.getSuggesterGroupDescriptor("searchbox");
        assertNotNull(sgd);

        // check 'searchbox' suggesterGroup's merged suggesters
        List<SuggesterGroupItemDescriptor> suggesters = sgd.getSuggesters();
        List<SuggesterGroupItemDescriptor> expectedSuggesters = new ArrayList<SuggesterGroupItemDescriptor>();
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "myNewSuggesterBegin"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "myNewSuggesterBeforeUsers"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "searchByUsersAndGroups"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "myNewSuggesterAfterUsers"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor("searchByDate"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "searchByKeywords"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "myNewSuggesterEnd"));
        expectedSuggesters.add(new SuggesterGroupItemDescriptor(
                "myNewSuggesterVeryEnd"));
        assertEquals(expectedSuggesters, suggesters);
    }
}
