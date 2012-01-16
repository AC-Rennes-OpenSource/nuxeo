/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     ataillefer
 */
package org.nuxeo.ecm.platform.diff.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.DifferenceConstants;
import org.custommonkey.xmlunit.NodeDetail;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.platform.diff.model.DocumentDiff;
import org.nuxeo.ecm.platform.diff.model.PropertyDiff;
import org.nuxeo.ecm.platform.diff.model.PropertyType;
import org.nuxeo.ecm.platform.diff.model.SchemaDiff;
import org.nuxeo.ecm.platform.diff.model.impl.ComplexPropertyDiff;
import org.nuxeo.ecm.platform.diff.model.impl.ListPropertyDiff;
import org.nuxeo.ecm.platform.diff.model.impl.PropertyHierarchyNode;
import org.nuxeo.ecm.platform.diff.model.impl.SimplePropertyDiff;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Helper for computing a field diff.
 * 
 * @author <a href="mailto:ataillefer@nuxeo.com">Antoine Taillefer</a>
 */
public final class FieldDiffHelper {

    private static final Log LOGGER = LogFactory.getLog(FieldDiffHelper.class);

    private static final String SYSTEM_ELEMENT = "system";

    private static final String SCHEMA_ELEMENT = "schema";

    private static final String NAME_ATTRIBUTE = "name";

    private static final String TYPE_ATTRIBUTE = "type";

