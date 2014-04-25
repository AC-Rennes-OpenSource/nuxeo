package org.nuxeo.segment.io;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("providersConfig")
public class SegmentIOProviders {

    @XNode("enableDefaults")
    boolean enableDefaults=true;

    @XNodeMap(value = "providers/provider", key = "@name", type = HashMap.class, componentType = Boolean.class)
    Map<String, Boolean> providers = new HashMap<String, Boolean>();

}
