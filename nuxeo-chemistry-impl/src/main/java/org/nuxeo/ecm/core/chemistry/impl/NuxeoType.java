/*
 * Copyright 2009 Nuxeo SA <http://nuxeo.com>
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
 * Authors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.chemistry.impl;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.BaseType;
import org.apache.chemistry.Choice;
import org.apache.chemistry.ContentStreamPresence;
import org.apache.chemistry.PropertyDefinition;
import org.apache.chemistry.PropertyType;
import org.apache.chemistry.Type;
import org.apache.chemistry.Updatability;
import org.apache.chemistry.impl.simple.SimplePropertyDefinition;
import org.apache.chemistry.impl.simple.SimpleType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.Namespace;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

public class NuxeoType implements Type {

    private static final Log log = LogFactory.getLog(NuxeoType.class);

    public static final String NX_DC_CREATED = "dc:created";

    public static final String NX_DC_CREATOR = "dc:creator";

    public static final String NX_DC_MODIFIED = "dc:modified";

    private final DocumentType documentType;

    private final Map<String, PropertyDefinition> propertyDefinitions;

    private final ContentStreamPresence contentStreamAllowed;

    public NuxeoType(DocumentType documentType) {
        this.documentType = documentType;

        Map<String, PropertyDefinition> map = new HashMap<String, PropertyDefinition>();
        for (PropertyDefinition def : SimpleType.getBasePropertyDefinitions(getBaseType())) {
            map.put(def.getId(), def);
        }

        boolean hasFileSchema = false;
        for (Schema schema : documentType.getSchemas()) {
            if ("file".equals(schema.getName())) {
                hasFileSchema = true;
            }
            for (Field field : schema.getFields()) {
                String prefixedName = field.getName().getPrefixedName();

                String name;
                boolean inherited = false;
                boolean multiValued;
                List<Choice> choices = null;
                boolean openChoice = false;
                boolean required = false;
                Serializable defaultValue = null;
                boolean queryable = true;
                boolean orderable = true;

                PropertyType cmisType = PropertyType.STRING;

                org.nuxeo.ecm.core.schema.types.Type fieldType = field.getType();
                if (fieldType.isComplexType()) {
                    // complex type
                    log.info("Chemistry: ignoring complex type: "
                            + schema.getName() + '/' + field.getName());
                    continue;
                } else {
                    if (fieldType.isListType()) {
                        org.nuxeo.ecm.core.schema.types.Type listFieldType = ((ListType) fieldType).getFieldType();
                        if (!listFieldType.isSimpleType()) {
                            // complex list
                            log.info("Chemistry: ignoring complex list: "
                                    + schema.getName() + '/' + field.getName());
                            continue;
                        } else {
                            // Array: use a collection table
                            multiValued = true;
                            cmisType = getPropertType((org.nuxeo.ecm.core.schema.types.SimpleType) listFieldType);
                        }
                    } else {
                        // primitive type
                        multiValued = false;
                        cmisType = getPropertType((org.nuxeo.ecm.core.schema.types.SimpleType) fieldType);
                    }
                }
                // ad-hoc mappings
                if (NX_DC_CREATED.equals(prefixedName)
                        || NX_DC_CREATOR.equals(prefixedName)
                        || NX_DC_MODIFIED.equals(prefixedName)) {
                    // mapped to standard CMIS properties
                    continue;
                }
                name = prefixedName;

                PropertyDefinition def = new SimplePropertyDefinition(name,
                        "def:nx:" + name, null, name, name, "", inherited,
                        cmisType, multiValued, choices, openChoice, required,
                        defaultValue, Updatability.READ_WRITE, queryable,
                        orderable, 0, null, null, -1, null);
                if (map.containsKey(name)) {
                    throw new RuntimeException(
                            "Property already defined for name: " + name);
                }
                map.put(name, def);
            }
        }
        propertyDefinitions = map;
        contentStreamAllowed = BaseType.DOCUMENT.equals(getBaseType())
                && hasFileSchema ? ContentStreamPresence.ALLOWED
                : ContentStreamPresence.NOT_ALLOWED;

    }

    protected PropertyType getPropertType(
            org.nuxeo.ecm.core.schema.types.SimpleType type) {
        org.nuxeo.ecm.core.schema.types.SimpleType primitive = type.getPrimitiveType();
        if (primitive == StringType.INSTANCE) {
            return PropertyType.STRING;
        } else if (primitive == BooleanType.INSTANCE) {
            return PropertyType.BOOLEAN;
        } else if (primitive == DateType.INSTANCE) {
            return PropertyType.DATETIME;
        } else if (primitive == LongType.INSTANCE) {
            return PropertyType.INTEGER;
        }
        return PropertyType.STRING;
    }

    public String getId() {
        return documentType.getName();
    }

    public String getLocalName() {
        return documentType.getName();
    }

    public URI getLocalNamespace() {
        Namespace ns = documentType.getNamespace();
        try {
            return ns == null ? null : new URI(ns.uri);
        } catch (URISyntaxException e) {
            log.error("Invalid URI: " + ns.uri + " for type: " + getId(), e);
            return null;
        }
    }

    public String getQueryName() {
        return getId();
    }

    public String getDisplayName() {
        return getId();
    }

    public String getParentId() {
        String name = documentType.getName();
        if ("Document".equals(name) || "Folder".equals(name)) {
            return null;
        }
        org.nuxeo.ecm.core.schema.types.Type superType = documentType.getSuperType();
        return superType == null ? null : superType.getName();
    }

    public BaseType getBaseType() {
        if (documentType.isFolder()) {
            return BaseType.FOLDER;
        }
        return BaseType.DOCUMENT;
    }

    public String getDescription() {
        return documentType.getName();
    }

    public boolean isCreatable() {
        return true;
    }

    public boolean isQueryable() {
        return true;
    }

    public boolean isControllable() {
        return true;
    }

    public boolean isIncludedInSuperTypeQuery() {
        return true;
    }

    public boolean isFileable() {
        return true;
    }

    public boolean isVersionable() {
        return BaseType.DOCUMENT.equals(getBaseType());
    }

    public ContentStreamPresence getContentStreamAllowed() {
        return contentStreamAllowed;
    }

    public String[] getAllowedSourceTypes() {
        return null;
    }

    public String[] getAllowedTargetTypes() {
        return null;
    }

    public Collection<PropertyDefinition> getPropertyDefinitions() {
        return Collections.unmodifiableCollection(propertyDefinitions.values());
    }

    public PropertyDefinition getPropertyDefinition(String name) {
        return propertyDefinitions.get(name);
    }

}