    /**
     * Computes a field diff.
     * <p>
     * First gets all needed elements to compute the field diff:
     * <ul>
     * <li>
     * propertyHierarchy: list holding the property hierarchy
     * 
     * <pre>
     * Every time we encounter a list or complex node going up in the DOM tree
     * from the property node to the prefixed field node, we add it to the property
     * hierarchy.
     * If it is a list item node, we set its index in the hierarchy.
     * If it is a complex item node, we set its name in the hierarchy.
     * 
     * Example: complex list
     * 
     * The "true" property's hierarchy is:
     * [{list,"0"},{complex, "complexBoolean"}]
     * 
     * The "jack" property's hierarchy is:
     * [{list,"1"},{complex, "complexString"}]
     * 
     * <list>
     *   <complexItem>
     *     <complexString>joe</complexString>
     *     <complexBoolean>true</complexBoolean>
     *   </complexItem>
     *   <complexItem>
     *     <complexString>jack</complexString>
     *     <complexBoolean>false</complexBoolean>
     *   </complexItem>
     * </list>
     * </pre>
     * 
     * </li>
     * 
     * </ul>
     * 
     * @param docDiff the doc diff
     * @param controlNodeDetail the control node detail
     * @param testNodeDetail the test node detail
     * @param fieldDifferenceCount the field difference countadd
     * @param difference the difference
     * @return true if a field diff has been found
     * @throws ClientException the client exception
     */
    public static boolean computeFieldDiff(DocumentDiff docDiff,
            NodeDetail controlNodeDetail, NodeDetail testNodeDetail,
            int fieldDifferenceCount, Difference difference)
            throws ClientException {

        // Use control node or if null test node to detect schema and
        // field elements
        Node currentNode = controlNodeDetail.getNode();
        if (currentNode == null) {
            currentNode = testNodeDetail.getNode();
        }
        if (currentNode != null) {

            String field = null;
            String currentNodeName = currentNode.getNodeName();
            List<PropertyHierarchyNode> propertyHierarchy = new ArrayList<PropertyHierarchyNode>();

            // Detect a schema element,
            // for instance: <schema name="dublincore" xmlns:dc="...">,
            // or the <system> element.
            Node parentNode = currentNode.getParentNode();
            while (parentNode != null
                    && !SCHEMA_ELEMENT.equals(currentNodeName)
                    && !SYSTEM_ELEMENT.equals(currentNodeName)) {

                // Get property type
                String propertyType = getPropertyType(currentNode);
                String parentPropertyType = getPropertyType(parentNode);

                // Fill in property hierarchy
                if (PropertyType.isListType(parentPropertyType)) {
                    int currentNodePosition = getNodePosition(currentNode);
                    propertyHierarchy.add(new PropertyHierarchyNode(
                            parentPropertyType,
                            String.valueOf(currentNodePosition)));
                } else if (PropertyType.isComplexType(parentPropertyType)) {
                    propertyHierarchy.add(new PropertyHierarchyNode(
                            parentPropertyType, currentNodeName));
                }

                // Detect a field element, ie. an element that has a
                // prefix, for instance: <dc:title>,
                // or an element nested in <system>.
                if (SCHEMA_ELEMENT.equals(parentNode.getNodeName())
                        || SYSTEM_ELEMENT.equals(parentNode.getNodeName())) {
                    field = currentNode.getLocalName();
                    if (PropertyType.isSimpleType(propertyType)) {
                        propertyHierarchy.add(new PropertyHierarchyNode(
                                propertyType, null));
                    } else if (PropertyType.isListType(propertyType)
                            && propertyHierarchy.isEmpty()) {
                        propertyHierarchy.add(new PropertyHierarchyNode(
                                propertyType, null));
                    } else if (PropertyType.isComplexType(propertyType)
                            && propertyHierarchy.isEmpty()) {
                        propertyHierarchy.add(new PropertyHierarchyNode(
                                propertyType, null));
                    }

                }
                currentNode = parentNode;
                currentNodeName = currentNode.getNodeName();
                parentNode = parentNode.getParentNode();
            }

            // If we found a schema or system element (ie. we did not
            // reached the root element, ie. parentNode != null) and a
            // nested field element, we can compute the diff for this
            // field.
            if (parentNode != null && field != null
                    && !propertyHierarchy.isEmpty()) {
                String schema = currentNodeName;
                // Get schema name if not system
                if (!SYSTEM_ELEMENT.equals(schema)) {
                    NamedNodeMap attr = currentNode.getAttributes();
                    if (attr != null && attr.getLength() > 0) {
                        Node nameAttr = attr.getNamedItem(NAME_ATTRIBUTE);
                        if (nameAttr != null) {
                            schema = nameAttr.getNodeValue();
                        }
                    }
                }

                // Reverse property hierarchy
                Collections.reverse(propertyHierarchy);

                // Pretty log field difference
                LOGGER.info(String.format(
                        "Found field difference #%d on [%s]/[%s] with hierarchy %s: [%s (%s)] {%s --> %s}",
                        fieldDifferenceCount + 1, schema, field,
                        propertyHierarchy, difference.getDescription(),
                        difference.getId(), controlNodeDetail.getValue(),
                        testNodeDetail.getValue()));

                // Compute field diff
                computeFieldDiff(docDiff, schema, field, propertyHierarchy,
                        difference.getId(), controlNodeDetail, testNodeDetail);
                // Return true since a field diff has been found
                return true;

            } else {// Non-field difference
                LOGGER.debug(String.format(
                        "Found non-field difference: [%s (%s)] {%s --> %s}",
                        difference.getDescription(), difference.getId(),
                        controlNodeDetail.getValue(), testNodeDetail.getValue()));
            }
        }
        return false;
    }

    /**
     * Gets the node property type.
     * 
     * @param node the node
     * @return the property diff type
     */
    public static String getPropertyType(Node node) {

        // Default: string type
        String propertyType = PropertyType.STRING;

        NamedNodeMap nodeAttr = node.getAttributes();
        if (nodeAttr != null) {
            Node type = nodeAttr.getNamedItem(TYPE_ATTRIBUTE);
            if (type != null) {
                propertyType = type.getNodeValue();
            }
        }

        return propertyType;
    }

    /**
     * Gets the node position.
     * 
     * @param node the node
     * @return the node position
     */
    private static int getNodePosition(Node node) {

        int nodePos = 0;
        Node previousSibling = node.getPreviousSibling();
        while (previousSibling != null) {
            nodePos++;
            previousSibling = previousSibling.getPreviousSibling();
        }
        return nodePos;
    }

