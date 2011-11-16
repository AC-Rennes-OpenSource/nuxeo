package org.nuxeo.ecm.platform.suggestbox.service.suggesters;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.platform.suggestbox.service.ComponentInitializationException;
import org.nuxeo.ecm.platform.suggestbox.service.SearchDocumentsSuggestion;
import org.nuxeo.ecm.platform.suggestbox.service.Suggester;
import org.nuxeo.ecm.platform.suggestbox.service.Suggestion;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionContext;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionException;
import org.nuxeo.ecm.platform.suggestbox.service.descriptors.SuggesterDescriptor;

/**
 * Simple stateless document search suggester that propose to use the user input
 * for searching a specific field.
 */
public class DocumentSearchByPropertySuggester implements Suggester {

    protected String searchField = "fsd:ecm_fulltext";

    protected String label = "label.searchDocumentsByKeywords";

    protected String description = "";

    protected String iconURL = "/img/facetedSearch.png";

    protected boolean disabled;

    @Override
    public List<Suggestion> suggest(String userInput, SuggestionContext context)
            throws SuggestionException {
        I18nHelper i18n = I18nHelper.instanceFor(context.messages);
        String i18nLabel = i18n.translate(label, userInput);
        Suggestion suggestion = new SearchDocumentsSuggestion(i18nLabel,
                iconURL).withSearchCriterion(searchField, userInput);
        if (disabled) {
            suggestion.disable();
        }
        if (description != null) {
            suggestion.withDescription(i18n.translate(description, userInput));
        }
        return Collections.singletonList(suggestion);
    }

    @Override
    public void initWithParameters(SuggesterDescriptor descriptor)
            throws ComponentInitializationException {
        Map<String, String> params = descriptor.getParameters();
        searchField = params.get("searchField");
        label = params.get("label");
        String iconURL = params.get("iconURL");
        if (iconURL != null) {
            this.iconURL = iconURL;
        }
        description = params.get("description");
        String disabled = params.get("disabled");
        if (disabled != null) {
            this.disabled = Boolean.valueOf(disabled);
        }
        if (searchField == null || label == null) {
            throw new ComponentInitializationException(
                    String.format("Could not initialize suggester '%s': "
                            + "type, propertyPath and label"
                            + " are mandatory parameters", descriptor.getName()));
        }
    }

}
