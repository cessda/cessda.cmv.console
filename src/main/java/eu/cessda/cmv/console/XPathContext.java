package eu.cessda.cmv.console;

import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;

enum XPathContext {
    DDI_2_5(buildContext("ddi:codebook:2_5"), "//ddi:codeBook//ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo"),
    NESSTAR(buildContext("http://www.icpsr.umich.edu/DDI"), "//ddi:codeBook/stdyDscr/citation/titlStmt/IDNo");

    private final NamespaceContext namespaceContext;
    private final String xPath;

    XPathContext(NamespaceContext namespaceContext, String xPath) {
        this.namespaceContext = namespaceContext;
        this.xPath = xPath;
    }

    private static NamespaceContext buildContext(String namespaceURI) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "ddi".equals(prefix) ? namespaceURI : null;
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

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    public String getXPath() {
        return xPath;
    }
}