    /**
     * Sets the property diff hierarchy.
     * 
     * @param firstPropertyDiff the first property diff
     * @param propertyHierarchy the property hierarchy
     * @return the property diff
     * @throws ClientException the client exception
     */
    public static PropertyDiff applyPropertyHierarchyToDiff(
            PropertyDiff firstPropertyDiff,
            List<PropertyHierarchyNode> propertyHierarchy)
            throws ClientException {

        if (propertyHierarchy.isEmpty()) {
            throw new ClientException("Empty property hierarchy.");
        }

        // Get first property hierarchy node
        PropertyHierarchyNode propertyHierarchyNode = propertyHierarchy.get(0);
        String firstPropertyType = propertyHierarchyNode.getNodeType();
        String firstPropertyValue = propertyHierarchyNode.getNodeValue();

        if (PropertyType.isSimpleType(firstPropertyType)
                && propertyHierarchy.size() > 1) {
            throw new ClientException(String.format(
                    "Inconsistant property hierarchy %s.", propertyHierarchy));
        }

        // Go through the property hierarchy
        PropertyDiff propertyDiff = firstPropertyDiff;
        String propertyType = firstPropertyType;
        String propertyValue = firstPropertyValue;
        for (int i = 1; i < propertyHierarchy.size(); i++) {

            PropertyDiff childPropertyDiff = null;
            PropertyHierarchyNode childPropertyHierarchyNode = propertyHierarchy.get(i);
            String childPropertyType = childPropertyHierarchyNode.getNodeType();
            String childPropertyValue = childPropertyHierarchyNode.getNodeValue();

            // Simple type
            if (PropertyType.isSimpleType(propertyType)) {
                // Nothing to do here (should never happen)
            } else if (PropertyType.isListType(propertyType)) {
                int propertyIndex = Integer.parseInt(propertyValue);
                // Get list diff, if null create a new one
                childPropertyDiff = ((ListPropertyDiff) propertyDiff).getDiff(propertyIndex);
                if (childPropertyDiff == null) {
                    childPropertyDiff = newPropertyDiff(childPropertyType);
                    ((ListPropertyDiff) propertyDiff).putDiff(propertyIndex,
                            childPropertyDiff);
                }
                propertyDiff = childPropertyDiff;
            } else { // Complex type
                // Get complex diff, initialize it if null
                childPropertyDiff = ((ComplexPropertyDiff) propertyDiff).getDiff(propertyValue);
                if (childPropertyDiff == null) {
                    childPropertyDiff = newPropertyDiff(childPropertyType);
                    ((ComplexPropertyDiff) propertyDiff).putDiff(propertyValue,
                            childPropertyDiff);
                }
                propertyDiff = childPropertyDiff;
            }

            propertyType = childPropertyType;
            propertyValue = childPropertyValue;
        }
        return propertyDiff;
    }

    /**
     * Computes a field diff.
     * 
     * @param docDiff the doc diff
     * @param schema the schema
     * @param field the field
     * @param propertyHierarchy the property hierarchy
     * @param differenceId the difference id
     * @param controlNodeDetail the control node detail
     * @param testNodeDetail the test node detail
     * @throws ClientException the client exception
     */
    private static void computeFieldDiff(DocumentDiff docDiff, String schema,
            String field, List<PropertyHierarchyNode> propertyHierarchy,
            int differenceId, NodeDetail controlNodeDetail,
            NodeDetail testNodeDetail) throws ClientException {

        if (propertyHierarchy.isEmpty()) {
            throw new ClientException("Empty property hierarchy.");
        }

        // Get first property hierarchy node
        PropertyHierarchyNode propertyHierarchyNode = propertyHierarchy.get(0);
        String firstPropertyType = propertyHierarchyNode.getNodeType();

        // Get schema diff, initialize it if null
        SchemaDiff schemaDiff = docDiff.getSchemaDiff(schema);
        if (schemaDiff == null) {
            schemaDiff = docDiff.initSchemaDiff(schema);
        }

        // Get field diff, initialize it if null
        PropertyDiff fieldDiff = schemaDiff.getFieldDiff(field);
        if (fieldDiff == null) {
            fieldDiff = newPropertyDiff(firstPropertyType);
        }

        PropertyDiff endPropertyDiff = fieldDiff;
        // Apply property hierarchy to diff if first property type in hierarchy
        // is list or complex
        if (!(PropertyType.isSimpleType(firstPropertyType))) {
            endPropertyDiff = applyPropertyHierarchyToDiff(fieldDiff,
                    propertyHierarchy);
        }

        // Compute field diff depending on difference type.
        switch (differenceId) {
        default:// In most cases: TEXT_VALUE_ID
            computeTextValueDiff(endPropertyDiff, controlNodeDetail,
                    testNodeDetail);
            break;
        case DifferenceConstants.CHILD_NODE_NOT_FOUND_ID:
            computeChildNodeNotFoundDiff(endPropertyDiff, controlNodeDetail,
                    testNodeDetail);
            break;
        case DifferenceConstants.HAS_CHILD_NODES_ID:
            computeHasChildNodesDiff(endPropertyDiff, controlNodeDetail,
                    testNodeDetail);
            break;
        }

        schemaDiff.putFieldDiff(field, fieldDiff);
    }

