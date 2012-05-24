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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.core.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.scripting.Expression;
import org.nuxeo.ecm.automation.core.scripting.Scripting;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.platform.routing.api.exception.DocumentRouteException;
import org.nuxeo.runtime.api.Framework;

/**
 * Graph Node implementation as an adapter over a DocumentModel.
 *
 * @since 5.6
 */
public class GraphNodeImpl extends DocumentRouteElementImpl implements
        GraphNode {

    private static final long serialVersionUID = 1L;

    protected final GraphRouteImpl graph;

    protected State localState;

    /** To be used through getter. */
    protected List<Transition> transitions;

    public GraphNodeImpl(DocumentModel doc, GraphRouteImpl graph) {
        super(doc, new GraphRunner());
        this.graph = graph;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append(getId()).toString();
    }

    protected boolean getBoolean(String propertyName) {
        return Boolean.TRUE.equals(getProperty(propertyName));
    }

    protected CoreSession getSession() {
        return document.getCoreSession();
    }

    protected void saveDocument() throws ClientException {
        getSession().saveDocument(document);
    }

    @Override
    public String getId() {
        return (String) getProperty(PROP_NODE_ID);
    }

    @Override
    public State getState() {
        try {
            if (localState != null) {
                return localState;
            }
            String s = document.getCurrentLifeCycleState();
            return State.fromString(s);
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public void setState(State state) {
        try {
            if (state == null) {
                throw new NullPointerException("null state");
            }
            String lc = state.getLifeCycleState();
            if (lc == null) {
                localState = state;
                return;
            } else {
                localState = null;
                String oldLc = document.getCurrentLifeCycleState();
                if (lc.equals(oldLc)) {
                    return;
                }
                document.followTransition(state.getTransition());
                saveDocument();
            }
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public boolean isStart() {
        return getBoolean(PROP_START);
    }

    @Override
    public boolean isStop() {
        return getBoolean(PROP_STOP);
    }

    @Override
    public boolean isMerge() {
        String merge = (String) getProperty(PROP_MERGE);
        return StringUtils.isNotEmpty(merge);
    }

    @Override
    public String getInputChain() {
        return (String) getProperty(PROP_INPUT_CHAIN);
    }

    @Override
    public String getOutputChain() {
        return (String) getProperty(PROP_OUTPUT_CHAIN);
    }

    @Override
    public boolean hasTask() {
        return getBoolean(PROP_HAS_TASK);
    }

    @Override
    public void incrementCount() {
        try {
            Long count = (Long) getProperty(PROP_COUNT);
            if (count == null) {
                count = Long.valueOf(0);
            }
            document.setPropertyValue(PROP_COUNT,
                    Long.valueOf(count.longValue() + 1));
            saveDocument();
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    /**
     * Gets the node variables.
     *
     * @return the map of variables
     */
    protected Map<String, Serializable> getVariables() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Serializable>> vars = (List<Map<String, Serializable>>) document.getPropertyValue(PROP_VARIABLES);
            Map<String, Serializable> map = new LinkedHashMap<String, Serializable>();
            for (Map<String, Serializable> var : vars) {
                String name = (String) var.get(PROP_VAR_NAME);
                Serializable value = var.get(PROP_VAR_VALUE);
                map.put(name, value);
            }
            return map;
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    /**
     * Sets the node variables.
     *
     * @param map the map of variables
     */
    protected void setVariables(Map<String, Serializable> map) {
        try {
            List<Map<String, Serializable>> vars = new LinkedList<Map<String, Serializable>>();
            for (Entry<String, Serializable> es : map.entrySet()) {
                Map<String, Serializable> m = new HashMap<String, Serializable>();
                m.put(PROP_VAR_NAME, es.getKey());
                m.put(PROP_VAR_VALUE, es.getValue());
                vars.add(m);
            }
            document.setPropertyValue(PROP_VARIABLES, (Serializable) vars);
            saveDocument();
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    protected OperationContext getContext(
            Map<String, Serializable> graphVariables,
            Map<String, Serializable> nodeVariables) {
        OperationContext context = new OperationContext(getSession());
        // context.put(DocumentRoutingConstants.OPERATION_STEP_DOCUMENT_KEY,
        // element);
        context.putAll(graphVariables);
        context.putAll(nodeVariables);
        // workflow context
        // context.put("workflowId", graph.get);
        context.put("initiator", "");
        context.put("documents", "");
        context.put("workflowStartTime", "");
        // node context
        context.put("nodeId", getId());
        context.put("state", getState().name().toLowerCase());
        context.put("nodeStartTime", ""); // TODO
        // task context
        context.put("assignees", ""); // TODO
        context.put("comment", ""); // TODO filled by form
        context.put("status", ""); // TODO filled by form
        // associated docs
        context.setInput(graph.getAttachedDocumentModels());
        return context;
    }

    @Override
    public void executeChain(String chainId) throws DocumentRouteException {
        executeChain(chainId, null);
    }

    @Override
    public void executeTransitionChain(Transition transition)
            throws DocumentRouteException {
        executeChain(transition.chain, transition.id);
    }

    public void executeChain(String chainId, String transitionId)
            throws DocumentRouteException {
        // TODO events
        if (StringUtils.isEmpty(chainId)) {
            return;
        }

        // get variables from node and graph
        Map<String, Serializable> graphVariables = graph.getVariables();
        Map<String, Serializable> nodeVariables = getVariables();
        OperationContext context = getContext(graphVariables, nodeVariables);
        if (transitionId != null) {
            context.put("transition", transitionId);
        }

        AutomationService automationService = Framework.getLocalService(AutomationService.class);
        try {
            automationService.run(context, chainId);
            // stupid run() method throws generic Exception
        } catch (InterruptedException e) {
            // restore interrupted state
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentRouteException("Error running chain: " + chainId,
                    e);
        }

        // set variables back into node and graph
        boolean changedNodeVariables = false;
        boolean changedGraphVariables = false;
        for (Entry<String, Object> es : context.entrySet()) {
            String key = es.getKey();
            Serializable value = (Serializable) es.getValue();
            if (nodeVariables.containsKey(key)) {
                Serializable oldValue = nodeVariables.get(key);
                if (!ObjectUtils.equals(value, oldValue)) {
                    changedNodeVariables = true;
                    nodeVariables.put(key, value);
                }
            } else if (graphVariables.containsKey(key)) {
                Serializable oldValue = graphVariables.get(key);
                if (!ObjectUtils.equals(value, oldValue)) {
                    changedGraphVariables = true;
                    graphVariables.put(key, value);
                }
            }
        }
        if (changedNodeVariables) {
            setVariables(nodeVariables);
        }
        if (changedGraphVariables) {
            graph.setVariables(graphVariables);
        }
    }

    protected List<Transition> computeTransitions() {
        try {
            ListProperty props = (ListProperty) document.getProperty(PROP_TRANSITIONS);
            List<Transition> trans = new ArrayList<Transition>(props.size());
            for (Property p : props) {
                trans.add(new Transition(p));
            }
            Collections.sort(trans);
            return trans;
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    public List<Transition> getTransitions() {
        if (transitions == null) {
            transitions = computeTransitions();
        }
        return transitions;
    }

    @Override
    public List<Transition> evaluateTransitions() throws DocumentRouteException {
        try {
            List<Transition> trueTrans = new ArrayList<Transition>();
            OperationContext context = getContext(graph.getVariables(),
                    getVariables());
            for (Transition t : getTransitions()) {
                context.put("transition", t.id);
                Expression expr = Scripting.newExpression(t.condition);
                Object res = null;
                try {
                    res = expr.eval(context);
                    // stupid eval() method throws generic Exception
                } catch (InterruptedException e) {
                    // restore interrupted state
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DocumentRouteException(
                            "Error evaluating condition: " + t.condition, e);
                }
                if (!(res instanceof Boolean)) {
                    throw new DocumentRouteException(
                            "Condition for transition " + t + " of node '"
                                    + getId() + "' of graph '"
                                    + graph.getName()
                                    + "' does not evaluate to a boolean: "
                                    + t.condition);
                }
                boolean bool = Boolean.TRUE.equals(res);
                t.setResult(bool);
                if (bool) {
                    trueTrans.add(t);
                }
            }
            saveDocument();
            return trueTrans;
        } catch (DocumentRouteException e) {
            throw e;
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

}
