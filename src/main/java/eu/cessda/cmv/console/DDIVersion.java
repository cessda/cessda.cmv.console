/*
 * Copyright © 2017-2025 CESSDA ERIC (support@cessda.eu)
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
 */
package eu.cessda.cmv.console;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.namespace.NamespaceContext;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

// TODO - configure this from external sources
enum DDIVersion {
    DDI_3_3(
        buildContext(Map.ofEntries(
            Map.entry("ddi", "ddi:instance:3_3"),
            Map.entry("s", "ddi:studyunit:3_3"),
            Map.entry("r", "ddi:reusable:3_3")
        )),
        "//r:Citation/r:InternationalIdentifier",
        DDIVersion::getDDILifecyclePID
    ),
    DDI_3_2(
        buildContext(Map.ofEntries(
            Map.entry("ddi", "ddi:instance:3_2"),
            Map.entry("s", "ddi:studyunit:3_2"),
            Map.entry("r", "ddi:reusable:3_2")
        )),
        "//r:Citation/r:InternationalIdentifier",
        DDIVersion::getDDILifecyclePID
    ),
    DDI_2_5(
        buildContext(Map.of("ddi", "ddi:codebook:2_5")),
        "//ddi:codeBook//ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo",
        DDIVersion::getDDI25PID
    ),
    NESSTAR(
        buildContext(Map.of("ddi", "http://www.icpsr.umich.edu/DDI")),
        "//ddi:codeBook/stdyDscr/citation/titlStmt/IDNo",
        DDIVersion::getDDI25PID
    );

    private static PID getDDILifecyclePID(Node node) {
        String agency = null;
        String uri = null;

        var childNodeList = node.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); i++) {
            var childNode = childNodeList.item(i);

            // Select child elements
            if (childNode instanceof Element) {
                switch (childNode.getLocalName()) {
                    case "ManagingAgency" -> agency = childNode.getTextContent().trim();
                    case "IdentifierContent" -> uri = childNode.getTextContent().trim();
                }
            }
        }

        // Return a new PID object with the validation state set to empty
        return new PID(agency, uri, EnumSet.noneOf(PID.State.class));
    }

    private final NamespaceContext namespaceContext;
    private final String pidXPath;
    private final Function<Node, PID> getPid;

    DDIVersion(NamespaceContext namespaceContext, String pidXPath, Function<Node, PID> getPid) {
        this.namespaceContext = namespaceContext;
        this.pidXPath = pidXPath;
        this.getPid = getPid;
    }

    private static NamespaceContext buildContext(Map<String, String> namespaces) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return namespaces.get(prefix);
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        };
    }

    private static PID getDDI25PID(Node node) {
        String agency = null;

        var agencyAttribute = node.getAttributes().getNamedItem("agency");
        if (agencyAttribute != null) {
            agency = agencyAttribute.getTextContent();
        }

        return new PID(agency, node.getTextContent().trim(), EnumSet.noneOf(PID.State.class));
    }

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    public String getXPath() {
        return pidXPath;
    }

    /**
     * Get the agency associated with the persistent identifier.
     * @param n the node
     * @return the agency.
     */
    public PID getPID(Node n) {
        return getPid.apply(n);
    }
}