    /**
     * New property diff.
     * 
     * @param propertyType the property type
     * @return the property diff
     */
    private static PropertyDiff newPropertyDiff(String propertyType) {

        if (PropertyType.isSimpleType(propertyType)) {
            return new SimplePropertyDiff(propertyType);
        } else if (PropertyType.isListType(propertyType)) {
            return new ListPropertyDiff(propertyType);
        } else { // Complex type
            return new ComplexPropertyDiff();
        }
    }

    /**
     * Computes a TEXT_VALUE diff.
     * 
     * @param fieldDiff the field diff
     * @param controlNodeDetail the control node detail
     * @param testNodeDetail the test node detail
     * @throws ClientException the client exception
     */
    private static void computeTextValueDiff(PropertyDiff fieldDiff,
            NodeDetail controlNodeDetail, NodeDetail testNodeDetail)
            throws ClientException {

        String leftValue = controlNodeDetail.getValue();
        String rightValue = testNodeDetail.getValue();

        Node controlNode = controlNodeDetail.getNode();
        if (controlNode == null) {
            throw new ClientException("Control node should never be null.");
        }

        Node controlParentNode = controlNode.getParentNode();
        if (controlParentNode == null) {
            throw new ClientException(
                    "Control parent node should never be null.");
        }

        String controlParentNodePropertyType = getPropertyType(controlParentNode);
        String fieldDiffPropertyType = fieldDiff.getPropertyType();
        if (PropertyType.isSimpleType(fieldDiffPropertyType)) {
            ((SimplePropertyDiff) fieldDiff).setLeftValue(leftValue);
            ((SimplePropertyDiff) fieldDiff).setRightValue(rightValue);
        } else if (PropertyType.isListType(fieldDiffPropertyType)) {
            ((ListPropertyDiff) fieldDiff).putDiff(
                    getNodePosition(controlParentNode), new SimplePropertyDiff(
                            controlParentNodePropertyType, leftValue,
                            rightValue));
        } else { // Complex type
            ((ComplexPropertyDiff) fieldDiff).putDiff(
                    controlParentNode.getNodeName(), new SimplePropertyDiff(
                            controlParentNodePropertyType, leftValue,
                            rightValue));
        }

    }

    /**
     * Computes a CHILD_NODE_NOT_FOUND diff.
     * 
     * @param fieldDiff the field diff
     * @param controlNodeDetail the control node detail
     * @param testNodeDetail the test node detail
     * @throws ClientException the client exception
     */
    private static void computeChildNodeNotFoundDiff(PropertyDiff fieldDiff,
            NodeDetail controlNodeDetail, NodeDetail testNodeDetail)
            throws ClientException {

        Node childNode;
        boolean isTestNodeNotFound = "null".equals(testNodeDetail.getValue());
        if (!isTestNodeNotFound) {
            childNode = testNodeDetail.getNode();
        } else {
            childNode = controlNodeDetail.getNode();
        }

        if (childNode == null) {
            throw new ClientException("Child node should never be null.");
        }

        String propertyType = fieldDiff.getPropertyType();
        if (PropertyType.isSimpleType(propertyType)) {
            // Should never happen as then it would be marked as a
            // HAS_CHILD_NODES difference.
            throw new ClientException(
                    "A CHILD_NODE_NOT_FOUND difference should never be found within a simple type.");
        } else if (PropertyType.isListType(propertyType)) {
            PropertyDiff childNodeDiff = getChildNodePropertyDiff(childNode,
                    isTestNodeNotFound);
            ((ListPropertyDiff) fieldDiff).putDiff(getNodePosition(childNode),
                    childNodeDiff);
        } else { // Complex type
            throw new ClientException(
                    "A CHILD_NODE_NOT_FOUND difference should never be found within a complex type.");
        }
    }

    /**
     * Computes a HAS_CHILD_NODES diff.
     * 
     * @param fieldDiff the field diff
     * @param controlNodeDetail the control node detail
     * @param testNodeDetail the test node detail
     * @throws ClientException the client exception
     */
    private static void computeHasChildNodesDiff(PropertyDiff fieldDiff,
            NodeDetail controlNodeDetail, NodeDetail testNodeDetail)
            throws ClientException {

        Node nodeWithChildren;
        boolean hasControlNodeChildNodes = Boolean.valueOf(controlNodeDetail.getValue());
        if (hasControlNodeChildNodes) {
            nodeWithChildren = controlNodeDetail.getNode();
        } else {
            nodeWithChildren = testNodeDetail.getNode();
        }

        if (nodeWithChildren == null) {
            throw new ClientException(
                    "Node with children should never be null.");
        }

        String propertyType = fieldDiff.getPropertyType();
        if (PropertyType.isSimpleType(propertyType)) {
            setSimplePropertyDiff((SimplePropertyDiff) fieldDiff,
                    nodeWithChildren, hasControlNodeChildNodes);
        } else if (PropertyType.isListType(propertyType)) {
            PropertyDiff childNodeDiff = getChildNodePropertyDiff(
                    nodeWithChildren, hasControlNodeChildNodes);
            if (PropertyType.isListType(getPropertyType(nodeWithChildren))) {
                ((ListPropertyDiff) fieldDiff).putAllDiff((ListPropertyDiff) childNodeDiff);
            } else {
                ((ListPropertyDiff) fieldDiff).putDiff(
                        getNodePosition(nodeWithChildren), childNodeDiff);
            }
        } else { // Complex type
            PropertyDiff childNodeDiff = getChildNodePropertyDiff(
                    nodeWithChildren, hasControlNodeChildNodes);
            if (PropertyType.isComplexType(getPropertyType(nodeWithChildren))) {
                ((ComplexPropertyDiff) fieldDiff).putAllDiff((ComplexPropertyDiff) childNodeDiff);
            } else {
                ((ComplexPropertyDiff) fieldDiff).putDiff(
                        nodeWithChildren.getNodeName(), childNodeDiff);
            }
        }
    }

    /**
     * Gets the child node property diff.
     * 
     * @param node the node
     * @param hasControlNodeChildNodes the test node was not found
     * @throws ClientException the client exception
     */
    private static PropertyDiff getChildNodePropertyDiff(Node node,
            boolean hasControlNodeChildNodes) throws ClientException {

        PropertyDiff propertyDiff;

        String nodePropertyType = getPropertyType(node);

        if (PropertyType.isSimpleType(nodePropertyType)) {
            propertyDiff = new SimplePropertyDiff(nodePropertyType);
            setSimplePropertyDiff((SimplePropertyDiff) propertyDiff, node,
                    hasControlNodeChildNodes);
        } else if (PropertyType.isListType(nodePropertyType)) {
            propertyDiff = new ListPropertyDiff(nodePropertyType);
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                ((ListPropertyDiff) propertyDiff).putDiff(
                        i,
                        getChildNodePropertyDiff(childNodes.item(i),
                                hasControlNodeChildNodes));
            }
        } else { // Complex type
            propertyDiff = new ComplexPropertyDiff();
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                ((ComplexPropertyDiff) propertyDiff).putDiff(
                        childNode.getNodeName(),
                        getChildNodePropertyDiff(childNode,
                                hasControlNodeChildNodes));
            }
        }
        return propertyDiff;
    }

    /**
     * Sets the text content of textNode on fieldDiff.
     * 
     * @param fieldDiff the field diff
     * @param textNode the text node
     * @param hasControlNodeContent the has control node content
     */
    private static void setSimplePropertyDiff(SimplePropertyDiff fieldDiff,
            Node textNode, boolean hasControlNodeContent) {

        String textNodeValue = textNode.getTextContent();

        String leftValue = hasControlNodeContent ? textNodeValue : null;
        String rightValue = hasControlNodeContent ? null : textNodeValue;

        fieldDiff.setLeftValue(leftValue);
        fieldDiff.setRightValue(rightValue);
    }

}
